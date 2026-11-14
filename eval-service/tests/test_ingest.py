import pytest

from app.ingest import IngestError, validate_cell


def test_livellm_requires_pass_rate_and_seeds() -> None:
    with pytest.raises(IngestError, match="pass_rate"):
        validate_cell({"bench": "jb", "harness": "metamorph", "source": "livellm", "n_seeds": 3})
    with pytest.raises(IngestError, match="n_seeds"):
        validate_cell(
            {"bench": "jb", "harness": "metamorph", "source": "livellm", "pass_rate": 70.0}
        )


def test_valid_livellm_cell() -> None:
    row = validate_cell(
        {
            "bench": "WB",
            "harness": "metamorph",
            "mode": "agg",
            "k": 1,
            "pass_rate": 61.0,
            "n_seeds": 3,
            "source": "livellm",
        }
    )
    assert row["bench"] == "wb"
    assert row["pass_rate"] == 61.0


def test_rejects_unknown_bench() -> None:
    with pytest.raises(IngestError, match="bench"):
        validate_cell({"bench": "foo", "harness": "base", "pass_rate": 1.0})
