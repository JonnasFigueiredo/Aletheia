# Contributing to Aletheia

## Development setup

1. Requires Java 17 and Maven 3.9+.
2. Copy `.env.example` to `.env` and fill in your provider credentials.
3. Run `mvn spotless:apply` before committing to auto-format code.
4. Run `mvn verify` to compile, run tests, and check style gates.

## Commit messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add semantic similarity evaluator
fix: handle missing prompt field in YAML loader
docs: update CONTRIBUTING guide
```

## Code style

- Google Java Format (2-space indent, 100-char lines) — enforced by Spotless.
- Google Checkstyle rules — enforced at `mvn verify`.
- No `TODO` or `FIXME` markers in committed code.

## Regenerating baselines

When you intentionally change prompts or fixtures so that scores improve, you must re-record the
baseline before your PR can pass CI:

```bash
# Mock baseline (committed to the repo):
mvn exec:java -Dexec.mainClass=io.aletheia.runner.Cli \
    -Dexec.args="record-baseline --provider mock --cases evalcases/"

# Real-provider baseline (not committed — keep locally or in a private artefact):
LLM_PROVIDER=openai LLM_API_KEY=sk-... LLM_MODEL=gpt-4o-mini \
    mvn exec:java -Dexec.mainClass=io.aletheia.runner.Cli \
    -Dexec.args="record-baseline --cases evalcases/"
```

Commit `baselines/mock-unknown.json` (or the relevant `baselines/<provider>-<model>.json`) as part
of the same PR that changes the prompts or fixtures.

## Branching policy

- `main` — always green, baseline enforced.
- Feature branches — open a PR against `main`; CI runs the full eval suite.
- Do not merge if `mvn -Daletheia.baseline.enforce=true verify` fails.

## Submitting changes

1. Fork the repository and create a branch from `main`.
2. Add tests for new behaviour.
3. Ensure `mvn verify` passes locally.
4. Open a pull request with a clear description.
