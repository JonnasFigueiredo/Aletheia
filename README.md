# Aletheia — Java LLM Evaluation Framework

[![Build](https://github.com/your-org/aletheia/actions/workflows/eval.yml/badge.svg)](https://github.com/your-org/aletheia/actions/workflows/eval.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 17](https://img.shields.io/badge/Java-17-blue.svg)](#quick-start)

LLMs are non-deterministic — how do you test them? Aletheia is a deterministic, provider-agnostic
Java testing framework for LLMs. It runs a suite of structured eval cases against a mock client
(zero API cost, zero flakiness), compares scores against a committed baseline snapshot, and fails
the build when quality regresses. Switch to a real provider for any case by setting two environment
variables.

## Why Aletheia

| Problem | Solution |
|---|---|
| LLMs produce different outputs on every call | Temperature 0 + deterministic mock fixtures for CI |
| "Did my prompt change hurt quality?" | Baseline snapshots catch silent regressions across evaluators |
| Every evaluator is a one-off script | Composable evaluator interface — mix JSON schema, semantic similarity, rubric judges, safety checks |
| CI costs money | Mock provider runs for free; real-provider runs are opt-in (nightly/manual) |

## Quick start

No API key required:

```bash
git clone https://github.com/your-org/aletheia.git
cd aletheia
mvn verify
```

This runs all eval cases against the mock client, enforces no baseline regressions (baseline file
already committed), and produces `target/aletheia/summary.html`.

To record a new baseline after improving your prompts:

```bash
mvn exec:java -Dexec.mainClass=io.aletheia.runner.Cli \
    -Dexec.args="record-baseline --provider mock --cases evalcases/"
```

## Run against a real provider

Set environment variables before running `mvn verify`. Baseline enforcement is optional for real
providers.

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `LLM_PROVIDER` | `mock` | `mock` · `openai` · `anthropic` · `custom` |
| `LLM_BASE_URL` | _(none)_ | Base URL for OpenAI-compatible APIs |
| `LLM_API_KEY` | _(none)_ | API key for the selected provider |
| `LLM_MODEL` | _(none)_ | Model identifier |
| `ALETHEIA_TEMPERATURE` | `0` | Sampling temperature (0 = deterministic) |
| `ALETHEIA_FIXTURE_PATH` | `fixtures/mock-responses.yaml` | Path to mock fixture file |
| `LLM_TIMEOUT_SECONDS` | `30` | HTTP timeout for real providers |

### OpenAI

```bash
export LLM_PROVIDER=openai
export LLM_BASE_URL=https://api.openai.com/v1
export LLM_API_KEY=sk-...
export LLM_MODEL=gpt-4o-mini
mvn -Daletheia.baseline.enforce=false verify
```

### DeepSeek (same adapter, different base URL)

```bash
export LLM_PROVIDER=openai
export LLM_BASE_URL=https://api.deepseek.com/v1
export LLM_API_KEY=sk-...
export LLM_MODEL=deepseek-chat
mvn -Daletheia.baseline.enforce=false verify
```

### Anthropic

```bash
export LLM_PROVIDER=anthropic
export LLM_API_KEY=sk-ant-...
export LLM_MODEL=claude-3-5-haiku-20241022
mvn -Daletheia.baseline.enforce=false verify
```

### Ollama (local)

```bash
export LLM_PROVIDER=openai
export LLM_BASE_URL=http://localhost:11434/v1
export LLM_API_KEY=ollama
export LLM_MODEL=llama3.2
mvn -Daletheia.baseline.enforce=false verify
```

## Evaluator catalogue

### `json_schema`

Validates the response as JSON against a JSON Schema (Draft-07).

```yaml
json_schema:
  type: object
  properties:
    name: { type: string }
    age:  { type: integer }
  required: [name, age]
evaluators:
  - type: json_schema
```

### `contains`

Checks required and forbidden substrings. Case-insensitive by default.

```yaml
evaluators:
  - type: contains
    must_include: ["Ernest Hemingway"]
    must_not_include: ["I don't know"]
```

### `regex`

Matches the response against a regular expression.

```yaml
evaluators:
  - type: regex
    pattern: "^\\d{4}-\\d{2}-\\d{2}$"
```

### `semantic_similarity`

Compares the response to a `golden` answer via embedding cosine similarity (or bag-of-words when
embeddings are unavailable).

```yaml
golden: "Ernest Hemingway"
evaluators:
  - type: semantic_similarity
    threshold: 0.7
```

### `faithfulness`

Verifies the response is grounded in the provided `context:`. Uses an LLM judge for real providers
and a token-overlap heuristic for the mock.

```yaml
context: "The StellarPod has a 30-hour battery life."
evaluators:
  - type: faithfulness
```

### `rubric_judge`

Scores the response against named criteria (0–5 each) using an LLM judge. Falls back to a
length-based heuristic for the mock client.

```yaml
evaluators:
  - type: rubric_judge
    threshold: 0.6
    rubric:
      clarity: "Is the explanation clear?"
      correctness: "Is the content accurate?"
```

### `latency_budget`

Fails when response latency exceeds the budget. Partial credit for near-misses.

```yaml
evaluators:
  - type: latency_budget
    max_latency_ms: 1500
```

### `cost_budget`

Fails when the estimated USD cost of the response exceeds the budget.

```yaml
evaluators:
  - type: cost_budget
    max_cost_usd: 0.01
```

### `safety`

Checks the response for toxic or harmful content. Uses a `forbidden_terms` list plus a built-in
toxic-phrase seed for the heuristic path.

```yaml
evaluators:
  - type: safety
    forbidden_terms: ["make a bomb", "explosive mixture"]
```

## Add a new evaluator

1. Implement `io.aletheia.eval.Evaluator`:

   ```java
   final class MyEvaluator implements Evaluator {
     public String type() { return "my_evaluator"; }
     public EvalResult evaluate(EvalCase c, LlmResponse r) { ... }
   }
   ```

2. Register it in `Evaluators.forSpec()` (one `case` line in the `switch`).

3. Write a unit test in `src/test/java/io/aletheia/eval/`.

## Add a new provider

1. Implement `io.aletheia.client.LlmClient`.
2. Register it in `LlmClientFactory.fromConfig()`.
3. Set `LLM_PROVIDER=<your-id>` to activate it.

## Example report

After `mvn verify`, open `target/aletheia/summary.html` in a browser for a per-case pass/fail
table with scores, latency, and cost. The Allure report is published to GitHub Pages on every push
to `main`.

## Roadmap

- Dataset versioning (track prompt ↔ fixture ↔ score triples across commits)
- Drift dashboards (score trend over time per case / per evaluator)
- Prompt versioning and A/B comparison mode
- Parallel case execution
- gRPC streaming latency measurement

## License

MIT — see [LICENSE](LICENSE).
