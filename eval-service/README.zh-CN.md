# Metamorph 评测服务（独立 Python）

对齐 [Harness²](https://arxiv.org/abs/2610.14224) §2–§3。与 Java Metamorph CLI **进程 / 依赖 / runs 目录分离**；代码位于本仓库 `eval-service/`。

## 能力

- 离线 Demo 自进化打分（PPC、R=4k/P=3k、双模式、Library、Judge→Report）
- 论文主表 WB/JB/LAB：`GET/PUT /v1/tables/main`、完备度 `GET /v1/tables/main/completeness`
- LiveLLM JSON/JSONL 导入（含按 `seed` 聚合 mean±SD）
- 同步文档：`POST /v1/sync/docs` → `docs/MAIN_TABLE.md`

## 启动

```bash
cd eval-service
python -m venv .venv
.venv\Scripts\activate
pip install -e ".[dev]"
pytest -q
uvicorn app.main:app --host 127.0.0.1 --port 8787
```

## CLI 同步主表

```bash
python scripts/sync_main_table.py --demo --import tests/fixtures/livellm_sample.jsonl
```

## CI

仓库工作流 `.github/workflows/eval-service.yml`：pytest + 三基准完备度冒烟（样例数据）。
