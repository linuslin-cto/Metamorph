from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.ingest import IngestError
from app.livellm_import import load_path, parse_payload
from app.main import STATE, app


FIXTURE = Path(__file__).parent / "fixtures" / "livellm_sample.jsonl"


def test_parse_jsonl_maps_score_to_pass_rate() -> None:
    rows = load_path(FIXTURE)
    assert len(rows) == 4
    assert rows[0]["pass_rate"] == 41.0
    assert rows[0]["source"] == "livellm"


def test_parse_json_array() -> None:
    rows = parse_payload(
        '[{"bench":"wb","harness":"base","mode":"agg","pass_rate":10,"n_seeds":3}]'
    )
    assert rows[0]["bench"] == "wb"


def test_parse_rejects_bad_line() -> None:
    with pytest.raises(IngestError, match="JSONL line 1"):
        parse_payload("not-json\n")


def test_resolve_relative_fixture_name() -> None:
    from app.livellm_import import resolve_import_path

    p = resolve_import_path("livellm_sample.jsonl")
    assert p.name == "livellm_sample.jsonl"
    assert p.is_file()


def test_aggregate_three_seeds_mean_sd() -> None:
    seed_file = Path(__file__).parent / "fixtures" / "livellm_seeds.jsonl"
    rows = load_path(seed_file)
    assert len(rows) == 4  # base/metamorph × agg/seq
    agg_base = next(r for r in rows if r["harness"] == "base" and r["mode"] == "agg")
    assert agg_base["n_seeds"] == 3
    assert abs(agg_base["pass_rate"] - 50.0) < 1e-6
    assert agg_base["pass_rate_sd"] > 0
    agg_m = next(r for r in rows if r["harness"] == "metamorph" and r["mode"] == "agg")
    assert abs(agg_m["pass_rate"] - 62.0) < 1e-6
    assert agg_m["meta"]["seeds_aggregated"] == 3


@pytest.fixture()
def client(tmp_path: Path) -> TestClient:
    STATE.work_root = tmp_path
    STATE.jobs.clear()
    STATE.scorecards.clear()
    STATE.table._cells.clear()
    STATE.table.seed_paper_placeholders(1)
    return TestClient(app)


def test_import_and_sync_docs(client: TestClient, tmp_path: Path) -> None:
    r = client.post("/v1/import/livellm", json={"path": str(FIXTURE)})
    assert r.status_code == 200
    assert r.json()["upserted"] == 4
    lifts = r.json()["table"]["lifts"]
    assert any(L["bench"] == "jb" and L["delta_pp"] > 0 for L in lifts)

    dest = tmp_path / "docs" / "MAIN_TABLE.md"
    s = client.post("/v1/sync/docs", json={"dest": str(dest), "filled_only": True})
    assert s.status_code == 200
    assert dest.is_file()
    text = dest.read_text(encoding="utf-8")
    assert "jb" in text
    assert "Base → Metamorph lift" in text
