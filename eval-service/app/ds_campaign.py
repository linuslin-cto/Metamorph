"""DeepSeek × eval-service campaign: offline framework scorecard + DS judge."""

from __future__ import annotations

import json
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from app.deepseek import judge_framework_scorecard
from app.offline_scorer import run_offline_demo


def _now() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat()


@dataclass
class Campaign:
    root: Path
    target: int = 100
    k: int = 1
    status: str = "idle"
    done: int = 0
    ok: int = 0
    fail: int = 0
    ds_ok: int = 0
    ds_fail: int = 0
    started: str | None = None
    updated: str | None = None
    last_error: str | None = None
    last_score: float | None = None
    last_pass: bool | None = None
    model: str | None = None
    _stop: threading.Event = field(default_factory=threading.Event)
    _thread: threading.Thread | None = None

    def progress(self) -> dict[str, Any]:
        return {
            "kind": "ds_framework_eval",
            "status": self.status,
            "target": self.target,
            "done": self.done,
            "ok": self.ok,
            "fail": self.fail,
            "ds_ok": self.ds_ok,
            "ds_fail": self.ds_fail,
            "k": self.k,
            "started": self.started,
            "updated": self.updated,
            "last_error": self.last_error,
            "last_score": self.last_score,
            "last_pass": self.last_pass,
            "model": self.model,
        }

    def write_progress(self) -> None:
        self.root.mkdir(parents=True, exist_ok=True)
        path = self.root / "progress.json"
        path.write_text(
            json.dumps(self.progress(), ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            raise RuntimeError("campaign already running")
        self._stop.clear()
        self.status = "running"
        self.started = self.started or _now()
        self.updated = _now()
        self.write_progress()
        self._thread = threading.Thread(target=self._run, name="ds-campaign", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()

    def _run(self) -> None:
        rounds_path = self.root / "rounds.jsonl"
        try:
            while self.done < self.target and not self._stop.is_set():
                rid = self.done + 1
                round_dir = self.root / "rounds" / f"{rid:04d}"
                row: dict[str, Any] = {
                    "round": rid,
                    "started": _now(),
                }
                try:
                    scorecard = run_offline_demo(round_dir, self.k)
                    offline_ok = bool(scorecard.get("ok"))
                    ds = judge_framework_scorecard(scorecard, round_id=rid)
                    self.model = ds.get("model")
                    passed = offline_ok and bool(ds.get("ok"))
                    row.update(
                        {
                            "offline_ok": offline_ok,
                            "offline_overall": scorecard.get("overall"),
                            "ds_ok": ds.get("ok"),
                            "ds_score": ds.get("score"),
                            "ds_summary": ds.get("summary"),
                            "ds_issues": ds.get("issues"),
                            "pass": passed,
                            "model": ds.get("model"),
                            "finished": _now(),
                        }
                    )
                    (round_dir / "ds_judge.json").write_text(
                        json.dumps(ds, ensure_ascii=False, indent=2) + "\n",
                        encoding="utf-8",
                    )
                    self.done = rid
                    if offline_ok:
                        self.ok += 1
                    else:
                        self.fail += 1
                    if ds.get("ok"):
                        self.ds_ok += 1
                    else:
                        self.ds_fail += 1
                    self.last_pass = passed
                    self.last_score = float(ds.get("score") or 0)
                    self.last_error = None
                except Exception as exc:  # noqa: BLE001
                    row.update({"pass": False, "error": str(exc), "finished": _now()})
                    self.done = rid
                    self.fail += 1
                    self.ds_fail += 1
                    self.last_pass = False
                    self.last_error = str(exc)
                with rounds_path.open("a", encoding="utf-8") as fh:
                    fh.write(json.dumps(row, ensure_ascii=False) + "\n")
                self.updated = _now()
                self.write_progress()
                # brief pause to be polite to API
                time.sleep(0.3)
            self.status = "done" if self.done >= self.target else "stopped"
            self.updated = _now()
            self.write_progress()
        except Exception as exc:  # noqa: BLE001
            self.status = "failed"
            self.last_error = str(exc)
            self.updated = _now()
            self.write_progress()


_ACTIVE: Campaign | None = None
_LOCK = threading.Lock()


def get_active() -> Campaign | None:
    return _ACTIVE


def start_campaign(root: Path, *, target: int = 100, k: int = 1) -> Campaign:
    global _ACTIVE
    with _LOCK:
        if _ACTIVE and _ACTIVE.status == "running":
            raise RuntimeError("a DS campaign is already running")
        camp = Campaign(root=root, target=target, k=k)
        camp.start()
        _ACTIVE = camp
        return camp
