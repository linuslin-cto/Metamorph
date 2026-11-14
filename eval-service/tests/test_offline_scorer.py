from pathlib import Path

from app.offline_scorer import run_offline_demo, validate_ingested_scorecard


def test_offline_demo_passes_at_k1(tmp_path: Path) -> None:
    sc = run_offline_demo(tmp_path / "k1", 1)
    assert sc["ok"] is True
    assert sc["overall"] >= 99.0
    ids = {d["id"] for d in sc["dimensions"]}
    assert "noise_floor_k1" in ids
    assert (tmp_path / "k1" / "eval-scorecard.json").is_file()


def test_offline_demo_passes_at_k2(tmp_path: Path) -> None:
    sc = run_offline_demo(tmp_path / "k2", 2)
    assert sc["ok"] is True
    assert sc["budget"] == {"R": 8, "P": 6}
    ids = {d["id"] for d in sc["dimensions"]}
    assert "modes_clear_at_k" in ids


def test_validate_ingested_scorecard(tmp_path: Path) -> None:
    sc = run_offline_demo(tmp_path / "v", 1)
    assert validate_ingested_scorecard(sc)["ok"] is True
    assert validate_ingested_scorecard({"dimensions": []})["ok"] is False
