# AGENTS.md — Project Context for AI Agents

> Purpose of this file: give any AI coding agent (and the human author) the full
> context of this project in one place — what it does, how it is built, where
> everything lives, how to run it, and which decisions/gotchas matter.
> **Keep this file up to date**: when you add a module / endpoint / entity,
> extend the matching section and the "Current status" / "Roadmap" sections.

---

## 1. What this project is

A **diploma thesis project** (Slovak university) about *using and comparing AI
models for automated moderation of user-generated content*. It is a Spring Boot
web application with two main capabilities:

1. **Moderation** — classify user content (currently text) against a configurable
   **policy** using an LLM and return a verdict (`ALLOW` / `FLAG` / `BLOCK`).
2. **Benchmark** — run the same classification task over prepared datasets,
   across multiple models, and compute evaluation metrics
   (precision / recall / F1 / accuracy / latency / cost).

Key characteristics:

- LLM access goes through **OpenRouter** (single provider) using a
  **BYO (bring-your-own) API key**; keys are stored **encrypted (AES/GCM)**.
- The benchmark compares models on a **fixed classification prompt** so results
  are comparable; **batching** (several samples per request) is used for speed.
- Datasets: **English** (Davidson et al.) and **Slovak** (TUKE-KEMT) hate-speech
  datasets prepared as JSONL. EN-vs-SK comparison is a core thesis angle.
- There is **no frontend yet** (planned: Vue.js). The app is driven via REST and
  a Postman collection.

Language conventions:

- **All code, comments and this file: English.**
- **`README.md` (root) and `data/README.md`: Slovak** (thesis design doc /
  dataset doc — intentionally not translated).

---

## 2. Tech stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 3.5.6, Java 21 |
| Database | PostgreSQL 18 (system install, local) |
| Persistence | Spring Data JPA / Hibernate (`ddl-auto=update`) |
| Security | Spring Security, HTTP Basic (single admin account) |
| HTTP client | Spring `RestClient` (OpenRouter calls) |
| AI provider | OpenRouter (`https://openrouter.ai/api/v1`) |
| Dataset prep | Python 3 (stdlib only) scripts |
| API testing | Postman collection (`postman/automoder.postman_collection.json`) |
| Frontend | *not yet* (planned Vue.js 3) |

---

## 3. How to run

The toolchain is **user-local** (installed without sudo) in `/home/martin/tools`.
Source the env before using `java` / `mvn` / `node`:

    source /home/martin/tools/env.sh

PostgreSQL is a **system service** on `localhost:5432`, database `automoder`, role
`automoder`. Real credentials are in
`backend/src/main/resources/application-local.properties` (gitignored);
`application.properties` contains placeholders only.

Run the backend:

    cd backend && mvn spring-boot:run

Run the tests:

    cd backend && mvn test

At startup the app **seeds reference models** and **imports datasets** (see §7–§8).
Public health endpoint: `GET http://localhost:8080/actuator/health`.
All other endpoints require HTTP Basic `admin` / `admin123` (dev values).

> **Agent tip:** the dev database is shared. To run a second instance for
> verification, start it on another port:
> `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`.
> **Never kill processes by name** (`pkill -f spring-boot:run`) — it also kills
> the developer's running app. Kill by port/PID instead.

---

## 4. Repository layout

    diplomovka/
    ├── README.md                # Slovak thesis design doc (keep in Slovak)
    ├── AGENTS.md                # this file (English, agent + human context)
    ├── docker-compose.yml       # Postgres compose (UNUSED — system Postgres is used)
    ├── .gitignore               # ignores data/raw, data/processed, application-local.properties, target, __pycache__
    ├── postman/
    │   └── automoder.postman_collection.json   # all REST endpoints + examples
    ├── scripts/
    │   ├── prepare_dataset.py            # Davidson (EN) -> data/processed/davidson
    │   └── prepare_dataset_slovak.py     # TUKE (SK)    -> data/processed/tuke_slovak
    ├── data/
    │   ├── README.md            # Slovak dataset doc (keep in Slovak)
    │   ├── raw/                 # originals (gitignored): labeled_data.csv, tuke_slovak/*.json
    │   └── processed/           # prepared JSONL (gitignored): davidson/, tuke_slovak/
    └── backend/                 # Spring Boot app (Maven)

---

## 5. Backend package structure

Base package: `sk.automoder` (`backend/src/main/java/sk/automoder/`).

| Package | Contents |
|---------|----------|
| `AutomodApplication` | Spring Boot entry point |
| `ai/` | `OpenRouterClient` (HTTP calls), `PromptFactory` (all prompts), `AiResult` (content + cost + latency), `AiProviderException` |
| `config/` | `SecurityConfig` (HTTP Basic), `AsyncConfig` (benchmark thread pool), `DataInitializer` (seed models), `DatasetInitializer` (import datasets) |
| `controller/` | REST controllers (see §9) |
| `dto/` | Request/response records |
| `exception/` | `ApiException` (+ NotFound/Conflict/BadRequest), `ApiError`, `GlobalExceptionHandler` |
| `model/` | JPA entities + enums |
| `repository/` | Spring Data JPA repositories |
| `security/` | `AesGcmEncryptor` (AES/GCM for API keys) |
| `service/` | Business logic (see below) |

Key services:

- `AiModelService`, `PolicyService`, `ApiKeyService` — CRUD + validation.
- `DatasetService` — imports JSONL datasets at startup, read access.
- `ModerationService` — text moderation (policy → model → severity → verdict).
- `BenchmarkService` — creates runs; `BenchmarkExecutor` — runs them async.
- `Metrics` — pure metric computation (macro precision/recall/F1 + accuracy).

---

## 6. Data model

Entities (all in `model/`):

| Entity | Key fields | Notes |
|--------|-----------|-------|
| `AiModel` | provider(`openrouter`), `modelId`(unique), name, `type`(TEXT/VISION), enabled | reference catalog; **no** inference params stored (fixed: temperature=0, JSON output) |
| `ApiKey` | tenantId, provider, `encryptedKey` (AES/GCM), label | BYO key; plaintext never returned |
| `Policy` | tenantId, name, description, categories(JSON), rules(JSON), `threshold`(0..1), `action`(ALLOW/FLAG/BLOCK), modelId, fallbackModelId, active, version | `version` increments on update/activation change |
| `ModerationLog` | tenantId, policy, model, contentType, verdict, `severity`, categories, confidence, latencyMs | audit of each moderation |
| `Dataset` | name, description, source | reference registry (no tenantId) |
| `DatasetSample` | dataset, content, imageUrl, expectedLabel | one sample; `expectedLabel` used as ground truth |
| `BenchmarkRun` | tenantId, dataset, policy(nullable), apiKeyId, level, batchSize, status, published, startedAt, finishedAt | one benchmark run |
| `BenchmarkResult` | tenantId, run, model, precision, recall, f1, accuracy, avgLatency, cost, errorCount, processedSamples | one row per model per run |

Enums: `ModelType{TEXT,VISION}`, `PolicyAction{ALLOW,FLAG,BLOCK}`,
`ContentType{TEXT,IMAGE}`, `BenchmarkLevel{EXTRA_LIGHT,LIGHT,FULL}`,
`RunStatus{PENDING,RUNNING,COMPLETED,FAILED}`.

**Tenancy:** the app is effectively single-tenant today, but entities carry
`tenantId` (constant `"default"`, see `PolicyService.DEFAULT_TENANT`) to be
multi-tenant-ready. Models and datasets are shared reference data (no tenantId).

---

## 7. Configuration & seeds

`application.properties` (committed) holds **placeholders** and non-secret config;
real secrets live in `application-local.properties` (gitignored) and the `local`
profile is activated by default (`spring.profiles.active=${SPRING_PROFILES_ACTIVE:local}`).

Required env/property values (placeholders in the committed file):
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`,
`CRYPTO_MASTER_KEY`.

Non-secret config:

| Property | Default | Meaning |
|----------|---------|---------|
| `automoder.openrouter.base-url` | `https://openrouter.ai/api/v1` | provider base URL |
| `automoder.openrouter.timeout-seconds` | `120` | per-call read timeout (lower for slow/free models) |
| `automoder.datasets.path` | `../data/processed` | JSONL import path (relative to `backend/`) |
| `automoder.benchmark.batch-size` | `10` | samples per OpenRouter call |

Seeds (`DataInitializer`): if the model catalog is empty, 4 OpenRouter models are
inserted — `google/gemini-2.0-flash` (VISION), `openai/gpt-4o-mini` (TEXT),
`openai/gpt-4o` (VISION), `anthropic/claude-3.5-sonnet` (VISION).
Model `type` is essentially a capability hint (VISION = multimodal = handles images too).

SECURITY NOTE: `CRYPTO_MASTER_KEY` must be a 32-byte base64 AES key. Changing it
makes previously stored API keys undecryptable.

---

## 8. Datasets

Prepared as JSONL, one sample per line: `{"id": int, "text": str, "label": str, "label_id": int}`.

| Dataset | Language | Samples | Labels | Source |
|---------|----------|---------|--------|--------|
| `davidson` | EN | 24 783 | `hate_speech`(0), `offensive`(1), `neither`(2) | Davidson et al. 2017 (GitHub) |
| `tuke_slovak` | SK | 13 189 | `not_hate`(0), `hate`(1) | HF `TUKE-KEMT/hate_speech_slovak` |

Files per dataset in `data/processed/<name>/`:

- `labeled_data.jsonl` — full dataset (original class distribution) → for `FULL` runs
- `extra_light.jsonl` — balanced subsample (EN: 50/class = 150; SK: 50/class = 100)
- `light.jsonl` — balanced subsample (EN: 500/class = 1500; SK: 500/class = 1000)
- `meta.json` — statistics + metadata

Why balanced subsamples: to have enough per-class examples for meaningful
precision/recall/F1 even on small runs. Selection is deterministic (`seed=42`).

Regenerate (after downloading raw files into `data/raw/`):

    python3 scripts/prepare_dataset.py          # Davidson
    python3 scripts/prepare_dataset_slovak.py   # Slovak

`data/raw/**` and `data/processed/**` are **gitignored** (large files); only
`data/README.md` is committed. `DatasetInitializer` imports every subdirectory of
`automoder.datasets.path` that contains `labeled_data.jsonl` (idempotent by name),
reading the source URL from `meta.json`.

> Ethics note: both datasets contain hateful/vulgar content. They are used for
> academic moderation research only.

---

## 9. REST API

All endpoints except `GET /actuator/health` require HTTP Basic.
Base URL: `http://localhost:8080`.

| Endpoint | Methods |
|----------|---------|
| `/api/models` | GET (filters `type`, `enabled`), POST, PUT `/{id}`, DELETE `/{id}`, GET `/{id}` |
| `/api/api-keys` | GET (masked), POST, DELETE `/{id}` |
| `/api/policies` | GET (filter `active`), POST, PUT `/{id}`, DELETE `/{id}`, GET `/{id}`, PATCH `/{id}/activate`, PATCH `/{id}/deactivate` |
| `/api/datasets` | GET, GET `/{id}` |
| `/api/benchmarks/runs` | GET, POST, GET `/{id}`, GET `/{id}/results` |
| `/api/moderation` | POST |
| `/actuator/health` | GET (public) |

Example request bodies:

    POST /api/models          {"modelId":"openai/gpt-4o-mini","name":"GPT-4o mini","type":"TEXT","enabled":true}
    POST /api/api-keys        {"label":"my-key","key":"sk-or-v1-..."}
    POST /api/policies        {"name":"Hate","description":"...","categories":"[\"hate_speech\"]","rules":"{}","threshold":0.5,"action":"BLOCK","modelId":3,"fallbackModelId":null,"active":true}
    POST /api/benchmarks/runs {"datasetId":1,"policyId":2,"modelIds":[3,8],"level":"EXTRA_LIGHT","apiKeyId":null,"batchSize":10}
    POST /api/moderation      {"policyId":2,"text":"text to moderate"}

Error shape (`ApiError`): `{timestamp, status, error, message, path, fieldErrors}`.

---

## 10. AI provider (OpenRouter)

`OpenRouterClient.call(apiKey, modelId, systemPrompt, userContent)` → `POST
{base-url}/chat/completions` with `temperature=0` and
`response_format={"type":"json_object"}`. Returns `AiResult(content, costUsd,
latencyMs)`, where `costUsd` comes from the response `usage.cost`.

- The API key is decrypted from `ApiKey` on demand (`ApiKeyService.resolveDefaultPlainKey()`).
- `PromptFactory` owns **all** prompts (moderation severity prompt, single
  classification prompt, batch classification prompt).
- Failures throw `AiProviderException`.

---

## 11. Moderation module

Endpoint: `POST /api/moderation {"policyId": <id>, "text": "..."}` →
`{verdict, severity, risk, categories, reason, modelId, modelName, latencyMs, policyId}`.

Flow (`ModerationService.moderate`):

1. Load policy, require `active=true` (else 400).
2. Resolve BYO key (`ApiKeyService.resolveDefaultPlainKey()`, else 400).
3. Build a **severity prompt** (`PromptFactory.severitySystemPrompt`): the model
   rates severity as `NONE|LOW|MODERATE|HIGH` and returns JSON
   `{"severity": "...", "categories": [...], "reason": "..."}`.
4. Call the policy's primary model; on failure try `fallbackModelId`.
5. **Verdict mapping** (app-side, per the design decision):
   `severityOrdinal >= floor(threshold * 4)` → `policy.action`, else `ALLOW`
   (ordinals NONE=0, LOW=1, MODERATE=2, HIGH=3). So threshold 0.5 triggers on
   MODERATE/HIGH, 0.75 only on HIGH, etc.
6. Save a `ModerationLog` row and return the response.

Rationale: keeping a **categorical severity** (not a raw 0–1 score) is more
cross-model consistent; the final verdict is what gets measured.

Not implemented yet: image/vision moderation (would need a multimodal
`OpenRouterClient` call), `rules` pre-filter (blacklist/regex), and the
`/api/moderation/logs` read endpoint.

---

## 12. Benchmark module

Endpoint: `POST /api/benchmarks/runs` (returns 202, run is executed async) →
`{datasetId, policyId?, modelIds[], level, apiKeyId?, batchSize?}`.
`policyId` is **optional** and only stored as metadata (benchmark classifies into
**dataset labels**, independent of policy rules). `batchSize` overrides the global
default for that run.

Execution (`BenchmarkExecutor`, runs on the `benchmarkTaskExecutor` pool):

- Status transitions `PENDING → RUNNING → COMPLETED | FAILED`.
- Sample selection per level, **balanced per class**: `EXTRA_LIGHT` = 50/class,
  `LIGHT` = 500/class, `FULL` = all samples. Order is shuffled deterministically
  by run id.
- **Batching**: samples are grouped into batches of `batchSize` (default 10) and
  sent as one request (`PromptFactory.classificationBatchSystemPrompt`), expecting
  a JSON array `[{"id": n, "label": "..."}]` in input order. If the batch
  response is unusable, it **falls back to individual calls** for that batch.
- **Progress**: every 25 samples it logs and persists `BenchmarkResult.processedSamples`;
  logs also include `starting model ...` and a final per-model summary.
- Metrics (`Metrics.compute`): **macro-averaged** precision/recall/F1 over the label
  set + accuracy. Failed/null predictions count as incorrect. Also stores avg latency
  (ms) and total cost (USD, from OpenRouter `usage.cost`) and `errorCount`.

Read: `GET /api/benchmarks/runs`, `GET /api/benchmarks/runs/{id}` (status + results),
`GET /api/benchmarks/runs/{id}/results`.

**Methodology caveat (important for the thesis):** batch size can affect results
(context/priming effects, label-prior bias on imbalanced data, position bias).
Use a **fixed batch size across all compared runs** and document it. `batchSize`
per run exists to support a sensitivity experiment (e.g. 1/5/10/20).

Timing reference (single vs batch, cheap model): single ≈ 3.8 s/sample;
`batchSize=10` ≈ 1.5 s/sample (~2.5× faster) on the same setup.

---

## 13. Conventions

- DTOs are **Java records**; entities never leave the service layer (always map to DTOs).
- Validation via `jakarta.validation` annotations on request records.
- Errors: throw `ApiException` subclasses → handled by `GlobalExceptionHandler`;
  AI failures (`AiProviderException`) → **502 Bad Gateway**.
- All user-facing strings, comments and docs in **English** (except the READMEs).
- Keep prompts centralized in `PromptFactory`.
- Logs: use SLF4J; benchmark progress is INFO, failures WARN/ERROR.

---

## 14. Known decisions & gotchas

- **Policy in benchmark is optional** — reference metadata only (see §12).
- **Hibernate `ddl-auto=update` limitation**: adding a `NOT NULL` column to a
  table that already has rows fails. New columns added to existing tables should
  be **nullable** (e.g. `BenchmarkResult.processedSamples` is `Integer`).
- **Shared dev DB + shared JVM ports**: don't assume an empty DB; clean up test
  rows you create. Don't kill processes by name (see §3).
- **No sudo**: the environment has no passwordless sudo; the toolchain is
  user-local in `/home/martin/tools`. Docker is not usable by the agent user
  (not in the `docker` group), hence the system PostgreSQL is used and
  `docker-compose.yml` is unused.
- **`docker-compose.yml`** exists but is not used at runtime.
- Model `modelId` must be a valid OpenRouter slug; free models (`:free`) are much
  slower and may not support `response_format=json_object`.

---

## 15. Current status

Implemented and verified:

- **Milestone 1** — project scaffold (Spring Boot, Java 21, Maven), PostgreSQL
  connection, toolchain, `.gitignore`, dataset download/prep.
- **Milestone 2** — all core entities + repositories; CRUD REST for models,
  policies, API keys; HTTP Basic admin security; AES/GCM key encryption; model seed.
- **Milestone 3** — OpenRouter integration; dataset import; benchmark engine
  (async, levels, metrics, latency/cost) + progress logging + batching.
- **Milestone 4** — moderation v1 (text, severity-based verdict mapping, logging).
- Tests: `mvn test` green (`AutomodApplicationTests`, `MetricsTest`).
- Postman collection covers all current endpoints.

Known verification notes: model calls were only exercised with a **fake key**
(→ expected `401` → `errorCount`), so real quality numbers require the user's own
OpenRouter key.

---

## 16. Roadmap / not yet done

1. **Vision moderation** — image input (`imageUrl` / `imageBase64`) via multimodal
   OpenRouter call; extend `ModerationRequest`/`ModerationResponse`.
2. **Rules pre-filter** — blacklist/regex from `Policy.rules` evaluated before the
   model call (fast deterministic BLOCK).
3. **Moderation logs read API** — `GET /api/moderation/logs` (filterable).
4. **Dashboard** — summaries/graphs of moderations + benchmark comparisons.
5. **Published (pre-prepared) benchmark results** — `published` flag already exists.
6. **Frontend** — Vue.js 3 web UI for policies/models/benchmark/dashboard.
7. **Export** — benchmark results to CSV for the thesis.
8. **Auth/tenancy** — JWT login, real multi-tenant isolation (tenantId already in place).

---

## 17. How to extend this document

- When adding an **entity/endpoint/module**: update §6 (data model), §9 (REST API),
  the matching module section (§11/§12), and §15/§16 (status/roadmap).
- When adding **config properties**: add a row in §7.
- When making a **design decision or hitting a gotcha**: add it to §14.
- Keep the README (Slovak) as the *design/thesis* document; keep AGENTS.md as the
  *engineering context* document. Don't duplicate large prose between them.

---

_Last substantial update: moderation v1 + benchmark batching + per-run batchSize._
