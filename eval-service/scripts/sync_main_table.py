#!/usr/bin/env python3
"""CLI: import LiveLLM JSONL (optional) and sync MAIN_TABLE.md into Metamorph/docs."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.livellm_import import (  # noqa: E402
    default_docs_path,
    load_path,
    write_docs_main_table,
)
from app.main_table import MainTable  # noqa: E402
from app.offline_scorer import run_offline_demo  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--import", dest="import_path", help="JSON/JSONL LiveLLM cells")
    ap.add_argument("--demo", action="store_true", help="also run offline demo k=1 into table")
    ap.add_argument("--dest", default=str(default_docs_path()), help="docs MAIN_TABLE.md path")
    ap.add_argument("--work", default="runs/eval-service", help="work root for demo artifacts")
    args = ap.parse_args()

    table = MainTable()
    if args.demo:
        sc = run_offline_demo(Path(args.work) / "cli-demo", 1)
        table.ingest_demo_scorecard(sc, 1)
    if args.import_path:
        from app.main_table import Cell

        rows = load_path(args.import_path, Path(args.work), Path.cwd(), ROOT)
        keys = (
            "bench",
            "harness",
            "mode",
            "k",
            "pass_rate",
            "pass_rate_sd",
            "steps",
            "steps_sd",
            "n_seeds",
            "status",
            "source",
            "meta",
        )
        for row in rows:
            table.put(Cell(**{k: row.get(k) for k in keys if k in row or k in ("meta",)}))
    dest = write_docs_main_table(table.render_markdown(filled_only=True), Path(args.dest))
    print(f"wrote {dest} filled={table.payload()['filled']} lifts={len(table.lift_rows())}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
