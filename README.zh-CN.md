# Metamorph

[![Java](https://img.shields.io/badge/Java-21+-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Eval](https://img.shields.io/badge/self--evolution%20eval-PASS%20%2F%20100-brightgreen)](docs/EVAL.md)
[![GitHub stars](https://img.shields.io/github/stars/linuslin-cto/Metamorph)](https://github.com/linuslin-cto/Metamorph)

[English](README.md) | **简体中文**

> 面向开放世界的递归式 Agent Harnessing —— 改进的是**模型周围的系统**，而不是权重。

**Harness** 是把模型变成 Agent 的一切：提示词、工具、记忆、技能、脚本与编排。  
**Metamorph** 把 harness 当作可进化对象：在权重固定的前提下，按预算运行 **Propose → Probe → Compose**，把可迁移经验写入领域 **Library**，再经 **Judge → Report** 打分汇总。

| | |
|---|---|
| 坐标 | `com.metamorph:metamorph` |
| 仓库 | https://github.com/linuslin-cto/Metamorph |
| 离线 Demo | 无需 API Key |
| 配置前缀 | `METAMORPH_*` |

---

## 为什么需要 Metamorph？

多数 Agent 栈是：固定 prompt + 工具面，评一次分。  
Metamorph 追问的是：*执行环境能否跨任务自我改进？*

1. **Propose** — 在改进预算 **P** 内编辑 harness  
2. **Probe** — 在 rollout 预算 **R** 内执行候选  
3. **Compose** — 接受或回退改动（`acceptTree`）  
4. **Library** — 保留有证据支撑的经验，供后续任务使用  
5. **Judge / Report** — 将 selection 汇总为 mode×k 图  

**预算身份：** `R = 4k` rollout，`P = 3k` 改进阶段（每种 mode）。  
**模式：** `agg`（并行）与 `seq`（顺序）。

离线契约见 [`docs/EVAL.md`](docs/EVAL.md) —— 当前 **PASS / 100**。

---

## 特性

- **自进化 harness 循环** — Propose–Probe–Compose + 显式 R/P 账本  
- **双递归模式** — 并行探索 vs 顺序精炼  
- **经验库** — 同领域跨任务保留  
- **Offline-first Demo** — 确定性 improver + 真实 Python 脚本执行  
- **评测 CLI** — 可重生成 Markdown/JSON scorecard（`metamorph eval`）  
- **执行表面** — OpenCode / Codex 树规格与轨迹渲染  
- **基准骨架** — LAB / JobBench / WorkBuddy 离线契约  
- **国际化文档** — 英文 + 简体中文（[i18n 约定](docs/i18n.md)）  

---

## 目录

- [为什么需要 Metamorph？](#为什么需要-metamorph)
- [特性](#特性)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [命令行](#命令行)
- [架构](#架构)
- [评测与打分](#评测与打分)
- [基准适配](#基准适配)
- [配置](#配置)
- [源码结构](#源码结构)
- [文档](#文档)
- [贡献](#贡献)
- [许可](#许可)
- [引用](#引用)

---

## 环境要求

- **JDK 21+**
- **Maven 3.9+**
- **Python 3**（仅 Demo 脚本执行器）

---

## 快速开始

```bash
git clone https://github.com/linuslin-cto/Metamorph.git
cd Metamorph

# 测试（含自进化 scorecard）
mvn test

# 预检
mvn -q exec:java -Dexec.mainClass=com.metamorph.cli.MetamorphMain \
  -Dexec.args="check --bench demo"

# 离线 Propose–Probe–Compose
mvn -q exec:java -Dexec.mainClass=com.metamorph.cli.MetamorphMain \
  -Dexec.args="demo --output runs/demo --k 2"

# 重新生成评测报告
mvn -q exec:java -Dexec.mainClass=com.metamorph.cli.MetamorphMain \
  -Dexec.args="eval --output runs/eval --k 1 --report docs/EVAL.md --json docs/eval-scorecard.json"
```

> `k=1` 时 parallel 与 sequential 过程相同，分差属于**噪声地板**，不是效应；`k≥2` 的效应必须越过该地板。

---

## 命令行

入口：`com.metamorph.cli.MetamorphMain`

```bash
mvn -q exec:java -Dexec.mainClass=com.metamorph.cli.MetamorphMain \
  -Dexec.args="<子命令> [选项...]"
```

| 子命令 | 说明 |
|--------|------|
| `demo --output <dir> [--k N]` | 离线 Propose–Probe–Compose（可接 judge/report） |
| `eval [--output dir] [--k N]` | 自进化 scorecard → Markdown/JSON |
| `check [--bench demo\|lab\|jb\|cx\|wb]` | 预检 |
| `judge --ns-dir <path>` | selection → `judged.json` |
| `report --ns-dir <path>` | 打印 mode×k 汇总 |
| `bridge ...` | 写 Codex bridge 文件（不启 LiteLLM） |
| `setup <jb\|lab\|wb>` | 安装计划 dry-run |

---

## 架构

```
Propose（改 harness） → Probe（rollout） → Compose（acceptTree）
         ↑                                         │
         └──────── Library（跨任务经验） ←──────────┘
                              │
                              ▼
                    Selection → Judge → Report
```

| 层 | 包（示意） | 作用 |
|----|------------|------|
| Recursion | `core.recursion` | Propose–Probe–Compose |
| Schedule | `core.schedule` | 域调度、namespace、账本 |
| Library | `core.library` | 经验保留 |
| Selection | `core.selection` | 终选 |
| Judge / Report / Eval | `core.judge` 等 | 打分流水线 |
| Tree / Context | `core.tree` / `core.context` | harness I/O 与防泄漏 |
| Demo | `demo` | 离线确定性单元 |
| Substrates | `substrate` | OpenCode / Codex |
| Benchmarks | `benchmarks.*` | LAB / JB / WB |

可编辑表面覆盖八类组件：system prompt、guardrails、memory、tool guidance、subagents、skills、scripts、plugins/hooks。

---

## 评测与打分

离线 scorecard 维度（对齐论文式图流水线）：

| 维度 | 含义 |
|------|------|
| `base_fails` | 基座 harness 不及格（存在进化空间） |
| `sweep_complete` | 域 sweep 无失败 |
| `budget_identity` | 账本报告 `R=4k`、`P=3k` |
| `evolved_passes` | 进化后 selection 通过 |
| `self_evolution_lift` | 基座失败 → 进化成功 |
| `library_retention` | Library 有跨任务材料 |
| `judge_report` | Judge→Report 均分 ≥ 99% |
| `dual_modes` | agg / seq 均有 graded 行 |
| `noise_floor_k1` / `modes_clear_at_k` | k=1 噪声地板 / k≥2 清障 |

最新入库报告：**[`docs/EVAL.md`](docs/EVAL.md)** · JSON：[`docs/eval-scorecard.json`](docs/eval-scorecard.json)

```bash
mvn -q test -Dtest=SelfEvolutionTest,EvalScorecardTest
```

---

## 基准适配

| 适配器 | 离线状态 | 说明 |
|--------|----------|------|
| **Demo** | ✅ 完整 | 确定性 improver；自进化证明 |
| **LAB** | 骨架 | 嵌套 deliverable；execute 需上游 pin |
| **JobBench** | 骨架 | 隔离、staging、退出注记、rubric 隔离 |
| **WorkBuddy** | 骨架 | 交付阈值、verifier 优先级、Jinja 校验 |

默认路径不拉起 live executor。完整安装依赖见 `setup --dry-run`。

---

## 配置

环境变量统一前缀 **`METAMORPH_`**（shell 覆盖 `.env`）。

| 变量 | 用途 |
|------|------|
| `METAMORPH_IMPROVER_MODEL` | Improver 模型 |
| `METAMORPH_SOLVER_MODEL` | Solver 回退 |
| `METAMORPH_CODEX_BASE_URL` | Codex Responses 基址 |
| `METAMORPH_CODEX_API_KEY` | Codex 鉴权 |
| `METAMORPH_CODEX_CONTAINER_URL` | 容器可达 bridge |
| `METAMORPH_RUNS_DIR` | runs 根目录覆盖 |
| `METAMORPH_PROPOSER_CONCURRENCY` | proposer 并发 |

`Config.SCRUB_ENV` 中的密钥不会进入子进程环境。

---

## 源码结构

```
Metamorph/
├── README.md / README.zh-CN.md   # 多语言入口
├── CONTRIBUTING.md               # PR 清单（中英）
├── LICENSE
├── docs/
│   ├── i18n.md                   # 语言约定
│   ├── EVAL.md                   # 最新打分
│   └── eval-scorecard.json
└── src/main/java/com/metamorph/
    ├── cli/
    ├── core/
    ├── demo/
    ├── benchmarks/
    ├── substrate/
    └── install/
```

---

## 文档

| 文档 | 说明 |
|------|------|
| [README (EN)](README.md) | English |
| [README (zh-CN)](README.zh-CN.md) | 本文 |
| [国际化约定](docs/i18n.md) | 语言文件规范 |
| [评测打分](docs/EVAL.md) | 自进化 PASS/FAIL |
| [贡献指南](CONTRIBUTING.md) | PR 清单（中英） |

---

## 贡献

欢迎 Issue / PR。完整清单见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。简要：

1. PR 前运行 `mvn test`
2. 变更面向用户的 CLI 或评测语义时，同步更新 `README.md` 与 `README.zh-CN.md`
3. 若 scorecard 维度变化，重新生成 `docs/EVAL.md`

翻译规则见 [docs/i18n.md](docs/i18n.md)。

---

## 许可

Apache License 2.0 — 见 [`LICENSE`](LICENSE)。  
第三方基准与 pin（若存在）保留各自上游许可。

---

## 引用

Metamorph 实现 Propose–Probe–Compose 递归 harnessing 方法。若用于研究，请同时引用方法论文：

```bibtex
@article{xu2026harness2,
  title   = {Harness$^2$: Recursive Agent Harnessing for an Open World},
  author  = {Xu, Ruiyao and Chen, Yanfei and CuiZhu, Zhongying and Dalvi Mishra, Bhavana and Ming, Yifei and Yu, Han and Han, Rujun and Lee, Chen-Yu and Pfister, Tomas},
  journal = {arXiv preprint arXiv:2610.14224},
  year    = {2026},
  url     = {https://arxiv.org/abs/2610.14224}
}
```

---

<p align="center">
  <b>Metamorph</b> — 进化 harness，固定模型。
</p>
