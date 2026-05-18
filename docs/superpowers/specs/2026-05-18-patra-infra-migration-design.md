# 设计：将 patra-infra 合并到 patra-api/infra/

- **状态**：Draft（等用户审阅）
- **日期**：2026-05-18
- **作者**：linqibin（与 Claude Code 协作）
- **范围**：`patra-api` 仓库新增 `infra/` 顶层目录；原 `patra-infra` 仓库归档
- **前提**：单人 greenfield 项目；`patra-infra` 的 Mac mini 化改造已完成并合入 main（HEAD: `d7f8599`）

---

## 1. 背景

`patra-infra` 是一个独立 Git 仓库（`linqibin0826/patra-infra`），存放 Patra 平台的 Docker Compose 部署配置。最初设想是"代码与基础设施物理分离"，但实际演进出现两个事实：

1. **真实内容只有一个目录**：经过 14 次 commit 的演进，仓库顶层 `k8s/` 是空目录、`README.md` 一句话、`scripts/` 直到最近才新增一个 `init-macmini.sh`。**有效内容 = `docker/` 一个目录 + 一个初始化脚本**，仓库框架成本高于内容收益。
2. **与 patra-api 形成事实耦合**：`patra-api/gradle.properties:51` 用相对路径 `../patra-infra/docker/opentelemetry-javaagent.jar` 引用 OTel agent；两仓库的"clone 顺序"和"位置约定"成为隐性依赖，新机器搭建必须同时拉两个仓库。

把 `patra-infra` 内容并入 `patra-api/infra/`，可以一举消除：
- 隐性的"两个仓库必须并列摆放"约定
- 分散的两个 README / CLAUDE.md / .gitignore
- "改部署配置要换仓库"的认知切换成本
- 远程仓库管理负担（GitHub 仓库 −1）

## 2. 决策与拒绝项

### 决策 A：迁移路径

| 方案 | 评估 |
|---|---|
| **A1. 整目录复制（采用）** | `cp -R` 把内容平铺到 `patra-api/infra/` 下，git 一次 commit 提交。**采用** — 单人项目无需保留 commit 历史的法医级精度；操作简单、diff 清晰；与 CLAUDE.md "绿地项目零历史包袱"原则一致。 |
| A2. `git filter-repo` 子目录化保留 commit 历史 | 否决 — 14 个 commit 全是 docker 配置局部演进，归档仓库已能查阅；引入额外工具（`git-filter-repo`）和 merge --allow-unrelated-histories 操作复杂度，对单人项目过度。 |
| A3. Git submodule | 否决 — 与"迁移到 patra-api"诉求相反；submodule 引入额外认知负担。 |

### 决策 B：落地位置

| 方案 | 评估 |
|---|---|
| **B1. `patra-api/infra/` 命名空间（采用）** | 顶层 `infra/` 与 Java 微服务模块（`patra-*-infra` 六边形架构基础设施层）语义不同（前者是部署编排、后者是代码层），但物理路径不会混淆。 |
| B2. `patra-api/docker/` + `patra-api/scripts/` 直接平铺 | 否决 — patra-api 已有 `scripts/`（含 javadoc 迁移、letpub 等开发工具），合并会让"运维脚本"和"开发工具脚本"混在同一目录，命名空间污染。 |
| B3. `patra-api/deploy/` | 否决 — `deploy` 偏 CI/CD 与发布，当前内容是本地 + Mac mini 开发环境，语义不准。 |

### 决策 C：原仓库处理

| 方案 | 评估 |
|---|---|
| **C1. 归档（采用）** | 本地 `mv` 到 `~/Projects/Archive/patra-infra/`，GitHub `gh repo archive` 标记为 archived 不删除。**采用** — 14 次 commit 历史完整保留，可读不可写，与活动开发隔离。 |
| C2. 直接删除 | 否决 — 不可逆，且 GitHub 仓库归档零成本，没理由删。 |
| C3. 保留不动 | 否决 — 容易让人困惑"哪个是当前真相"。 |

### 决策 D：文档迁移

| 方案 | 评估 |
|---|---|
| **D1. 不迁 superpowers 文档（采用）** | `patra-infra/docs/superpowers/{specs,plans}/2026-05-17-migrate-infra-to-macmini-*.md` 两份文档保留在归档仓库中，patra-api 的 docs 库不被污染。 |
| D2. 迁到 patra-api/docs/superpowers/ 下 | 否决 — 用户决策；这些是 patra-infra 内部演进的快照，与 patra-api 开发主线无关。 |

### 决策 E：脚本改名

| 方案 | 评估 |
|---|---|
| **E1. `init-macmini.sh` → `init-volumes.sh`（采用）** | 脚本无 Mac mini 专属逻辑（不 chmod `/Volumes/...`、不调 Tailscale、不识别 Apple Silicon），名字误导。改为 `init-volumes.sh` 准确反映"宿主机数据卷初始化"的实际作用。 |
| E2. 保持原名 | 否决 — 误导后续维护者。 |

## 3. 范围

### In scope

| 源 | 目标 |
|---|---|
| `patra-infra/docker/` | `patra-api/infra/docker/` |
| `patra-infra/scripts/init-macmini.sh` | `patra-api/infra/scripts/init-volumes.sh`（改名） |

patra-api 既有文件触达：

- `gradle.properties:51`：`../patra-infra/docker/opentelemetry-javaagent.jar` → `infra/docker/opentelemetry-javaagent.jar`
- `.gitignore`：把 `.env` / `.env.*` 限定为仓库顶层匹配，避免误伤 `infra/docker/.env*`（OTel agent jar 已由 `*.jar` 规则覆盖，无需新增）
- `infra/docker/README.md`：把"运行此脚本"指引由 `bash scripts/init-macmini.sh` 改为 `bash infra/scripts/init-volumes.sh`

仓库管理：

- 本地 `mv ~/Projects/Products/Patra/patra-infra ~/Projects/Archive/patra-infra`
- GitHub `gh repo archive linqibin0826/patra-infra --yes`
- 删除已合并的 worktree：`/Users/linqibin/.claude-worktrees/patra-infra/migrate-to-macmini`
- 删除已合并的分支：`chore/migrate-to-macmini`（本地 + 远程）

### Out of scope

- `patra-infra/docs/superpowers/` 两份文档不迁（决策 D）
- `patra-infra/README.md`、`patra-infra/.gitignore`、`patra-infra/.claude/CLAUDE.md` 不迁（patra-api 已有自己的版本）
- patra-api 内对 `patra-infra` 的历史引用（如 `docs/superpowers/specs|plans/2026-05-17-mysql-ops-*.md` 中提到的 `patra-infra/docker/...`）不修改 —— 这些是已归档的设计文档，路径作为历史快照保留
- Mac mini 上 `~/.patra/docker/` 实际数据卷不动 —— 用户确认已部署运行，迁移仅改变仓库代码位置，与运行时数据卷无关
- patra-infra 之前 push 到远程的状态：当前 origin/main 落后于本地已合并的 d7f8599（用户选 C 暂不 push），归档前会补一次 push 防止丢失 commit

## 4. 详细设计

### 4.1 目标目录结构

```
patra-api/
├── infra/                                  ← 新增顶层目录
│   ├── docker/
│   │   ├── docker-compose.{core,dev,jobs,observability,search,storage}.yaml
│   │   ├── .env / .env.dev / .env.example
│   │   ├── README.md
│   │   ├── alertmanager/  consul/  grafana/  loki/  mysql-ops/
│   │   ├── otel-agent/    otel-collector/   postgres/  prometheus/  tempo/
│   │   └── (opentelemetry-javaagent.jar — 工作目录有，.gitignore 忽略)
│   └── scripts/
│       └── init-volumes.sh                 ← 改名自 init-macmini.sh
├── docs/  patra-*/  linqibin-*/  scripts/  ... ← 既有不变
└── .gitignore                              ← 调整规则
```

### 4.2 `.gitignore` 调整

当前 `patra-api/.gitignore` 在 "Env files (local only)" 段含两条不带前缀斜杠的规则：

```
.env
.env.*
```

这是**任意层级**匹配，会把 `infra/docker/.env`、`infra/docker/.env.dev`、`infra/docker/.env.example` 全部忽略。但 `patra-infra` 当前的行为是**三个 .env 文件全部入库**（commit `e1d11bb` "docker compose up 默认即可工作" 的明确决策）。为保留这一行为，把规则限定为顶层：

```diff
- .env
- .env.*
+ /.env
+ /.env.*
```

带前缀 `/` 的 pattern 在 .gitignore 语法中只匹配仓库根。

OTel agent jar（23 MB）已被既有规则 `*.jar` 覆盖（连同 `!gradle/wrapper/gradle-wrapper.jar` 的反向放行机制），无需新增条目。

### 4.3 `gradle.properties` 路径修复

```diff
- otel.agent.path=../patra-infra/docker/opentelemetry-javaagent.jar
+ otel.agent.path=infra/docker/opentelemetry-javaagent.jar
```

迁移后 OTel agent jar 仍由用户从官方下载放到 `infra/docker/` 下（gitignore 不入库）。`gradle.properties` 中此路径用于本地 IDE run config 注入 `-javaagent`。

### 4.4 `infra/scripts/init-volumes.sh`

内容不变，仅文件名改。脚本内部所有路径都基于 `${HOME}/.patra/docker/`，与仓库位置无关，无需修改。

### 4.5 `infra/docker/README.md` 引用更新

原 README 中提到的 "运行 `scripts/init-macmini.sh`" 改为 "运行 `infra/scripts/init-volumes.sh`"。其他内容（端口表、服务列表、Mac mini 部署指南）不变。

### 4.6 仓库归档操作

```bash
# 1. 确保已合并状态推到远程（防止归档时丢 d7f8599）
cd ~/Projects/Products/Patra/patra-infra
git push origin main

# 2. 清理 worktree 与已合并分支
git worktree remove /Users/linqibin/.claude-worktrees/patra-infra/migrate-to-macmini
git branch -d chore/migrate-to-macmini
git push origin --delete chore/migrate-to-macmini

# 3. 物理移动
mv ~/Projects/Products/Patra/patra-infra ~/Projects/Archive/patra-infra

# 4. GitHub 归档
gh repo archive linqibin0826/patra-infra --yes
```

## 5. 执行顺序

按依赖分四个阶段，每个阶段独立可验证：

1. **复制内容**：`cp -R` docker/ 和 scripts/init-macmini.sh（同时改名为 init-volumes.sh）
2. **调整 patra-api 既有文件**：`.gitignore`、`gradle.properties:51`、`infra/docker/README.md` 中的脚本引用
3. **验证 + commit 到 patra-api**：见 §6
4. **归档原仓库**：见 §4.6

阶段 1-3 可一次 commit 到 patra-api，阶段 4 与 patra-api 解耦，可独立执行（先 commit、push patra-api 验证一切正常后再归档原仓库，最稳）。

## 6. 验证

### 6.1 构建验证（强制）

```bash
cd ~/Projects/Products/Patra/patra-api
./gradlew clean build -x test
```

预期：构建通过；OTel agent 路径无误（gradle 不解析此路径，只是写入 IDE run config，所以构建本身不会因路径错误失败 —— 但要肉眼检查 `gradle.properties` 改对了）。

### 6.2 Docker Compose 验证

```bash
cd ~/Projects/Products/Patra/patra-api/infra/docker
docker compose -f docker-compose.core.yaml config  # 解析配置不报错
```

不要在本地真正启动容器，因 Mac mini 已经在跑这套服务，避免端口冲突。

### 6.3 脚本验证

```bash
ls -la ~/Projects/Products/Patra/patra-api/infra/scripts/init-volumes.sh
# 权限应为 -rwxr-xr-x（与原文件一致）
bash -n ~/Projects/Products/Patra/patra-api/infra/scripts/init-volumes.sh
# 仅做语法检查（-n），不实际运行；mac mini 已经初始化过，不需要再跑
```

### 6.4 提交前自检

- `git status` 中应有：新增 `infra/`、修改 `.gitignore`、修改 `gradle.properties`
- `git diff gradle.properties` 应只有 OTel agent 路径一行变化
- `git diff .gitignore` 应只把 `.env` / `.env.*` 改为 `/.env` / `/.env.*`，其余不动

## 7. 回退策略

阶段 3 commit 前：本地随意 `git restore`。

阶段 3 commit 后但未 push：`git reset --hard HEAD~1`。

阶段 3 已 push 但阶段 4 未执行：原 patra-infra 仓库还在 `Products/Patra/patra-infra` 位置，OTel agent jar 路径已断（gradle.properties 已改），可以临时把 jar 拷贝过去恢复。

阶段 4 已归档：从 `~/Projects/Archive/patra-infra` 拷回，GitHub `gh repo edit --visibility public` 或 web UI 取消归档。

## 8. 已知风险

| 风险 | 缓解 |
|---|---|
| OTel agent jar 不入库，新机器 clone 后构建会找不到此文件 | 现状已经如此（`patra-infra/.gitignore` 也忽略），不引入新问题；后续可加一个 setup 脚本自动从官方下载 |
| Mac mini 上的 `~/.patra/docker/` 数据卷仍指向原 docker compose 文件路径运行中 | 数据卷绑定与仓库位置解耦（脚本内 `${HOME}/.patra/docker/...`），仅 docker compose 文件路径变化。Mac mini 上 git pull 时只需把仓库换成 patra-api，重新 `docker compose -f infra/docker/docker-compose.dev.yaml up -d` 即可 |
| patra-api 历史 spec 文档中的 `patra-infra/docker/...` 路径失效 | 这些是已归档的设计文档，路径作为时间戳保留，无现实指代意义；不必修改 |

## 9. 后续工作（非本次范围）

- Mac mini 上 git remote 切换（从 `patra-infra` 改 `patra-api`），可在归档完成后随时操作
- `infra/scripts/init-volumes.sh` 增加 `--dry-run` 选项
- OTel agent jar 自动下载脚本
