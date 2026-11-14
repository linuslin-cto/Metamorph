from app.main_table import Cell, MainTable


def test_seed_and_ingest_live_cell() -> None:
    table = MainTable()
    payload = table.payload()
    assert payload["pending"] >= 30
    assert payload["filled"] == 0

    table.put(
        Cell(
            bench="jb",
            harness="metamorph",
            mode="agg",
            k=1,
            pass_rate=72.5,
            pass_rate_sd=1.2,
            steps=14.0,
            n_seeds=3,
            status="filled",
            source="livellm",
        )
    )
    cell = table.get("jb", "metamorph", "agg", 1)
    assert cell is not None
    assert cell.pass_rate == 72.5
    assert table.payload()["filled"] >= 1


def test_ingest_demo_scorecard() -> None:
    table = MainTable()
    table.ingest_demo_scorecard({"ok": True, "overall": 100.0, "dimensions": [{}, {}]}, 1)
    assert table.get("demo", "metamorph", "agg", 1).status == "filled"
    assert table.get("demo", "base", "seq", 1).pass_rate == 0.0
    lifts = table.lift_rows()
    assert lifts
    assert all(L["delta_pp"] == 100.0 for L in lifts if L["bench"] == "demo")
    md = table.render_markdown(filled_only=True)
    assert "Base → Metamorph lift" in md
    assert "Paper completeness" in md


def test_completeness_tracks_paper_core_slots() -> None:
    table = MainTable()
    c0 = table.completeness(1)
    assert c0["complete"] is False
    assert c0["ready_slots"] == 0
    assert c0["total_slots"] == 12
    for mode in ("agg", "seq"):
        table.put(
            Cell(
                bench="jb",
                harness="base",
                mode=mode,
                k=1,
                pass_rate=40.0,
                n_seeds=3,
                status="filled",
                source="livellm",
            )
        )
        table.put(
            Cell(
                bench="jb",
                harness="metamorph",
                mode=mode,
                k=1,
                pass_rate=70.0,
                n_seeds=3,
                status="filled",
                source="livellm",
            )
        )
    c1 = table.completeness(1)
    assert c1["benches"]["jb"]["ready"] is True
    assert c1["ready_slots"] == 4
    assert c1["benches_ready"] == 1
