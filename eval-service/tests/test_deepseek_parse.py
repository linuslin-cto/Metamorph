from app.deepseek import _parse_json_object


def test_parse_json_object_direct() -> None:
    assert _parse_json_object('{"ok": true, "score": 100}')["score"] == 100


def test_parse_json_object_fenced() -> None:
    text = 'Here you go:\n```json\n{"ok": true, "score": 90}\n```\n'
    assert _parse_json_object(text)["ok"] is True
