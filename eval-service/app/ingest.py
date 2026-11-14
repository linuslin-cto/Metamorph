"""Validate LiveLLM / external main-table cell payloads before upsert."""

from __future__ import annotations

from typing import Any

from app.main_table import BENCHES, HARNESSES, MODES


class IngestError(ValueError):
    pass


def validate_cell(row: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(row, dict):
        raise IngestError("cell must be an object")
    bench = str(row.get("bench") or "").strip().lower()
    harness = str(row.get("harness") or "").strip().lower()
    mode = str(row.get("mode") or "agg").strip().lower()
    if bench not in BENCHES:
        raise IngestError(f"bench must be one of {list(BENCHES)}, got {bench!r}")
    if harness not in HARNESSES:
        raise IngestError(f"harness must be one of {list(HARNESSES)}, got {harness!r}")
    if mode not in MODES:
        raise IngestError(f"mode must be one of {list(MODES)}, got {mode!r}")
    try:
        k = int(row.get("k") or 1)
    except (TypeError, ValueError) as exc:
        raise IngestError("k must be an integer >= 1") from exc
    if k < 1:
        raise IngestError("k must be an integer >= 1")

    def _opt_float(name: str) -> float | None:
        v = row.get(name)
        if v is None:
            return None
        try:
            return float(v)
        except (TypeError, ValueError) as exc:
            raise IngestError(f"{name} must be a number") from exc

    pass_rate = _opt_float("pass_rate")
    if pass_rate is not None and not (0.0 <= pass_rate <= 100.0):
        raise IngestError("pass_rate must be in [0, 100]")

    n_seeds = row.get("n_seeds")
    if n_seeds is not None:
        try:
            n_seeds = int(n_seeds)
        except (TypeError, ValueError) as exc:
            raise IngestError("n_seeds must be an integer") from exc
        if n_seeds < 1:
            raise IngestError("n_seeds must be >= 1")

    source = str(row.get("source") or "ingest").strip() or "ingest"
    status = str(row.get("status") or "filled").strip() or "filled"
    if source == "livellm" and pass_rate is None:
        raise IngestError("livellm cells require pass_rate")
    if source == "livellm" and n_seeds is None:
        raise IngestError("livellm cells require n_seeds (paper: multi-seed mean)")

    meta = row.get("meta") or {}
    if not isinstance(meta, dict):
        raise IngestError("meta must be an object")

    return {
        "bench": bench,
        "harness": harness,
        "mode": mode,
        "k": k,
        "pass_rate": pass_rate,
        "pass_rate_sd": _opt_float("pass_rate_sd"),
        "steps": _opt_float("steps"),
        "steps_sd": _opt_float("steps_sd"),
        "n_seeds": n_seeds,
        "status": status,
        "source": source,
        "meta": meta,
    }


def validate_cells(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not rows:
        raise IngestError("no cells to ingest")
    return [validate_cell(r) for r in rows]
