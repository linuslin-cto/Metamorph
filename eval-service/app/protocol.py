"""Paper §2–§3 protocol mapping used by the eval service."""

from __future__ import annotations

PAPER = {
    "title": "Harness²: Recursive Agent Harnessing for an Open World",
    "arxiv": "2610.14224",
    "sections": "§2–§3",
}

MAPPING = [
    {
        "paper": "Propose–Probe–Compose",
        "service": "offline_scorer + ingested LiveLLM trajectories",
    },
    {
        "paper": "Parallel / Sequential recursion",
        "service": "modes agg / seq on main-table cells",
    },
    {
        "paper": "Budget multiplier k; identity R=4k, P=3k",
        "service": "dimension budget_identity on scorecards",
    },
    {
        "paper": "Base harness vs evolved harness",
        "service": "base_fails → self_evolution_lift",
    },
    {
        "paper": "Harnessing-level Library",
        "service": "library_retention dimension",
    },
    {
        "paper": "Judge → Report (mode×k)",
        "service": "judge_report + dual_modes dimensions",
    },
    {
        "paper": "WB / JB / LAB LiveLLM main tables",
        "service": "GET/PUT /v1/tables/main cells (pending until ingest)",
    },
]

ENDPOINTS = [
    "GET /health",
    "GET /v1/protocol",
    "POST /v1/eval/runs",
    "GET /v1/eval/runs",
    "GET /v1/eval/runs/{id}",
    "GET /v1/tables/main",
    "GET /v1/tables/main?format=md",
    "GET /v1/tables/main/completeness",
    "PUT /v1/tables/main",
    "POST /v1/tables/main/export",
    "POST /v1/import/livellm",
    "POST /v1/sync/docs",
    "GET /v1/scorecards/{id}",
]


def protocol_payload() -> dict:
    return {
        "paper": PAPER,
        "mapping": MAPPING,
        "endpoints": ENDPOINTS,
        "budgets": {"R": "4k", "P": "3k"},
        "benches": ["wb", "jb", "lab", "demo"],
        "harnesses": [
            "base",
            "metamorph",
            "reasoning_bank",
            "meta_harness",
            "harness_scaling",
        ],
        "modes": ["agg", "seq"],
    }
