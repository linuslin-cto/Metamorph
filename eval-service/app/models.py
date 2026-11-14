from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class Dimension(BaseModel):
    id: str
    title: str
    pass_: bool = Field(alias="pass")
    score: float
    detail: str

    model_config = {"populate_by_name": True}


class Scorecard(BaseModel):
    ok: bool
    overall: float
    dimensions: list[Dimension]
    k: int = 1
    source: str = "offline-demo"


class CellIn(BaseModel):
    bench: str
    harness: str
    mode: str = "agg"
    k: int = 1
    pass_rate: float | None = None
    pass_rate_sd: float | None = None
    steps: float | None = None
    steps_sd: float | None = None
    n_seeds: int | None = None
    status: str = "filled"
    source: str = "ingest"
    meta: dict[str, Any] = Field(default_factory=dict)


class CellsIn(BaseModel):
    cells: list[CellIn] | None = None
    # allow single-cell PUT body without wrapping
    bench: str | None = None
    harness: str | None = None
    mode: str | None = None
    k: int | None = None
    pass_rate: float | None = None
    pass_rate_sd: float | None = None
    steps: float | None = None
    steps_sd: float | None = None
    n_seeds: int | None = None
    status: str | None = None
    source: str | None = None
    meta: dict[str, Any] | None = None


class EvalRunRequest(BaseModel):
    k: int = 1
    sync: bool = True
    output: str | None = None


class JobOut(BaseModel):
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


class LiveLlmImportRequest(BaseModel):
    """Import cells from a file path and/or inline JSON/JSONL text."""

    path: str | None = None
    text: str | None = None
    cells: list[CellIn] | None = None


class DocsSyncRequest(BaseModel):
    """Write MAIN_TABLE.md to a docs path (default: sibling Metamorph/docs)."""

    dest: str | None = None
    filled_only: bool = True


class DsCampaignRequest(BaseModel):
    target: int = 100
    k: int = 1
    name: str = "ds-eval100"
