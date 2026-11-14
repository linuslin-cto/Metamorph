#!/usr/bin/env python3
"""Run N DeepSeek-judged Metamorph framework eval rounds via eval-service logic."""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.deepseek import bootstrap_env  # noqa: E402
from app.ds_campaign import Campaign  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--target", type=int, default=100)
    ap.add_argument("--k", type=int, default=1)
    ap.add_argument(
        "--root",
        default=str(ROOT / "runs" / "eval-service" / "campaigns" / "ds-eval100"),
    )
    ap.add_argument("--poll", type=float, default=5.0)
    args = ap.parse_args()
    bootstrap_env()
    camp = Campaign(root=Path(args.root), target=args.target, k=args.k)
    print(f"starting DS framework campaign target={args.target} root={camp.root}")
    camp.start()
    while True:
        p = camp.progress()
        print(
            f"[{p['status']}] done={p['done']}/{p['target']} "
            f"ok={p['ok']} fail={p['fail']} ds_ok={p['ds_ok']} ds_fail={p['ds_fail']} "
            f"last_score={p['last_score']}"
        )
        if p["status"] in ("done", "stopped", "failed"):
            break
        time.sleep(args.poll)
    return 0 if p.get("fail", 0) == 0 and p.get("status") == "done" else 1


if __name__ == "__main__":
    raise SystemExit(main())
