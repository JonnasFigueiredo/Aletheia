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

## Submitting changes

1. Fork the repository and create a branch from `main`.
2. Add tests for new behaviour.
3. Ensure `mvn verify` passes locally.
4. Open a pull request with a clear description.
