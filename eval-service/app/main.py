from __future__ import annotations

import os
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse, PlainTextResponse

from app import __version__
from app.ingest import IngestError
from app.livellm_import import (
    default_docs_path,
    load_path,
    parse_payload,
    write_docs_main_table,
)
from app.ds_campaign import get_active, start_campaign
from app.models import (
    CellsIn,
    DocsSyncRequest,
    DsCampaignRequest,
    EvalRunRequest,
    LiveLlmImportRequest,
)
from app.protocol import protocol_payload
from app.store import AppState

WORK = Path(os.environ.get("METAMORPH_EVAL_WORK", "runs/eval-service")).resolve()
STATE = AppState(work_root=WORK)

app = FastAPI(
    title="Metamorph Eval Service",
    version=__version__,
    description="Standalone paper-aligned evaluation service (WB/JB/LAB main table + offline Demo)",
)


@app.get("/")
def root() -> dict[str, Any]:
    return {
        "service": "metamorph-eval",
        "version": __version__,
        "docs": "/docs",
        "health": "/health",
        "protocol": "/v1/protocol",
    }


@app.get("/health")
def health() -> dict[str, Any]:
    payload = STATE.table.payload()
    comp = payload.get("completeness") or {}
    return {
        "ok": True,
        "service": "metamorph-eval",
        "version": __version__,
        "protocol": "Harness2 §2–§3 Propose–Probe–Compose / R=4k P=3k / Judge→Report",
        "jobs": len(STATE.jobs),
        "table_filled": payload["filled"],
        "table_pending": payload["pending"],
        "paper_complete_pct": comp.get("pct"),
        "work_root": str(STATE.work_root),
    }


@app.get("/v1/protocol")
def protocol() -> dict[str, Any]:
    return protocol_payload()


@app.post("/v1/eval/runs")
def create_run(body: EvalRunRequest) -> JSONResponse:
    job = STATE.create_job(k=body.k, output=body.output, sync=body.sync)
    code = 200 if body.sync else 202
    return JSONResponse(status_code=code, content=job.to_dict())


@app.get("/v1/eval/runs")
def list_runs() -> dict[str, Any]:
    return {"ok": True, "jobs": [j.to_dict() for j in STATE.jobs.values()]}


@app.get("/v1/eval/runs/{job_id}")
def get_run(job_id: str) -> dict[str, Any]:
    job = STATE.jobs.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="job not found")
    return job.to_dict()


@app.get("/v1/tables/main")
def get_main_table(
    format: str = Query(default="json", pattern="^(json|md|markdown)$"),
    filled_only: bool = False,
):
    if format in ("md", "markdown"):
        return PlainTextResponse(
            STATE.table.render_markdown(filled_only=filled_only),
            media_type="text/markdown; charset=utf-8",
        )
    return STATE.table.payload()


@app.get("/v1/tables/main/completeness")
def main_table_completeness(k: int = 1) -> dict[str, Any]:
    return STATE.table.completeness(k)


@app.post("/v1/tables/main/export")
def export_main_table(
    filled_only: bool = False,
    filename: str = "MAIN_TABLE.md",
) -> dict[str, Any]:
    """Write Markdown main table under the service work root (docs sync helper)."""
    safe = Path(filename).name
    if not safe.lower().endswith(".md"):
        raise HTTPException(status_code=400, detail="filename must end with .md")
    path = STATE.work_root / safe
    path.parent.mkdir(parents=True, exist_ok=True)
    text = STATE.table.render_markdown(filled_only=filled_only)
    path.write_text(text, encoding="utf-8")
    return {"ok": True, "path": str(path), "bytes": path.stat().st_size, "lifts": STATE.table.lift_rows()}


@app.put("/v1/tables/main")
def put_main_table(body: CellsIn) -> dict[str, Any]:
    rows: list[dict[str, Any]] = []
    if body.cells:
        rows = [c.model_dump() for c in body.cells]
    elif body.bench and body.harness:
        rows = [
            {
                "bench": body.bench,
                "harness": body.harness,
                "mode": body.mode or "agg",
                "k": body.k or 1,
                "pass_rate": body.pass_rate,
                "pass_rate_sd": body.pass_rate_sd,
                "steps": body.steps,
                "steps_sd": body.steps_sd,
                "n_seeds": body.n_seeds,
                "status": body.status or "filled",
                "source": body.source or "ingest",
                "meta": body.meta or {},
            }
        ]
    else:
        raise HTTPException(status_code=400, detail="provide cells[] or a single cell body")
    try:
        n = STATE.upsert_cells(rows)
    except IngestError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"ok": True, "upserted": n, "table": STATE.table.payload()}


@app.post("/v1/import/livellm")
def import_livellm(body: LiveLlmImportRequest) -> dict[str, Any]:
    """Batch-upsert LiveLLM / Harness² cells from path, JSON/JSONL text, or cells[]."""
    try:
        rows: list[dict[str, Any]] = []
        if body.path:
            rows.extend(load_path(body.path, STATE.work_root, Path.cwd()))
        if body.text:
            rows.extend(parse_payload(body.text))
        if body.cells:
            rows.extend([c.model_dump() for c in body.cells])
        if not rows:
            raise IngestError("provide path, text, and/or cells")
        n = STATE.upsert_cells(rows)
    except IngestError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"ok": True, "upserted": n, "table": STATE.table.payload()}


@app.post("/v1/sync/docs")
def sync_docs(body: DocsSyncRequest | None = None) -> dict[str, Any]:
    """Export filled main table Markdown into a docs path for repo sync."""
    req = body or DocsSyncRequest()
    if req.dest:
        dest = Path(req.dest)
    else:
        env_dest = os.environ.get("METAMORPH_EVAL_DOCS")
        dest = Path(env_dest) if env_dest else default_docs_path()
    if dest.suffix.lower() != ".md":
        dest = dest / "MAIN_TABLE.md"
    md = STATE.table.render_markdown(filled_only=req.filled_only)
    path = write_docs_main_table(md, dest)
    # also mirror under work_root for local artifact browsing
    mirror = write_docs_main_table(md, STATE.work_root / "MAIN_TABLE.md")
    return {
        "ok": True,
        "path": str(path),
        "mirror": str(mirror),
        "bytes": path.stat().st_size,
        "filled_only": req.filled_only,
        "lifts": STATE.table.lift_rows(),
    }


@app.get("/v1/scorecards/{job_id}")
def get_scorecard(job_id: str) -> dict[str, Any]:
    sc = STATE.scorecards.get(job_id)
    if not sc:
        raise HTTPException(status_code=404, detail="scorecard not found")
    return {"id": job_id, **sc}


@app.post("/v1/campaign/ds")
def start_ds_campaign(body: DsCampaignRequest) -> JSONResponse:
    """Start DeepSeek-judged framework eval loops (offline scorecard + DS paper judge)."""
    name = (body.name or "ds-eval100").replace("..", "").replace("/", "-").replace("\\", "-")
    root = STATE.work_root / "campaigns" / name
    try:
        camp = start_campaign(root, target=max(1, body.target), k=max(1, body.k))
    except RuntimeError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc
    return JSONResponse(status_code=202, content=camp.progress())


@app.get("/v1/campaign/ds")
def ds_campaign_status() -> dict[str, Any]:
    camp = get_active()
    if not camp:
        # fall back to latest progress file if present
        prog = STATE.work_root / "campaigns" / "ds-eval100" / "progress.json"
        if prog.is_file():
            import json

            return json.loads(prog.read_text(encoding="utf-8"))
        return {"status": "idle", "done": 0, "target": 0}
    return camp.progress()


@app.post("/v1/campaign/ds/stop")
def stop_ds_campaign() -> dict[str, Any]:
    camp = get_active()
    if not camp:
        raise HTTPException(status_code=404, detail="no active campaign")
    camp.stop()
    return {"ok": True, "progress": camp.progress()}


def run() -> None:
    import uvicorn

    host = os.environ.get("METAMORPH_EVAL_HOST", "127.0.0.1")
    port = int(os.environ.get("METAMORPH_EVAL_PORT", "8787"))
    uvicorn.run("app.main:app", host=host, port=port, reload=False)


if __name__ == "__main__":
    run()
