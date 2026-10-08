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
   **policy** using an LLM and return a verdict (`ALLOW` / `BLOCK`). The user tunes
   the outcome via the policy's `thresholdSeverity`.
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
- **`README.md` is an early design concept and is intentionally NOT kept in sync
  with the implementation** (do not update it when code/API changes; it carries a
  note saying so). **This file (`AGENTS.md`) is the single source of truth for the
  current state of the code, config and REST API.**

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
| `model/` | JPA entities + enums; `Category` (value type) + `CategoryListConverter` (JSON ⇄ `List<Category>`) |
| `repository/` | Spring Data JPA repositories |
| `security/` | `AesGcmEncryptor` (AES/GCM for API keys) |
| `service/` | Business logic (see below) |

Key services:

- `AiModelService`, `PolicyService`, `ApiKeyService` — CRUD + validation.
- `DatasetService` — imports JSONL datasets at startup, read access, and the label→verdict mapping (`setLabelVerdicts`, `distinctLabels`, `missingVerdictLabels`).
- `ModerationService` — **batched** text moderation (policy → model → severity → verdict); `ModerationMapping` — pure verdict mapping + grouping/aggregation helpers; `SeverityResponseParser` — pure severity-string + severity-response parsing shared by moderation and the benchmark.
- `BenchmarkService` — creates runs (validates moderation prerequisites); `BenchmarkExecutor` — runs them async (both modes).
- `Metrics` — pure metric computation (macro precision/recall/F1 + accuracy).

---

## 6. Data model

Entities (all in `model/`):

| Entity | Key fields | Notes |
|--------|-----------|-------|
| `AiModel` | provider(`openrouter`), `modelId`(unique), name, `type`(TEXT/VISION), enabled | reference catalog; **no** inference params stored (fixed: temperature=0, JSON output) |
| `ApiKey` | tenantId, provider, `encryptedKey` (AES/GCM), label | BYO key; plaintext never returned |
| `Policy` | tenantId, name, description, categories(`List<Category>` JSON), `thresholdSeverity`(NONE/LOW/MODERATE/HIGH), modelId, fallbackModelId, active, version | `version` increments on update/activation change; `thresholdSeverity` = minimum severity that triggers a `BLOCK` (there is no configurable action); each `Category` is `{id, prompt}` (see below) |
| `ModerationLog` | tenantId, policy, model, contentType, verdict, `severity`(Severity enum), categories, confidence, latencyMs, `requestId` | audit of each moderation (one row per moderated text); `requestId` groups the rows of one batched request |
| `Dataset` | name, description, source, labelVerdicts(`List<LabelRule>` JSON) | reference registry (no tenantId); `labelVerdicts` maps each label to ALLOW/BLOCK for the moderation benchmark (see below) |
| `DatasetSample` | dataset, content, imageUrl, expectedLabel | one sample; `expectedLabel` used as ground truth |
| `BenchmarkRun` | tenantId, dataset, policy(nullable), apiKeyId, `mode`(CLASSIFICATION/MODERATION), level, batchSize, status, published, startedAt, finishedAt | one benchmark run; `mode` selects what is measured |
| `BenchmarkResult` | tenantId, run, model, precision, recall, f1, accuracy, avgLatency, cost, errorCount, processedSamples, `thresholdMetrics`(JSON) | one row per model per run; `thresholdMetrics` (moderation mode) holds the per-threshold sweep (see §12) |

**Categories (`model.Category`):** a policy's categories are a `List<Category>` where each
`Category` is `{id, prompt}`. The `prompt` is a **mini-prompt** describing what the category
covers (the only part shown to the model); the `id` is a short, unique, machine-safe slug used
purely for reference/matching/logging. The list is stored as a JSON array in the `policy.categories`
`text` column via `CategoryListConverter` (an `AttributeConverter`), so the service layer works with
typed objects and never parses JSON. The converter also reads the **legacy** bare-string format
(`["hate_speech"]` → `id = prompt = "hate_speech"`), so pre-existing rows keep working with no
migration. `PolicyService.validateCategories` rejects blank/duplicate `id`s and blank `prompt`s.

**Label rules (`model.LabelRule`):** a dataset's `labelVerdicts` is a `List<LabelRule>` where each
`LabelRule` is `{label, verdict}` (`verdict` ∈ `PolicyAction{ALLOW,BLOCK}`). It says which of the
dataset's own labels mean "should be blocked". Stored as a JSON array in the `dataset.label_verdicts`
`text` column via `LabelRuleListConverter` (mirrors `CategoryListConverter`), so no migration is
needed (nullable column). `DatasetService.validateLabelVerdicts` rejects blank/duplicate labels,
null verdicts and labels that are not actually present in the dataset. Used only by the
`MODERATION` benchmark mode; `Dataset.verdictMap()` returns the label→verdict lookup.

**Threshold metrics (`model.MetricScores`):** a `BenchmarkResult.thresholdMetrics` is a
`Map<Severity, MetricScores>` (each `MetricScores` = `{precision, recall, f1, accuracy}`) stored as a
JSON object in the `benchmark_result.threshold_metrics` `text` column via `ThresholdMetricsConverter`.

Enums: `ModelType{TEXT,VISION}`, `PolicyAction{ALLOW,BLOCK}`,
`ContentType{TEXT,IMAGE}`, `BenchmarkLevel{DEBUG,EXTRA_LIGHT,LIGHT,FULL}`,
`BenchmarkMode{CLASSIFICATION,MODERATION}`,
`RunStatus{PENDING,RUNNING,COMPLETED,FAILED}`, `Severity{NONE,LOW,MODERATE,HIGH,UNKNOWN}`
(`UNKNOWN` is an app-side sentinel for unclassified texts and is never a valid policy threshold).

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
| `automoder.benchmark.batch-size` | `10` | samples per OpenRouter call (benchmark) |
| `automoder.moderation.batch-size` | `10` | texts per OpenRouter call (moderation); overridable per request via `batchSize` |

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
| `/api/datasets` | GET, GET `/{id}`, PUT `/{id}/label-verdicts` |
| `/api/benchmarks/runs` | GET, POST, GET `/{id}`, GET `/{id}/results` |
| `/api/moderation` | POST |
| `/actuator/health` | GET (public) |

`GET /api/datasets/{id}` returns the dataset's actual `labels` and its current
`labelVerdicts` (so a UI can render the ALLOW/BLOCK toggles), e.g.
`{"id":2,"name":"tuke_slovak","labels":["hate","not_hate"],"labelVerdicts":[],...}`.

Example request bodies:

    POST /api/models          {"modelId":"openai/gpt-4o-mini","name":"GPT-4o mini","type":"TEXT","enabled":true}
    POST /api/api-keys        {"label":"my-key","key":"sk-or-v1-..."}
    POST /api/policies        {"name":"Hate","description":"...","categories":[{"id":"hate_speech","prompt":"Content attacking people based on protected attributes."},{"id":"violence","prompt":"Content threatening or glorifying physical violence."}],"thresholdSeverity":"MODERATE","modelId":3,"fallbackModelId":null,"active":true}
    PUT  /api/datasets/2/label-verdicts {"labelVerdicts":[{"label":"hate","verdict":"BLOCK"},{"label":"not_hate","verdict":"ALLOW"}]}
    POST /api/benchmarks/runs {"datasetId":1,"policyId":2,"modelIds":[3,8],"mode":"CLASSIFICATION","level":"EXTRA_LIGHT","apiKeyId":null,"batchSize":10}
    POST /api/benchmarks/runs {"datasetId":2,"modelIds":[3],"mode":"MODERATION","level":"EXTRA_LIGHT"}   # dataset needs labelVerdicts; no policy used
    POST /api/moderation      {"policyId":2,"batchSize":10,"items":[{"id":"user-comment-1","text":"text to moderate"},{"id":"user-comment-2","text":"another text"}]}

Error shape (`ApiError`): `{timestamp, status, error, message, path, fieldErrors}`.

---

## 10. AI provider (OpenRouter)

`OpenRouterClient.call(apiKey, modelId, systemPrompt, userContent)` → `POST
{base-url}/chat/completions` with `temperature=0` and
`response_format={"type":"json_object"}`. Returns `AiResult(content, costUsd,
latencyMs)`, where `costUsd` comes from the response `usage.cost`.

- The API key is decrypted from `ApiKey` on demand (`ApiKeyService.resolveDefaultPlainKey()`).
- `PromptFactory` owns **all** prompts (moderation severity prompt, batch moderation
  severity prompt, single classification prompt, batch classification prompt).
- Failures throw `AiProviderException`.

---

## 11. Moderation module

Endpoint: `POST /api/moderation` — **batched**. Request:

    {"policyId": 2, "batchSize": 10, "items": [{"id": "user-comment-1", "text": "..."}, ...]}

- `id` inside an item is an **external id** supplied by the caller; the app also
  assigns its own **internal id** (the 1-based position in the request), which is
  used to reference texts in the batch prompt and appears in the output.
- `batchSize` is optional (1..100) → defaults to `automoder.moderation.batch-size` (10).

Response: the moderated texts grouped by verdict (in input order within each list),
an `error` list of texts that could not be classified, plus request-level metadata:

    {
      "allow": [ {id, externalId, text, verdict, severity, risk, categories, reason, latencyMs, cost}, ... ],
      "block": [ ... ],
      "error": [ ... ],   // unclassified: verdict = null, severity = "UNKNOWN"
      "policyId", "policyName", "thresholdSeverity",
      "modelId", "modelName", "usedFallback",
      "requestId", "batchSize", "batchCount", "totalItems", "errorCount",
      "verdictCounts", "severityCounts", "categoryCounts",
      "latencyMs", "cost", "avgLatencyPerItem", "timestamp"
    }

Flow (`ModerationService.moderate(policyId, items, batchSize)`):

1. Load policy, require `active=true` (else 400).
2. Resolve BYO key (`ApiKeyService.resolveDefaultPlainKey()`, else 400).
3. Build **severity prompts**: single (`PromptFactory.severitySystemPrompt`) and
   batch (`PromptFactory.severityBatchSystemPrompt`). Categories are given to the
   model as a **numbered list of mini-prompts** (`prompt` only — the `id` is never
   shown to the model). The model rates severity as `NONE|LOW|MODERATE|HIGH` and, in
   `categories`, returns the **1-based numbers** of the categories it matched (empty
   array if none). The batch variant returns a JSON array
   `[{"id": n, "severity": "...", "categories": [1,2], "reason": "..."}]` in input order.
   **`id` in that array is batch-relative (1..batchSize)**, because
   `PromptFactory.batchUserContent` numbers texts from 1 within each batch — the
   parser maps it back to the batch position (not the global internal id).
   The returned category **numbers are mapped back to the policy category `id`s and
   filtered to `1..N`**, so the model can never introduce a category that the policy
   did not define (out-of-range numbers are dropped).
4. Split items into batches of `batchSize`; one call per batch. On
   `AiProviderException` retry the batch with `fallbackModelId` (`usedFallback=true`);
   if the batch response is unusable, **fall back to individual calls** for that batch
   (same pattern as the benchmark, §12).
5. **Verdict mapping** (app-side, per the design decision):
   the model's rated severity is compared against the policy's `thresholdSeverity`
   (the **minimum** severity that triggers a block): `severity >= thresholdSeverity`
   → `BLOCK`, else `ALLOW` (order NONE=0, LOW=1, MODERATE=2, HIGH=3 via the
   `Severity` enum). So `thresholdSeverity = MODERATE` blocks MODERATE/HIGH,
   `HIGH` only blocks HIGH, etc. (`UNKNOWN` never triggers via this path.) There is
   no configurable action — a policy always blocks its violations and the user tunes
   the outcome purely via `thresholdSeverity`.
6. **Unclassified texts → `error`**: when a text could not be classified (batch and
   individual attempts failed, or missing from the response) it is placed in the
   `error` list with `verdict=null`, `severity="UNKNOWN"`, `risk=0`, and the error in
   `reason`. It is not a verdict, so it is not counted in `verdictCounts` (the request
   `errorCount` gives the total); `severityCounts` still shows it as `UNKNOWN`.
7. Save one `ModerationLog` row **per text** (carrying `requestId` of the request);
   the log `latencyMs`/`confidence` are the per-item attributed values.
8. Aggregate (`ModerationMapping` — pure, unit-tested) and return the response.

**Cost/latency attribution caveat:** per-item `latencyMs` and `cost` are
**attributions, not per-text measurements** — the whole batch's latency is assigned
to each text of the batch and the batch's `usage.cost` is split evenly
(`batchCost / batchSize`). Same caveat as the benchmark batch-size note (§12).

Rationale: keeping a **categorical severity** (not a raw 0–1 score) is more
cross-model consistent; the final verdict is what gets measured. Batching reduces
HTTP calls and repeated prompt cost.

Not implemented yet: image/vision moderation (would need a multimodal
`OpenRouterClient` call) and the `/api/moderation/logs` read endpoint.

---

## 12. Benchmark module

Endpoint: `POST /api/benchmarks/runs` (returns 202, run is executed async) →
`{datasetId, policyId?, modelIds[], mode?, level, apiKeyId?, batchSize?}`.
`mode` is `CLASSIFICATION` (default) or `MODERATION` and selects what the run measures.
`batchSize` overrides the global default for that run.

- **`CLASSIFICATION`** (default): classifies samples into the dataset's own labels;
  `policyId` is **optional** and only stored as metadata (independent of policy rules).
- **`MODERATION`**: runs the moderation pipeline — the model rates each sample's
  `Severity` against the **dataset's own labels** (severity prompt), the app maps it to an
  `ALLOW`/`BLOCK` verdict at every threshold, and the verdict is scored against the
  dataset's **expected verdict** (from its `labelVerdicts` mapping). **No policy is used**:
  the severity prompt's categories are derived from the dataset itself, so the prompt and
  the ground truth refer to the same concepts (a policy whose categories differ from the
  dataset labels would make the run non-evaluable). Requires a complete label→verdict
  mapping on the dataset, otherwise the request fails fast with `400` (see §6 / §9).
  `policyId` is ignored for `MODERATION` (it stays optional CLASSIFICATION metadata).
- **Moderation categories from labels**: only the dataset labels mapped to `BLOCK` become
  categories (the violation concepts; a text matching none is `NONE`); labels mapped to
  `ALLOW` are **not** categories. Each label is used as both the category `id` and its
  mini-prompt (`new Category(label, label)`), in `labelVerdicts` order.

### Threshold sweep (moderation mode)

Because the model outputs only a categorical severity, the verdict for **every**
threshold can be derived from the same ratings at no extra model cost. The executor
therefore computes the verdict metrics (`Metrics.compute`, macro P/R/F1 + accuracy over
`{ALLOW, BLOCK}`) for all four thresholds and stores them in `BenchmarkResult.thresholdMetrics`
(`{NONE, LOW, MODERATE, HIGH} → {precision, recall, f1, accuracy}`, JSON). A moderation run
has no single operating point, so it leaves the result's scalar
`precision/recall/f1/accuracy` columns **null** and exposes the whole sweep instead (the
sweep *is* the deliverable). The scalar columns remain the classification metrics for
`CLASSIFICATION` runs.

Execution (`BenchmarkExecutor`, runs on the `benchmarkTaskExecutor` pool):

- Status transitions `PENDING → RUNNING → COMPLETED | FAILED`.
- Sample selection per level, **balanced per class**: `DEBUG` = 10/class,
  `EXTRA_LIGHT` = 50/class, `LIGHT` = 500/class, `FULL` = all samples. Levels are
  self-describing: each `BenchmarkLevel` carries its `samplesPerClass` (via
  `getSamplesPerClass()`; `FULL` = `Integer.MAX_VALUE`), so `selectSamples` has no
  `switch`. Order is shuffled deterministically by run id.
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

- **A MODERATION benchmark does not use a policy** — its severity prompt's categories and
  its expected verdicts are both derived from the dataset (`labelVerdicts`), so the prompt
  and the ground truth refer to the same concepts and runs stay comparable across
  (irrelevant) policies. `policyId` is optional reference metadata for both modes (see §12).
- **Moderation benchmark vs. ground truth**: these datasets carry only a categorical
  label, so the moderation benchmark is scored at the **verdict (ALLOW/BLOCK) level**
  (there is no per-row ground-truth severity or category), and the expected verdict is
  derived from the dataset's `labelVerdicts` mapping. Datasets do **not** need per-row
  probability values. Threshold tuning is done by the sweep, not by ground-truth scores.
- **New nullable columns** (three added): `dataset.label_verdicts`, `benchmark_run.mode`,
  `benchmark_result.threshold_metrics` — all nullable `text`/`varchar` so `ddl-auto=update`
  works on a populated DB. Existing `benchmark_run` rows have `mode = NULL`, which the
  executor/response treat as `CLASSIFICATION`.
- **Hibernate `ddl-auto=update` limitation**: adding a `NOT NULL` column to a
  table that already has rows fails. New columns added to existing tables should
  be **nullable** (e.g. `BenchmarkResult.processedSamples` is `Integer`).
- **Policy threshold migration**: `Policy.threshold` (double 0..1) was replaced by
  `Policy.thresholdSeverity` (`Severity` enum). `ddl-auto=update` adds the new
  `threshold_severity` column but does **not** drop the old NOT NULL `threshold`
  column, so on an existing DB run: `ALTER TABLE policy ALTER COLUMN threshold DROP
  NOT NULL;` (and optionally backfill `threshold_severity` then `DROP COLUMN
  threshold`). Otherwise inserts fail because the old column has no default.
- **Policy action removal migration**: `Policy.action` (`PolicyAction`) was removed —
  a policy always `BLOCK`s its violations. `ddl-auto=update` does **not** drop the old
  NOT NULL `action` column and the entity no longer writes it, so on an existing DB run:
  `ALTER TABLE policy DROP COLUMN action;`. Otherwise policy inserts fail on the
  orphaned NOT NULL column. `PolicyAction` is now `{ALLOW, BLOCK}`.
- **Policy rules field removal**: `Policy.rules` (`String`) was removed — it was never
  read by moderation or benchmark (a placeholder for the planned rules pre-filter, see
  §16). `ddl-auto=update` does **not** drop the old (nullable) `rules` column, so on an
  existing DB it stays as a harmless orphan; drop it manually with
  `ALTER TABLE policy DROP COLUMN rules;` if you want a clean schema. It was also removed
  from `PolicyRequest`/`PolicyResponse`, so clients must no longer send it.
- **ModerationLog verdict for failed texts**: the `verdict` column is `NOT NULL`, so
  unclassified texts (which have `verdict = null` in the API response) are logged as
  the literal string `"ERROR"`.
- **Shared dev DB + shared JVM ports**: don't assume an empty DB; clean up test
  rows you create. Don't kill processes by name (see §3).
- **No sudo**: the environment has no passwordless sudo; the toolchain is
  user-local in `/home/martin/tools`. Docker is not usable by the agent user
  (not in the `docker` group), hence the system PostgreSQL is used and
  `docker-compose.yml` is unused.
- **`docker-compose.yml`** exists but is not used at runtime.
- **Model `modelId`** must be a valid OpenRouter slug; free models (`:free`) are much
  slower and may not support `response_format=json_object`.
- **Policy categories are typed objects now**: `Policy.categories` changed from a JSON
  string to `List<Category>` (`{id, prompt}`) via `CategoryListConverter`. The `text`
  column keeps its name, so **no migration is needed**; the converter still reads the
  legacy `["hate_speech"]` array-of-strings form. The moderation prompt shows only the
  `prompt` (numbering each category) and the model replies with category **numbers**
  (`1..N`), which are mapped back to `id`s and filtered — the model can never invent a
  category. `PolicyRequest`/`PolicyResponse.categories` are now arrays of `{id, prompt}`.

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
- **Milestone 5** — batched moderation: `POST /api/moderation` takes a list of texts
  (`items[{id,text}]`) + optional `batchSize` and returns them grouped into
  `allow`/`block` lists plus an `error` list, with request metadata and aggregates;
  unclassified texts go to `error` (verdict `null`, severity `UNKNOWN`);
  `ModerationLog.requestId` correlates rows.
- **Milestone 6** — fully automatic moderation: the configurable `Policy.action` and
  the `FLAG` verdict were removed; a violation is always `BLOCK` and the user tunes
  the outcome via `thresholdSeverity`. The severity prompts no longer mention any
  action. Failed/unclassified texts are surfaced in an `error` list (`verdict = null`).
- **Milestone 7** — policy categories refactored into typed `{id, prompt}` **mini-prompt**
  objects (`model.Category` + `CategoryListConverter`, no DB migration). The moderation
  prompt presents categories as a numbered list of prompts and the model reports the hit
  categories by **1-based number**, which the app maps back to the policy `id`s and filters
  to the input set (invented categories are dropped). `Policy{Request,Response}.categories`
  are arrays of `{id, prompt}`; `PolicyService` validates them (non-blank/unique `id`, non-blank `prompt`).
- **Milestone 8** — moderation benchmark: `Dataset.labelVerdicts` (typed `{label, verdict}`
  mapping, editable via `PUT /api/datasets/{id}/label-verdicts`, no migration), a
  `BenchmarkMode{CLASSIFICATION,MODERATION}` on the run, and a `MODERATION` executor path
  that runs the severity prompt and scores the `ALLOW`/`BLOCK` verdict against the dataset's
  expected verdict. Includes the **threshold sweep** (`BenchmarkResult.thresholdMetrics`:
  verdict metrics for every threshold from the same severity ratings, no extra model cost).
  Severity parsing shared via `SeverityResponseParser`.
- **Milestone 9** — the `MODERATION` benchmark no longer uses a policy: its severity prompt's
  categories come from the dataset (the labels mapped to `BLOCK`, via
  `BenchmarkExecutor.moderationCategories`), and the run reports **only** the threshold sweep —
  it leaves the scalar `precision/recall/f1/accuracy` columns `null` (no single operating
  point). The policy is now optional metadata for both modes; `BenchmarkService`
  `requireModerationPrerequisites` checks only the dataset's label→verdict mapping. No DB
  migration (nothing added; `benchmark_run.policy_id` was already nullable).
- Tests: `mvn test` green (`AutomodApplicationTests`, `MetricsTest`, `ModerationMappingTest`,
  `ModerationServiceTest`, `CategoryListConverterTest`, `LabelRuleListConverterTest`,
  `ThresholdMetricsConverterTest`, `SeverityResponseParserTest`, `BenchmarkExecutorModerationTest`,
  `DatasetServiceLabelVerdictsTest`).
- Postman collection covers all current endpoints.

Known verification notes: model calls were only exercised with a **fake key**
(→ expected `401` → `errorCount`), so real quality numbers require the user's own
OpenRouter key.

---

## 16. Roadmap / not yet done

1. **Vision moderation** — image input (`imageUrl` / `imageBase64`) via multimodal
   OpenRouter call; extend `ModerationRequest`/`ModerationResponse`.
2. **Rules pre-filter** — blacklist/regex evaluated before the model call (fast
   deterministic BLOCK); would need a new field on `Policy` (the old unused `rules`
   field was removed).
3. **Moderation logs read API** — `GET /api/moderation/logs` (filterable).
4. **Dashboard** — summaries/graphs of moderations + benchmark comparisons.
5. **Published (pre-prepared) benchmark results** — `published` flag already exists.
6. **Frontend** — Vue.js 3 web UI for policies/models/benchmark/dashboard. In particular a
   **dataset view** that lists a dataset's `labels` and lets the user toggle each to
   ALLOW/BLOCK (the `labelVerdicts` mapping) via `PUT /api/datasets/{id}/label-verdicts`.
7. **Dataset upload UI** — creating a *new* dataset from the FE (today datasets only appear
   via `DatasetInitializer` scanning `data/processed/`). Separate, larger feature; the
   label-verdict mapping editor works for already-imported datasets without it.
8. **Export** — benchmark results to CSV for the thesis (including the threshold sweep).
9. **Auth/tenancy** — JWT login, real multi-tenant isolation (tenantId already in place).

---

## 17. How to extend this document

- When adding an **entity/endpoint/module**: update §6 (data model), §9 (REST API),
  the matching module section (§11/§12), and §15/§16 (status/roadmap).
- When adding **config properties**: add a row in §7.
- When making a **design decision or hitting a gotcha**: add it to §14.
- Keep the README (Slovak) as the *design/thesis* document; keep AGENTS.md as the
  *engineering context* document. Don't duplicate large prose between them.

---

_Last substantial update: the `MODERATION` benchmark **no longer uses a policy** — its severity prompt's categories are derived from the dataset's `BLOCK` labels (`BenchmarkExecutor.moderationCategories`) and the run reports **only** the threshold sweep (the scalar `precision/recall/f1/accuracy` columns stay `null`, since a moderation run has no single operating point); `BenchmarkService.requireModerationPrerequisites` now checks only the dataset's label→verdict mapping and `policyId` is optional metadata for both modes (no DB migration). Prior update: added the **moderation benchmark** — `Dataset.labelVerdicts` (typed `{label, verdict}` mapping + `LabelRuleListConverter`, editable via `PUT /api/datasets/{id}/label-verdicts`), `BenchmarkMode{CLASSIFICATION,MODERATION}` on the run, a `MODERATION` executor path (severity prompt against the policy → verdict vs. the dataset's expected verdict), and the **threshold sweep** (`BenchmarkResult.thresholdMetrics`, all thresholds from one run at no extra model cost). Shared severity parsing extracted to `SeverityResponseParser`. All new DB columns are nullable (no migration). Prior update: made `BenchmarkLevel` self-describing — each level now carries its `samplesPerClass` (`getSamplesPerClass()`; `DEBUG` = 10/class added, `FULL` = `Integer.MAX_VALUE`), so `BenchmarkExecutor.selectSamples` no longer needs a `switch`. Prior update: removed the unused `Policy.rules` field (never read by moderation/benchmark; it was a placeholder for the planned rules pre-filter) from the entity and the policy DTOs. Prior update: removed the configurable `Policy.action` and the `FLAG` verdict — moderation is fully automatic (`ALLOW`/`BLOCK`, tuned via `thresholdSeverity`); unclassified texts are returned in an `error` list (`verdict = null`). `PolicyAction` is now `{ALLOW, BLOCK}`._
