"""DeepSeek OpenAI-compatible chat client for framework evaluation."""

from __future__ import annotations

import json
import os
import re
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


def load_dotenv(path: Path) -> None:
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        k, v = k.strip(), v.strip().strip('"').strip("'")
        if k and k not in os.environ:
            os.environ[k] = v


def bootstrap_env() -> None:
    """Load keys from common .env locations without overriding shell."""
    here = Path(__file__).resolve()
    candidates = [
        here.parents[1] / ".env",
        here.parents[2] / ".env",
        Path.cwd() / ".env",
    ]
    for p in candidates:
        load_dotenv(p)


def chat_json(
    *,
    system: str,
    user: str,
    model: str | None = None,
    timeout: float = 120.0,
) -> dict[str, Any]:
    bootstrap_env()
    api_key = os.environ.get("DEEPSEEK_API_KEY") or os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("DEEPSEEK_API_KEY / OPENAI_API_KEY missing")
    base = (os.environ.get("OPENAI_BASE_URL") or "https://api.deepseek.com").rstrip("/")
    if base.endswith("/v1"):
        url = f"{base}/chat/completions"
    else:
        url = f"{base}/v1/chat/completions"
    model = model or os.environ.get("METAMORPH_IMPROVER_MODEL") or "deepseek-chat"
    payload = {
        "model": model,
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "response_format": {"type": "json_object"},
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"DeepSeek HTTP {exc.code}: {detail[:500]}") from exc
    text = body["choices"][0]["message"]["content"]
    return {"raw": body, "parsed": _parse_json_object(text), "model": model, "text": text}


def _parse_json_object(text: str) -> dict[str, Any]:
    text = (text or "").strip()
    try:
        data = json.loads(text)
        if isinstance(data, dict):
            return data
    except json.JSONDecodeError:
        pass
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        raise RuntimeError(f"DeepSeek did not return JSON object: {text[:300]}")
    data = json.loads(m.group(0))
    if not isinstance(data, dict):
        raise RuntimeError("DeepSeek JSON root is not an object")
    return data


def judge_framework_scorecard(scorecard: dict[str, Any], *, round_id: int) -> dict[str, Any]:
    """Ask DeepSeek whether the Metamorph offline scorecard matches paper §2–§3."""
    system = (
        "You are an evaluation judge for Metamorph / Harness² paper alignment. "
        "Reply ONLY with a JSON object."
    )
    user = (
        f"Round {round_id}. Scorecard from Metamorph offline Demo eval service:\n"
        f"{json.dumps(scorecard, ensure_ascii=False, indent=2)}\n\n"
        "Paper contract: Propose–Probe–Compose; modes agg/seq; budget R=4k P=3k; "
        "base harness fails then evolved passes; Library retention; Judge→Report; "
        "k=1 noise floor between agg/seq.\n\n"
        "Return JSON keys:\n"
        '- "ok": boolean — true if scorecard satisfies the paper method contract\n'
        '- "score": number 0-100 — alignment quality\n'
        '- "paper_aligned": boolean\n'
        '- "issues": string[] — short issues if any\n'
        '- "summary": string — one sentence\n'
    )
    result = chat_json(system=system, user=user)
    parsed = result["parsed"]
    ok = bool(parsed.get("ok") or parsed.get("paper_aligned"))
    try:
        score = float(parsed.get("score", 0))
    except (TypeError, ValueError):
        score = 0.0
    return {
        "ok": ok,
        "score": score,
        "paper_aligned": bool(parsed.get("paper_aligned", ok)),
        "issues": parsed.get("issues") if isinstance(parsed.get("issues"), list) else [],
        "summary": str(parsed.get("summary") or ""),
        "model": result["model"],
        "raw_text": result["text"],
    }
