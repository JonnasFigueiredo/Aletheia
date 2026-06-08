# Aletheia — Test Strategy

## Why mock-by-default

LLMs are non-deterministic and expensive. Running every CI build against a live API introduces
flakiness, billing costs, and latency. Aletheia solves this with a **mock client** that replays
hand-crafted fixture responses keyed by case ID. The mock is the default provider (`LLM_PROVIDER=mock`),
so `mvn verify` is always green, fast, and free — no API key required.

Real-provider runs (OpenAI, Anthropic, Ollama) are gated to `workflow_dispatch` and nightly cron
in CI, and are marked `continue-on-error: true` so they can fail without blocking the main pipeline.

## Why temperature 0

`temperature: 0` makes real-provider responses as close to deterministic as the provider allows.
This minimises variance between runs, which matters for baseline comparison: a result that passes
today should also pass tomorrow. Higher temperatures make threshold-based evaluators brittle because
the model's phrasing changes unpredictably.

## How baselines guard against silent regressions

When you swap model versions (e.g., `gpt-4o-mini` → `gpt-4o-mini-2025-01`) or change prompt
wording, subtle score drops may not surface as test failures unless you explicitly track them.

Aletheia's **baseline snapshot** (`baselines/<provider>-<model>.json`) stores every evaluator score
from a known-good run. The `AletheiaBaselineTest` replays the same suite and flags any case whose
score drops more than `tolerance` (default `0.05`) below the baseline. This catches:

- Model updates that regress reasoning quality
- Prompt edits that inadvertently reduce faithfulness or rubric scores
- Provider changes that alter latency or cost characteristics

To regenerate the baseline after an intentional improvement:

```bash
mvn exec:java -Dexec.mainClass=io.aletheia.runner.Cli -Dexec.args="record-baseline"
# or
mvn exec:java -Dexec.mainClass=io.aletheia.runner.Cli \
    -Dexec.args="record-baseline --provider mock --cases evalcases/"
```

Commit the updated `baselines/mock-unknown.json` (or `baselines/<provider>-<model>.json` for real
providers) so future CI runs compare against the new baseline.

## How to choose thresholds

| Evaluator          | Typical threshold | Guidance |
|--------------------|:-----------------:|----------|
| `json_schema`      | n/a (pass/fail)   | Either validates or not — no threshold to tune |
| `contains`         | n/a (pass/fail)   | Either the term is present or not |
| `regex`            | n/a (pass/fail)   | Pattern match is binary |
| `semantic_similarity` | 0.7 – 0.85    | Lower for paraphrases; raise only for near-verbatim answers |
| `faithfulness`     | 0.5 (default)     | Token-overlap heuristic; 0.5 accepts most on-topic answers |
| `rubric_judge`     | 0.6 – 0.75        | Mean rubric score across criteria; 0.6 = "acceptable", 0.75 = "good" |
| `safety`           | n/a (pass/fail)   | Absence of toxic terms is binary |
| `latency_budget`   | task-specific     | Set to P95 of your acceptable latency window |
| `cost_budget`      | task-specific     | Set to your per-call budget ceiling |

Start conservative (low thresholds) and raise them as you collect data on real-provider scores.
Raising a threshold without a corresponding improvement in the model or prompt will cause
regressions and force a baseline re-record — that is the intended friction.

## When to use rubric judge vs. semantic similarity

| Scenario | Recommended evaluator |
|---|---|
| Exact or near-exact answer expected (factual Q&A) | `semantic_similarity` + `contains` |
| Open-ended explanation with no single correct answer | `rubric_judge` |
| Response must be drawn from context (RAG) | `faithfulness` |
| Response must follow a strict schema | `json_schema` |
| Response must not contain certain content | `safety` or `contains` with `must_not_include` |

`rubric_judge` is powered by an LLM judge and calls the API twice per case. For mock runs it uses
a length-based heuristic, so rubric scores in the baseline reflect heuristic values. When enforcing
baselines against real providers, scores will differ from the mock heuristic — set appropriate
thresholds accordingly and maintain separate baselines per provider/model.

## Limitations and trade-offs

| Limitation | Implication |
|---|---|
| Mock baseline scores are heuristic | Faithfulness, rubric, and semantic scores from the mock do not equal real-provider scores. Use real-provider runs to calibrate thresholds before raising them in the baseline. |
| Baseline is point-in-time | If you change a fixture response you must re-record the baseline or the comparison will flag a false regression. |
| Token-overlap faithfulness can be fooled | The heuristic passes responses that repeat context tokens verbatim but are semantically wrong. Use a real LLM judge for production faithfulness checks. |
| Temperature 0 ≠ fully deterministic | Some providers still introduce nondeterminism at temperature 0 (top-k sampling, speculative decoding). Real-provider baselines may drift over time even without intentional changes. |
| No prompt versioning | Aletheia does not track prompt changes across runs. Combine it with a Git commit reference or a prompt registry to trace which prompt version produced which score. |
