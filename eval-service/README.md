# Metamorph Eval Service

Standalone **Python** evaluation service aligned with [Harness²](https://arxiv.org/abs/2610.14224) §2–§3.

Independent from the Java Metamorph CLI — separate process, separate `runs/`, separate deps.
Lives in-repo at `eval-service/` (next to `docs/`).

## What it measures

| Paper item | Service |
|---|---|
| Propose–Probe–Compose / R=4k P=3k / dual modes | `POST /v1/eval/runs` offline Demo scorecard |
| Base → evolved lift, Library, Judge→Report | scorecard dimensions |
| WB / JB / LAB LiveLLM main tables | `GET/PUT /v1/tables/main` + JSONL import |

## Quick start

```bash
cd eval-service
python -m venv .venv
# Windows: .venv\Scripts\activate
source .venv/bin/activate
pip install -e ".[dev]"
pytest -q
uvicorn app.main:app --host 127.0.0.1 --port 8787
```

### Docker

```bash
docker build -t metamorph-eval .
docker run --rm -p 8787:8787 -v "%cd%/runs:/data/runs" metamorph-eval
# or: docker compose up --build
```

## API

- `GET /health` · `GET /v1/protocol`
- `POST /v1/eval/runs` — `{"k":1,"sync":true}`
- `GET/PUT /v1/tables/main` · `?format=md`
- `POST /v1/tables/main/export`
- `POST /v1/import/livellm` — path / JSON / JSONL (per-`seed` rows → mean±SD)
- `POST /v1/sync/docs` — write `docs/MAIN_TABLE.md`
- `GET /v1/scorecards/{id}`

### Import + docs sync (CLI, no HTTP)

```bash
python scripts/sync_main_table.py --demo --import tests/fixtures/livellm_sample.jsonl
```

### Import LiveLLM JSONL

```bash
curl -X POST http://127.0.0.1:8787/v1/import/livellm \
  -H "content-type: application/json" \
  -d "{\"path\":\"tests/fixtures/livellm_seeds.jsonl\"}"
```

## CI

GitHub Actions template: [ci/github-actions-eval-service.yml](ci/github-actions-eval-service.yml) (copy to .github/workflows/ if your token has `workflow` scope).

## Env

| Variable | Default |
|---|---|
| `METAMORPH_EVAL_HOST` | `127.0.0.1` |
| `METAMORPH_EVAL_PORT` | `8787` |
| `METAMORPH_EVAL_WORK` | `runs/eval-service` |
| `METAMORPH_EVAL_DOCS` | `../Metamorph/docs/MAIN_TABLE.md` |

