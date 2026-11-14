from __future__ import annotations

import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from app.ingest import validate_cells
from app.main_table import Cell, MainTable
from app.offline_scorer import run_offline_demo


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass
class Job:
    id: str
    status: str
    k: int
    output: str | None = None
    started_at: str | None = None
    finished_at: str | None = None
    ok: bool | None = None
    overall: float | None = None
    error: str | None = None
    scorecard_json: str | None = None
    scorecard_md: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "status": self.status,
            "k": self.k,
            "output": self.output,
            "started_at": self.started_at,
            "finished_at": self.finished_at,
            "ok": self.ok,
            "overall": self.overall,
            "error": self.error,
            "scorecard_json": self.scorecard_json,
            "scorecard_md": self.scorecard_md,
        }


@dataclass
class AppState:
    work_root: Path
    table: MainTable = field(default_factory=MainTable)
    jobs: dict[str, Job] = field(default_factory=dict)
    scorecards: dict[str, dict[str, Any]] = field(default_factory=dict)
    lock: threading.Lock = field(default_factory=threading.Lock)
    pool: ThreadPoolExecutor = field(default_factory=lambda: ThreadPoolExecutor(max_workers=4))

    def create_job(self, k: int, output: str | None, sync: bool) -> Job:
        jid = uuid.uuid4().hex[:12]
        out = Path(output) if output else self.work_root / "jobs" / jid
        job = Job(id=jid, status="running", k=k, output=str(out), started_at=_now())
        with self.lock:
            self.jobs[jid] = job
        if sync:
            self._finish(jid, k, out)
        else:
            self.pool.submit(self._finish, jid, k, out)
        with self.lock:
            return self.jobs[jid]

    def _finish(self, jid: str, k: int, out: Path) -> None:
        try:
            scorecard = run_offline_demo(out, k)
            with self.lock:
                self.scorecards[jid] = scorecard
                self.table.ingest_demo_scorecard(scorecard, k)
                prev = self.jobs[jid]
                self.jobs[jid] = Job(
                    id=jid,
                    status="done",
                    k=k,
                    output=str(out),
                    started_at=prev.started_at,
                    finished_at=_now(),
                    ok=bool(scorecard["ok"]),
                    overall=float(scorecard["overall"]),
                    error=None,
                    scorecard_json=str(out / "eval-scorecard.json"),
                    scorecard_md=str(out / "EVAL.md"),
                )
        except Exception as exc:  # noqa: BLE001 — surface to API
            with self.lock:
                prev = self.jobs.get(jid)
                self.jobs[jid] = Job(
                    id=jid,
                    status="failed",
                    k=k,
                    output=str(out),
                    started_at=prev.started_at if prev else _now(),
                    finished_at=_now(),
                    ok=False,
                    overall=None,
                    error=str(exc),
                )

    def upsert_cells(self, rows: list[dict[str, Any]]) -> int:
        validated = validate_cells(rows)
        n = 0
        with self.lock:
            for row in validated:
                cell = Cell(
                    bench=row["bench"],
                    harness=row["harness"],
                    mode=row["mode"],
                    k=row["k"],
                    pass_rate=row["pass_rate"],
                    pass_rate_sd=row["pass_rate_sd"],
                    steps=row["steps"],
                    steps_sd=row["steps_sd"],
                    n_seeds=row["n_seeds"],
                    status=row["status"],
                    source=row["source"],
                    meta=dict(row["meta"] or {}),
                )
                self.table.put(cell)
                n += 1
        return n
