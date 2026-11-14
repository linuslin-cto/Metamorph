# Metamorph

[![Java](https://img.shields.io/badge/Java-21+-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Eval](https://img.shields.io/badge/self--evolution%20eval-PASS%20%2F%20100-brightgreen)](docs/EVAL.md)
[![H2 campaign](https://img.shields.io/badge/H2%20aligned%20100--round-99%2F100-brightgreen)](docs/EVAL.md#harness2-对齐--100-轮自进化战役)
[![GitHub stars](https://img.shields.io/github/stars/linuslin-cto/Metamorph)](https://github.com/linuslin-cto/Metamorph)

**English** | [简体中文](README.zh-CN.md)

> Recursive agent harnessing for an open world — improve the **system around the model**, not the weights.

A **harness** is everything that turns a model into an agent: prompts, tools, memory, skills, scripts, and orchestration.  
**Metamorph** treats that harness as an evolvable artifact. With model weights fixed, it runs a budgeted **Propose → Probe → Compose** loop, keeps transferable lessons in a domain **Library**, and scores results through **Judge → Report**.

| | |
|---|---|
| Coordinates | `com.metamorph:metamorph` |
| Repository | https://github.com/linuslin-cto/Metamorph |
| Offline demo | No API keys required |
| Config prefix | `METAMORPH_*` |

---

## Why Metamorph?

Most agent stacks ship a static prompt + tool surface and score once.  
Metamorph instead asks: *can the execution environment improve itself across tasks?*

1. **Propose** — edit the harness under improvement budget **P**
2. **Probe** — execute candidates under rollout budget **R**
3. **Compose** — accept or reject edits (`acceptTree`)
4. **Library** — retain evidence-backed lessons for later tasks
5. **Judge / Report** — grade selections into mode×k figures

**Budgets (paper identity):** `R = 4k` rollouts, `P = 3k` improvement stages per mode.  
**Modes:** `agg` (parallel) and `seq` (sequential).

Offline contract: [`docs/EVAL.md`](docs/EVAL.md) — single-run **PASS / 100**; H2-aligned 100-round campaign **99 / 100**.

---

## Features

- **Self-evolving harness loop** — Propose–Probe–Compose with explicit R/P ledgers
- **Dual recursion modes** — parallel exploration vs sequential refinement
- **Experience library** — cross-task retention inside a domain
- **Offline-first Demo** — deterministic improver + real Python script execution
- **Eval scorecard CLI** — regenerable Markdown/JSON scorecard (`metamorph eval`)
- **Substrate surfaces** — OpenCode / Codex tree specs and trajectory renderers
- **Benchmark skeletons** — LAB / JobBench / WorkBuddy adapters (offline contracts)
- **International docs** — English + Simplified Chinese READMEs ([i18n guide](docs/i18n.md))

---

## Contents

- [Why Metamorph?](#why-metamorph)
- [Features](#features)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [CLI](#cli)
- [Architecture](#architecture)
- [Evaluation](#evaluation)
- [Benchmarks](#benchmarks)
- [Configuration](#configuration)
- [Project layout](#project-layout)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)
- [Citation](#citation)

---

## Requirements

- **JDK 21+**
- **Maven 3.9+**
- **Python 3** (Demo script executor only)

---

## Quick start

```bash
git clone https://github.com/linuslin-cto/Metamorph.git
cd Metamorph

# Tests (includes self-evolution scorecard)
mvn test

# Preflight
mvn -q exec:java -Dexec.mainClass=com.metamorph.cli.MetamorphMain \
  -Dexec.args="check --bench demo"

# Offline Propose–Probe–Compose demo
mvn -q exec:java -Dexec.mainClass=com.metamorph.cli.MetamorphMain \
  -Dexec.args="demo --output runs/demo --k 2"

# Regenerate evaluation scorecard
mvn -q exec:java -Dexec.mainClass=com.metamorph.cli.MetamorphMain \
  -Dexec.args="eval --output runs/eval --k 1 --report docs/EVAL.md --json docs/eval-scorecard.json"
```

> At `k=1`, parallel and sequential follow the same procedure — any score gap is a **noise floor**, not an effect. Effects at `k≥2` must clear that floor.

---

## CLI

Entry point: `com.metamorph.cli.MetamorphMain`

```bash
mvn -q exec:java -Dexec.mainClass=com.metamorph.cli.MetamorphMain \
  -Dexec.args="<command> [options...]"
```

| Command | Description |
|---------|-------------|
| `demo --output <dir> [--k N]` | Offline Propose–Probe–Compose (+ optional judge/report) |
| `eval [--output dir] [--k N]` | Self-evolution scorecard → Markdown/JSON |
| `check [--bench demo\|lab\|jb\|cx\|wb]` | Preflight for a bench / Codex surface |
| `judge --ns-dir <path>` | Score selections → `judged.json` |
| `report --ns-dir <path>` | Print mode×k summaries |
| `bridge ...` | Write Codex bridge files (does not start LiteLLM) |
| `setup <jb\|lab\|wb>` | Dry-run install plan for pinned upstreams |

---

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Propose   │────▶│    Probe    │────▶│   Compose   │
│ edit harness│     │  rollouts   │     │ acceptTree  │
└─────────────┘     └─────────────┘     └──────┬──────┘
       ▲                                       │
       │         Library (cross-task)          │
       └───────────────────────────────────────┘
                         │
                         ▼
               Selection → Judge → Report
```

| Layer | Package (sketch) | Role |
|-------|------------------|------|
| Recursion | `core.recursion` | Propose–Probe–Compose stages |
| Schedule | `core.schedule` | Domain sweep, namespace, ledgers |
| Library | `core.library` | Experience retention |
| Selection | `core.selection` | Choose anonymized finals |
| Judge / Report / Eval | `core.judge`, `core.report`, `core.eval` | Scoring pipeline |
| Tree / Context | `core.tree`, `core.context` | Harness I/O + leak tripwires |
| Demo | `demo` | Deterministic offline cell |
| Substrates | `substrate` | OpenCode / Codex surfaces |
| Benchmarks | `benchmarks.*` | LAB / JB / WB adapters |

Editable harness components follow the eight-surface model: system prompt, guardrails, memory, tool guidance, subagents, skills, scripts, and plugins/hooks.

---

## Evaluation

Metamorph ships an offline scorecard aligned with the paper-style figure pipeline:

| Dimension | Meaning |
|-----------|---------|
| `base_fails` | Stock harness fails the criterion (room to evolve) |
| `sweep_complete` | Domain sweep finishes without failures |
| `budget_identity` | Ledgers report `R=4k`, `P=3k` |
| `evolved_passes` | Selected runs pass after evolution |
| `self_evolution_lift` | Base fail → evolved pass |
| `library_retention` | Library keeps cross-task material |
| `judge_report` | Judge→Report mean ≥ 99% |
| `dual_modes` | Both `agg` and `seq` graded |
| `noise_floor_k1` / `modes_clear_at_k` | k=1 floor vs k≥2 clear-bar |

Latest checked-in report: **[`docs/EVAL.md`](docs/EVAL.md)** · machine-readable: [`docs/eval-scorecard.json`](docs/eval-scorecard.json)

**Harness2-aligned 100-round campaign** (same demo ledgers + `R=4k`/`P=3k` + agg/seq): **99 / 100** pass · charts in [`docs/EVAL.md`](docs/EVAL.md#harness2-对齐--100-轮自进化战役)

```bash
mvn -q test -Dtest=SelfEvolutionTest,EvalScorecardTest
```

---

## Benchmarks

| Adapter | Offline status | Notes |
|---------|----------------|-------|
| **Demo** | ✅ Full | Deterministic improver; self-evolution proof |
| **LAB** | Skeleton | Nested deliverables; execute needs upstream pin |
| **JobBench** | Skeleton | Isolation, staging, exit notes, rubric quarantine |
| **WorkBuddy** | Skeleton | Body threshold, verifier score precedence, Jinja check |

Live executors (bun / containers / pinned judges) are intentionally out of the default offline path. Use `setup --dry-run` to inspect what a full install would require.

---

## Configuration

Environment variables use the **`METAMORPH_`** prefix (shell overrides `.env`).

| Variable | Purpose |
|----------|---------|
| `METAMORPH_IMPROVER_MODEL` | Improver model id |
| `METAMORPH_SOLVER_MODEL` | Solver fallback |
| `METAMORPH_CODEX_BASE_URL` | Codex Responses base URL |
| `METAMORPH_CODEX_API_KEY` | Codex auth |
| `METAMORPH_CODEX_CONTAINER_URL` | Container-reachable bridge |
| `METAMORPH_RUNS_DIR` | Override runs root |
| `METAMORPH_PROPOSER_CONCURRENCY` | Proposer concurrency |

Secrets listed in `Config.SCRUB_ENV` are stripped from child process environments.

---

## Project layout

```
Metamorph/
├── README.md / README.zh-CN.md   # i18n entry docs
├── CONTRIBUTING.md               # PR checklist (EN + zh-CN)
├── LICENSE
├── docs/
│   ├── i18n.md                   # language policy
│   ├── EVAL.md                   # latest scorecard + H2 100-round charts
│   └── eval-scorecard.json
└── src/main/java/com/metamorph/
    ├── cli/                      # metamorph subcommands
    ├── core/                     # method core
    ├── demo/                     # offline demo
    ├── benchmarks/               # lab / jb / wb
    ├── substrate/                # OpenCode / Codex
    └── install/                  # third_party checklist
```

---

## Documentation

| Doc | Description |
|-----|-------------|
| [README (EN)](README.md) | This file |
| [README (zh-CN)](README.zh-CN.md) | 简体中文 |
| [Internationalization](docs/i18n.md) | Language file conventions |
| [Evaluation scorecard](docs/EVAL.md) | Self-evolution PASS/FAIL + H2 100-round charts |
| [Contributing](CONTRIBUTING.md) | PR checklist (EN + zh-CN) |

---

## Contributing

Issues and PRs are welcome. See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the full checklist. In short:

1. Run `mvn test` before opening a PR
2. Keep docs bilingual when changing user-facing CLI or eval semantics (`README.md` + `README.zh-CN.md`)
3. Regenerate `docs/EVAL.md` if scorecard dimensions change

See [docs/i18n.md](docs/i18n.md) for translation rules.

---

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).  
Third-party benchmarks and pins, when present, retain their upstream licenses.

---

## Citation

Metamorph implements the Propose–Probe–Compose recursive harnessing method. If you use this work in research, please also cite the method paper:

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
  <b>Metamorph</b> — evolve the harness, keep the model fixed.
</p>
