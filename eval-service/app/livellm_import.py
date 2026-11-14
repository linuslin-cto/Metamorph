"""Import LiveLLM / Harness² result files into main-table cells.

Supported inputs:
- JSON array of cell objects
- JSONL: one cell object per line
- Harness²-ish summary rows with ``score`` (mapped to ``pass_rate``)
- Per-seed JSONL rows (``seed`` field) → mean±SD aggregation (paper 3-seed mean)
"""

from __future__ import annotations

import json
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Any

from app.ingest import IngestError, validate_cells


def _as_cell(obj: dict[str, Any]) -> dict[str, Any]:
    row = dict(obj)
    if "pass_rate" not in row and "score" in row:
        row["pass_rate"] = row.pop("score")
    if "harness" not in row and "system" in row:
        row["harness"] = row["system"]
    row.setdefault("source", "livellm")
    row.setdefault("status", "filled")
    return row


def aggregate_seed_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Collapse per-seed rows sharing bench|harness|mode|k into mean±SD cells."""
    groups: dict[tuple[Any, ...], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        key = (
            str(row.get("bench") or "").lower(),
            str(row.get("harness") or "").lower(),
            str(row.get("mode") or "agg").lower(),
            int(row.get("k") or 1),
        )
        groups[key].append(row)

    out: list[dict[str, Any]] = []
    for key, items in groups.items():
        has_seed = any("seed" in it for it in items)
        if len(items) == 1 and not has_seed:
            out.append(items[0])
            continue
        rates: list[float] = []
        steps: list[float] = []
        for it in items:
            if it.get("pass_rate") is None and it.get("score") is None:
                raise IngestError(f"seed row missing pass_rate/score for {key}")
            rates.append(float(it["pass_rate"] if it.get("pass_rate") is not None else it["score"]))
            if it.get("steps") is not None:
                steps.append(float(it["steps"]))
        base = dict(items[0])
        base.pop("seed", None)
        base["bench"], base["harness"], base["mode"], base["k"] = key
        base["pass_rate"] = round(statistics.fmean(rates), 4)
        base["pass_rate_sd"] = (
            round(statistics.stdev(rates), 4) if len(rates) > 1 else 0.0
        )
        base["n_seeds"] = len(rates)
        if steps:
            base["steps"] = round(statistics.fmean(steps), 4)
            if len(steps) > 1:
                base["steps_sd"] = round(statistics.stdev(steps), 4)
        base["source"] = base.get("source") or "livellm"
        base["status"] = base.get("status") or "filled"
        meta = dict(base.get("meta") or {})
        meta["seeds_aggregated"] = len(rates)
        base["meta"] = meta
        out.append(base)
    return out


def _finalize(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return validate_cells(aggregate_seed_rows(rows))


def parse_payload(raw: str) -> list[dict[str, Any]]:
    text = (raw or "").strip()
    if not text:
        raise IngestError("empty import payload")

    # JSON array or single object
    if text[0] in "{[":
        try:
            data = json.loads(text)
        except json.JSONDecodeError:
            data = None
        else:
            if isinstance(data, dict):
                if "cells" in data and isinstance(data["cells"], list):
                    rows = [_as_cell(x) for x in data["cells"] if isinstance(x, dict)]
                else:
                    rows = [_as_cell(data)]
            elif isinstance(data, list):
                rows = [_as_cell(x) for x in data if isinstance(x, dict)]
            else:
                raise IngestError("JSON root must be object or array")
            return _finalize(rows)

    # JSONL
    rows = []
    for i, line in enumerate(text.splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError as exc:
            raise IngestError(f"JSONL line {i}: {exc}") from exc
        if not isinstance(obj, dict):
            raise IngestError(f"JSONL line {i}: expected object")
        rows.append(_as_cell(obj))
    return _finalize(rows)


def resolve_import_path(path: str | Path, *roots: Path) -> Path:
    """Resolve import path; try absolute, then each root / relative."""
    raw = Path(path)
    candidates: list[Path] = []
    if raw.is_absolute():
        candidates.append(raw)
    else:
        candidates.append(Path.cwd() / raw)
        for root in roots:
            if root is not None:
                candidates.append(Path(root) / raw)
        # package-relative fixtures for local smoke
        pkg = Path(__file__).resolve().parents[1]
        candidates.append(pkg / raw)
        candidates.append(pkg / "tests" / "fixtures" / raw.name)
    for c in candidates:
        if c.is_file():
            return c.resolve()
    raise IngestError(f"file not found: {path} (tried {len(candidates)} locations)")


def load_path(path: Path | str, *roots: Path) -> list[dict[str, Any]]:
    p = resolve_import_path(path, *roots)
    return parse_payload(p.read_text(encoding="utf-8"))


def default_docs_path() -> Path:
    """Metamorph docs/MAIN_TABLE.md (eval-service lives beside docs/ under the Java tree)."""
    # app/ -> eval-service/ -> repo root → docs/MAIN_TABLE.md
    return Path(__file__).resolve().parents[1].parent / "docs" / "MAIN_TABLE.md"


def write_docs_main_table(md: str, dest: Path) -> Path:
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(md, encoding="utf-8")
    return dest
