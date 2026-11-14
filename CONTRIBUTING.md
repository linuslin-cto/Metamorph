# Contributing to Metamorph

[English](#english) | [简体中文](#简体中文)

## English

Thanks for contributing. Please:

1. Run `mvn test` before opening a PR.
2. Keep user-facing docs bilingual: update both `README.md` and `README.zh-CN.md` when CLI or eval semantics change.
3. Follow [docs/i18n.md](docs/i18n.md) for translation rules (identifiers stay English).
4. If scorecard dimensions change, regenerate:

```bash
mvn -q exec:java -Dexec.mainClass=com.metamorph.cli.MetamorphMain \
  -Dexec.args="eval --output runs/eval --k 1 --report docs/EVAL.md --json docs/eval-scorecard.json"
```

5. Prefer small, focused PRs with a clear “why”.
6. If you change [`eval-service/`](eval-service/), run `cd eval-service && pip install -e ".[dev]" && pytest -q` (independent of `mvn test`).

## 简体中文

感谢贡献。请：

1. 提交 PR 前运行 `mvn test`。
2. 变更面向用户的 CLI / 评测语义时，同步更新 `README.md` 与 `README.zh-CN.md`。
3. 翻译约定见 [docs/i18n.md](docs/i18n.md)（标识符保持英文）。
4. 若 scorecard 维度变化，请按上方命令重新生成 `docs/EVAL.md`。
5. 优先小而清晰的 PR，说明「为什么改」。
6. 若改动 [`eval-service/`](eval-service/)，额外运行 `cd eval-service && pip install -e ".[dev]" && pytest -q`（与 `mvn test` 独立）。
