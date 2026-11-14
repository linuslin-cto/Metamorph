from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.main import STATE, app


@pytest.fixture()
def client(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> TestClient:
    STATE.work_root = tmp_path
    STATE.jobs.clear()
    STATE.scorecards.clear()
    STATE.table._cells.clear()
    STATE.table.seed_paper_placeholders(1)
    return TestClient(app)


def test_health_and_protocol(client: TestClient) -> None:
    h = client.get("/health")
    assert h.status_code == 200
    assert h.json()["ok"] is True
    assert h.json()["service"] == "metamorph-eval"

    p = client.get("/v1/protocol")
    assert p.status_code == 200
    assert "mapping" in p.json()
    assert p.json()["budgets"] == {"R": "4k", "P": "3k"}


def test_sync_eval_run_and_scorecard(client: TestClient) -> None:
    r = client.post("/v1/eval/runs", json={"k": 1, "sync": True})
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "done"
    assert body["ok"] is True
    assert body["overall"] >= 99.0

    sc = client.get(f"/v1/scorecards/{body['id']}")
    assert sc.status_code == 200
    assert sc.json()["ok"] is True

    table = client.get("/v1/tables/main").json()
    assert table["filled"] >= 4  # demo base+metamorph × agg/seq


def test_put_main_table_cell(client: TestClient) -> None:
    r = client.put(
        "/v1/tables/main",
        json={
            "bench": "wb",
            "harness": "metamorph",
            "mode": "agg",
            "k": 1,
            "pass_rate": 61.0,
            "n_seeds": 3,
            "source": "livellm",
        },
    )
    assert r.status_code == 200
    assert r.json()["upserted"] == 1
    cells = r.json()["table"]["cells"]
    hit = [c for c in cells if c["bench"] == "wb" and c["harness"] == "metamorph" and c["mode"] == "agg"]
    assert hit and hit[0]["pass_rate"] == 61.0


def test_put_rejects_invalid_livellm(client: TestClient) -> None:
    r = client.put(
        "/v1/tables/main",
        json={"bench": "jb", "harness": "metamorph", "source": "livellm", "pass_rate": 50.0},
    )
    assert r.status_code == 400


def test_main_table_markdown(client: TestClient) -> None:
    client.put(
        "/v1/tables/main",
        json={
            "bench": "lab",
            "harness": "base",
            "mode": "seq",
            "pass_rate": 40.0,
            "n_seeds": 3,
            "source": "livellm",
        },
    )
    r = client.get("/v1/tables/main", params={"format": "md", "filled_only": True})
    assert r.status_code == 200
    assert "text/markdown" in r.headers.get("content-type", "")
    assert "| Bench |" in r.text
    assert "lab" in r.text


def test_export_main_table_file(client: TestClient, tmp_path: Path) -> None:
    client.post("/v1/eval/runs", json={"k": 1, "sync": True})
    r = client.post("/v1/tables/main/export", params={"filled_only": True})
    assert r.status_code == 200
    body = r.json()
    assert body["ok"] is True
    assert Path(body["path"]).is_file()
    assert body["lifts"]
    assert "demo" in Path(body["path"]).read_text(encoding="utf-8")
