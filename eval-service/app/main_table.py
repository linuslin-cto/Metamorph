from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any

BENCHES = ("wb", "jb", "lab", "demo")
HARNESSES = (
    "base",
    "metamorph",
    "reasoning_bank",
    "meta_harness",
    "harness_scaling",
)
MODES = ("agg", "seq")


@dataclass
class Cell:
    bench: str
    harness: str
    mode: str
    k: int
    pass_rate: float | None = None
    pass_rate_sd: float | None = None
    steps: float | None = None
    steps_sd: float | None = None
    n_seeds: int | None = None
    status: str = "pending"
    source: str = "paper-slot"
    meta: dict[str, Any] = field(default_factory=dict)

    def key(self) -> str:
        return cell_key(self.bench, self.harness, self.mode, self.k)

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        return d


def cell_key(bench: str, harness: str, mode: str, k: int) -> str:
    return f"{bench.strip().lower()}|{harness.strip().lower()}|{mode.strip().lower()}|{k}"


class MainTable:
    def __init__(self) -> None:
        self._cells: dict[str, Cell] = {}
        self.seed_paper_placeholders(1)

    def seed_paper_placeholders(self, k: int = 1) -> None:
        kk = max(1, int(k))
        for bench in ("wb", "jb", "lab"):
            for harness in HARNESSES:
                for mode in MODES:
                    key = cell_key(bench, harness, mode, kk)
                    self._cells.setdefault(
                        key,
                        Cell(
                            bench=bench,
                            harness=harness,
                            mode=mode,
                            k=kk,
                            status="pending",
                            source="paper-slot",
                            meta={"note": "LiveLLM not yet ingested"},
                        ),
                    )

    def put(self, cell: Cell) -> None:
        self._cells[cell.key()] = cell

    def get(self, bench: str, harness: str, mode: str, k: int) -> Cell | None:
        return self._cells.get(cell_key(bench, harness, mode, k))

    def ingest_demo_scorecard(self, scorecard: dict[str, Any], k: int) -> None:
        kk = max(1, int(k))
        ok = bool(scorecard.get("ok"))
        overall = float(scorecard.get("overall", 0.0))
        pass_rate = 100.0 if ok else 0.0
        meta = {
            "overall": overall,
            "ok": ok,
            "dimensions": len(scorecard.get("dimensions") or []),
        }
        for mode in MODES:
            self.put(
                Cell(
                    bench="demo",
                    harness="base",
                    mode=mode,
                    k=kk,
                    pass_rate=0.0,
                    pass_rate_sd=0.0,
                    n_seeds=1,
                    status="filled",
                    source="offline-demo",
                    meta={"n_passed": 0, "note": "base fails by construction"},
                )
            )
            self.put(
                Cell(
                    bench="demo",
                    harness="metamorph",
                    mode=mode,
                    k=kk,
                    pass_rate=pass_rate,
                    pass_rate_sd=0.0,
                    n_seeds=1,
                    status="filled",
                    source="offline-demo",
                    meta=meta,
                )
            )

    def lift_rows(self) -> list[dict[str, Any]]:
        """Paper-style Base → Metamorph pass-rate deltas where both cells are filled."""
        out: list[dict[str, Any]] = []
        for bench in BENCHES:
            for mode in MODES:
                for k in sorted({c.k for c in self._cells.values()}):
                    base = self.get(bench, "base", mode, k)
                    evolved = self.get(bench, "metamorph", mode, k)
                    if not base or not evolved:
                        continue
                    if base.status != "filled" or evolved.status != "filled":
                        continue
                    if base.pass_rate is None or evolved.pass_rate is None:
                        continue
                    out.append(
                        {
                            "bench": bench,
                            "mode": mode,
                            "k": k,
                            "base": base.pass_rate,
                            "metamorph": evolved.pass_rate,
                            "delta_pp": round(evolved.pass_rate - base.pass_rate, 2),
                            "source_base": base.source,
                            "source_metamorph": evolved.source,
                        }
                    )
        return out

    def completeness(self, k: int = 1) -> dict[str, Any]:
        """Paper main-table readiness: base+metamorph × agg/seq for WB/JB/LAB."""
        kk = max(1, int(k))
        required = ("base", "metamorph")
        benches: dict[str, Any] = {}
        ready = 0
        total = 0
        missing: list[str] = []
        for bench in ("wb", "jb", "lab"):
            slots = []
            bench_ok = True
            for harness in required:
                for mode in MODES:
                    total += 1
                    cell = self.get(bench, harness, mode, kk)
                    filled = (
                        cell is not None
                        and cell.status == "filled"
                        and cell.pass_rate is not None
                    )
                    if filled:
                        ready += 1
                        slots.append(
                            {
                                "harness": harness,
                                "mode": mode,
                                "pass_rate": cell.pass_rate,
                                "n_seeds": cell.n_seeds,
                                "source": cell.source,
                            }
                        )
                    else:
                        bench_ok = False
                        missing.append(f"{bench}|{harness}|{mode}|k={kk}")
            benches[bench] = {"ready": bench_ok, "slots": slots}
        return {
            "k": kk,
            "paper_core": "base+metamorph × agg/seq × wb/jb/lab",
            "ready_slots": ready,
            "total_slots": total,
            "pct": round(100.0 * ready / total, 1) if total else 0.0,
            "benches_ready": sum(1 for b in benches.values() if b["ready"]),
            "benches_total": 3,
            "complete": ready == total,
            "missing": missing,
            "benches": benches,
            "lifts": [L for L in self.lift_rows() if L["bench"] in ("wb", "jb", "lab") and L["k"] == kk],
        }

    def payload(self) -> dict[str, Any]:
        rows = [c.to_dict() for c in self._cells.values()]
        rows.sort(key=lambda r: (r["bench"], r["harness"], r["mode"], r["k"]))
        pending = sum(1 for r in rows if r["status"] == "pending")
        filled = sum(1 for r in rows if r["status"] == "filled")
        lifts = self.lift_rows()
        return {
            "protocol": "Harness2 §3 main table (WB/JB/LAB) + offline demo",
            "benches": list(BENCHES),
            "harnesses": list(HARNESSES),
            "modes": list(MODES),
            "cells": rows,
            "pending": pending,
            "filled": filled,
            "lifts": lifts,
            "completeness": self.completeness(1),
        }

    def render_markdown(self, *, filled_only: bool = False) -> str:
        """Paper-style main-table Markdown for docs sync."""
        rows = [c.to_dict() for c in self._cells.values()]
        rows.sort(key=lambda r: (r["bench"], r["harness"], r["mode"], r["k"]))
        if filled_only:
            rows = [r for r in rows if r["status"] == "filled"]
        pending = sum(1 for r in self._cells.values() if r.status == "pending")
        filled = sum(1 for r in self._cells.values() if r.status == "filled")
        lines = [
            "# Paper main table (WB / JB / LAB + demo)",
            "",
            f"Filled **{filled}** · Pending **{pending}**",
            "",
            "| Bench | Harness | Mode | k | Pass% | ±SD | Steps | Seeds | Status | Source |",
            "|-------|---------|------|---|-------|-----|-------|-------|--------|--------|",
        ]
        for r in rows:
            pr = "" if r["pass_rate"] is None else f"{r['pass_rate']:.1f}"
            sd = "" if r["pass_rate_sd"] is None else f"{r['pass_rate_sd']:.1f}"
            steps = "" if r["steps"] is None else f"{r['steps']:.1f}"
            seeds = "" if r["n_seeds"] is None else str(r["n_seeds"])
            lines.append(
                f"| {r['bench']} | {r['harness']} | {r['mode']} | {r['k']} | "
                f"{pr} | {sd} | {steps} | {seeds} | {r['status']} | {r['source']} |"
            )
        lifts = self.lift_rows()
        if lifts:
            lines += [
                "",
                "## Base → Metamorph lift (pp)",
                "",
                "| Bench | Mode | k | Base% | Metamorph% | Δpp |",
                "|-------|------|---|-------|------------|-----|",
            ]
            for L in lifts:
                lines.append(
                    f"| {L['bench']} | {L['mode']} | {L['k']} | "
                    f"{L['base']:.1f} | {L['metamorph']:.1f} | {L['delta_pp']:+.1f} |"
                )
        comp = self.completeness(1)
        lines += [
            "",
            "## Paper completeness (WB/JB/LAB · base+metamorph · k=1)",
            "",
            f"Ready **{comp['ready_slots']}/{comp['total_slots']}** ({comp['pct']}%) · "
            f"benches **{comp['benches_ready']}/3** · "
            f"{'COMPLETE' if comp['complete'] else 'INCOMPLETE'}",
            "",
        ]
        if comp["missing"]:
            lines.append("Missing: `" + "`, `".join(comp["missing"][:12]) + "`")
            if len(comp["missing"]) > 12:
                lines.append(f"… +{len(comp['missing']) - 12} more")
            lines.append("")
        lines += [
            "_Aligned with Harness² §3; LiveLLM cells via `PUT /v1/tables/main`._",
            "",
        ]
        return "\n".join(lines)
