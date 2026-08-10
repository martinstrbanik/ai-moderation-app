# AutoModer AI — Automatizovaná moderácia používateľského obsahu pomocou AI

> Diplomová práca: **Využitie a porovnanie modelov umelej inteligencie pri automatizovanej moderácii používateľského obsahu**
>
> Cieľ: navrhnúť a implementovať webovú aplikáciu, ktorá pomocou AI modelov automaticky analyzuje a moderuje používateľsky vytvorený obsah (texty a obrázky) podľa zvolených politík, a benchmark modul na porovnávanie rôznych modelov (Gemini, Claude, ChatGPT / OpenRouter).

## 1. Prehľad / Úvod

Aplikácia pozostáva z dvoch navzájom prepojených častí:

1. **Moderačný nástroj** — konfigurovateľný nástroj, ktorý cez REST API prijíma používateľský obsah (text, obrázky), automaticky ho analyzuje vybraným AI modelom podľa definovaných politík a vracia verdikt (povolené / zamietnuté / flag) spolu s odôvodnením.
2. **Benchmark modul** — nástroj na testovanie a porovnávanie viacerých AI modelov na dostupných datasetoch na základe metrík (precision, recall, F1-score, latencia, náklady). Výsledky sa vizualizujú v dashboarde.

Obe časti zdieľajú rovnaké entity (modely, politiky) a bežia v jednej webovej aplikácii.

### Rozhodnutie: multi-tenant web service vs. lokálny nástroj

Po zvážení otázky **multi-tenant webovej služby vs. lokálneho nástroja** bola zvolená **stredná cesta**:

- **Single-tenant aplikácia dnes**, ale s **architektúrou pripravenou na multi-tenancy** — hlavné entity majú `tenant_id` a služby sú scoped na tenanta, takže neskorší prechod na viac používateľov nevyžaduje prerábať dátový model ani API.
- **Jednoduchá administrátorská autentifikácia** (jeden admin účet; pre API volania prípadne vlastný API kľúč) — bez plnej registrácie, RBAC a správy tímov, ktoré zostávajú vo fáze nice-to-have (sekcia 9).
- **BYO (bring-your-own) OpenRouter kľúč** — každý používateľ si nastaví vlastný API kľúč a platí si vlastné tokeny. Odpadá tým nutnosť riešiť billing, kvóty a sledovanie nákladov na strane aplikácie.
- Plný **multi-tenant SaaS** (registrácia, izolácia viacerých nájomníkov, RBAC) je reálnym cieľom pre nasadenie v praxi, ale **nie je v rozsahu tejto diplomovej práce** — výskumný prínos (moderácia + benchmark) na ňom nezávisí a zbytočne by rozšíril rozsah.

#### Prečo nie plný multi-tenant SaaS (sumár)

- Výskumný prínos práce je o **modeloch a moderácii**, nie o správe používateľov.
- Auth, izolácia nájomníkov a RBAC by pridali celú ďalšiu dimenziu rozsahu a odčerpali čas od **experimentov a vyhodnotenia**.
- Pri viacerých tenantoch by bolo nutné riešiť, **kto platí tokeny a limity** — tomu sa pri single-tenant + BYO kľúči vyhneme.
- Moderácia sa týka citlivého obsahu; menej dát = menšia bezpečnostná a GDPR záťaž.

## 2. Technologický stack

| Vrstva | Technológia | Účel |
|--------|-------------|------|
| Backend | **Spring Boot 3 (Java 21)** | REST API, business logika, integračné služby, orchestrácia benchmarkov |
| Databáza | **PostgreSQL** | Relačné ukladanie entít (politiky, modely, výsledky, metriky) |
| Frontend | **Vue.js 3 (Vite, Pinia, Vue Router)** | Webové rozhranie, konfigurácia politík, dashboard, benchmark UI |
| Autentifikácia | **Spring Security (JWT / admin účet)** | Jednoduchá administrátorská autentifikácia a ochrana API |
| AI integrácia | **OpenRouter API** | Jednotný prístup k viacerým LLM modelom (Gemini, Claude, ChatGPT) cez jedného providera |
| API kľúče | **BYO OpenRouter kľúč** (šifrované na strane servera) | Každý používateľ si nastaví vlastný API kľúč a platí si vlastné tokeny |
| Obrázková moderácia | Využitie vision-modelov cez OpenRouter (multimodálne LLM) | Analýza obrázkov |
| Scheduler | **Spring Scheduler / @Async** | Spúšťanie benchmark behov |
| Docker / Docker Compose | PostgreSQL + aplikácia | Lokálny vývoj a nasadenie |

## 3. Architektúra systému

```
+---------------------+        +---------------------------+        +-------------------+
|     Frontend        |        |          Backend          |        |      AI modely     |
|     (Vue.js)        |  REST  |       (Spring Boot)       |  HTTP  |   (OpenRouter)    |
|                     | <----> |                           | <----> |  Gemini / Claude   |
|  - Politiky         |        |  - Controllers (REST)    |        |  ChatGPT / iné     |
|  - Dashboard        |        |  - Service vrstva        |        +-------------------+
|  - Benchmark UI     |        |  - Provider adaptery    |
|  - Moderačný klient |        |  - Benchmark engine     |
+---------------------+        |  - Scheduler            |
                               +-----------+---------------+
                                           |
                                   +-------v--------+
                                   |   PostgreSQL     |
                                   +------------------+
```

Kľúčové princípy:
- **Adapter vzor** pre AI providerov — jedno rozhranie `AiModelProvider`, aktuálne iba implementácia pre OpenRouter (iné providery neplánujeme; prípadné priame API len ako voliteľné rozšírenie). Vďaka tomu sa pridávanie nového modelu robí konfiguráciou, nie zmenou kódu.
- **Moderácia aj benchmark** volajú rovnaké provider služby.
- Modely aj politiky sú **dátové entity** spravované cez CRUD API (nie hard-kódované).

## 4. Funkčné moduly (feature set)

### 4.1 Správa modelov (`/models`)
- CRUD nad registrovanými modelmi (napr. `google/gemini-2.0-flash`, `anthropic/claude-3.5-sonnet`, `openai/gpt-4o`).
- Konfigurácia parametrov: max_tokens, temperature, timeout, cena za token.
- Možnosť označiť model ako „textový“ / „vision“ (multimodálny).

### 4.2 Politiky moderácie (`/policies`)
- Definícia pravidiel, podľa ktorých sa obsah kontroluje, napr.:
  - kategórie: hate speech, sexual content, violence, spam, personal data, custom.
  - severity threshold (aký stupeň rizika = zablokovať / flag).
  - povolené/zakázané slová, regex, blacklist/whitelist.
  - cieľový model pre danú politiku + fallback model.
  - akcia pri porušení: `ALLOW`, `FLAG`, `BLOCK`.
- Verzovanie a aktivácia/deaktivácia politík.

### 4.3 Moderačný modul (`/moderation`)
- Endpoint na **textovú** a **obrázkovú** (vision) analýzu obsahu.
- Vstup: text a/alebo obrázok (base64/URL), identifikátor politiky.
- Výstup: verdikt, rizikové kategórie, odôvodnenie modelu, použité pravidlá, dôvera (confidence).
- Logovanie každej moderácie do histórie (`moderation_logs`).

### 4.4 Dashboard (`/dashboard`)
- Prehľady: počet moderácií, rozdelenie verdiktov, TOP porušované kategórie.
- Grafy (napr. Chart.js) — trendy v čase.
- Prehľad benchmark výsledkov a porovnanie modelov.

### 4.5 Benchmark modul (`/benchmarks`)
- **Datasety:** používame dostupné / existujúce datasety (napr. toxicity detekcia, hate speech atď.) — **vlastné datasety netvoríme**. Užívateľ si vyberá z pripravených datasetov; import vlastného datasetu (CSV/JSON s obsahom a očakávaným označením `label`) je voliteľné rozšírenie.
- **Benchmark run:** vybraný dataset × vybrané modely × vybrané politiky → systém spustí moderáciu na vzorke a uloží výsledky.
- **Velkosť behu (levely):** kvôli šetreniu tokenov sú preddefinované úrovne, ktoré určujú počet vzoriek spracovaných v jednom behu:
  - `EXTRA_LIGHT` — malá vzorka (rýchly orientačný test),
  - `LIGHT` — stredná vzorka (štandardný beh),
  - (prípadne `FULL` — celý dataset).
- **Metriky:** precision, recall, F1-score, accuracy + latencia (ms), náklady, počet zlyhaní.
- **Porovnanie:** tabuľkový a grafický výstup, export do CSV.

### 4.6 Predpripravené benchmark výsledky (`/benchmarks/published`)
- Ukladajú sa tu vzorové výsledky benchmarkov **vybraných modelov, ktoré spustíme my (autori práce)** na pripravených datasetoch.
- Slúžia ako referenčné dáta pre čitateľov práce aj pre používateľov — vidia porovnanie modelov bez nutnosti minúť tokeny.
- Sú to teda niekoľké predpripravené výsledky, ale užívateľ si môže kedykoľvek spustiť **vlastný benchmark** (sekcia 4.5) a porovnať ho s nimi.

## 5. Dátový model (hlavné entity)

Hlavné entity majú `tenant_id` kvôli budúcej izolácii (dnes single-tenant). Modely a datasety sú referenčné/seed dáta (spoločné, bez `tenant_id`):

```
AiModel    (id, provider, model_id, name, type[text|vision], params, enabled)
ApiKey     (id, tenant_id, provider, encrypted_key, label)   // BYO OpenRouter klúč
Policy     (id, tenant_id, name, description, rules, categories, threshold, model_id,
            fallback_model_id, action, active, version)
Dataset    (id, name, description, source, created_at)       // referenčné dáta
DatasetSample (id, dataset_id, content, image_url, expected_label)
ModerationLog (id, tenant_id, policy_id, model_id, content_type, verdikt, categories,
               confidence, latency, created_at)
BenchmarkRun   (id, tenant_id, dataset_id, policy_id, level[EXTRA_LIGHT|LIGHT|FULL],
                published, status, started_at, finished_at)
BenchmarkResult(id, tenant_id, run_id, model_id, precision, recall, f1, accuracy,
                avg_latency, cost, error_count)
```

## 6. REST API (návrh koncových bodov)

```
GET/POST/PUT/DELETE  /api/models
POST/PUT            /api/api-keys      (BYO OpenRouter kľúč, šifrovaný na strane servera)
GET/POST/PUT/DELETE  /api/policies
POST  /api/moderation        { policyId, text?, image? }  -> { verdict, categories, ... }
GET   /api/moderation/logs   (filtrovateľné)
POST  /api/datasets          (import datasetu)
POST  /api/benchmarks/runs   { datasetId, policyId, modelIds[] }
GET   /api/benchmarks/runs/{id}
GET   /api/benchmarks/results
GET   /api/dashboard/summary
```

## 7. Metriky a vyhodnocovanie

- **Precision** = TP / (TP + FP)
- **Recall** = TP / (TP + FN)
- **F1-score** = 2 * (P * R) / (P + R)
- Ďalšie: accuracy, latencia (p50/p95), náklady na 1 000 vzoriek, chybovosť API.
- Porovnanie modelov sa prezentuje v dashboarde aj ako exportovaná tabuľka pre text práce.

## 8. Roadmap (mapovanie na úlohy diplomovej práce)

1. **Setup projektu** — Spring Boot skeleton, PostgreSQL, Vue.js scaffold, Docker Compose.
2. **Integrácia OpenRouter** — provider adapter, práca s textovými aj vision modelmi, držanie API kľúča na strane servera.
3. **Core entity + CRUD** — modely a politiky, REST API.
4. **Moderačný engine** — volanie modelu pre danú politiku, parsovanie/výstup, logovanie.
5. **Dashboard** — vizualizácia výsledkov moderácie aj benchmarkov.
6. **Benchmark modul** — nájdenie/použitie datasetov, spúšťanie behov (levely extra light/light), výpočet metrík, predpripravené výsledky.
7. **Experimenty** — testovanie viacerých modelov, vyhodnotenie F1/precision/recall/latencie.
8. **Zhodnotenie a dokumentácia** — prínos, nasadenie v praxi, text práce.

## 9. Neskoršie rozšírenia (nice-to-have)

- **Plný multi-tenant SaaS** — registrácia používateľov, izolácia viacerých nájomníkov, RBAC, správa tímov (architektúra je na to pripravená cez `tenant_id`).
- A/B testovanie politík.
- Webhook notifikácie pri moderácii.
- Export reportov do PDF/CSV.
- Podpora vlastných (open-source) modelov (napr. lokálne Ollama) pre porovnanie.
- Cache a rate-limiting voči providerom.
