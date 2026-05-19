# patra-infra 迁移到 patra-api/infra/ 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把独立仓库 `patra-infra` 的全部有效内容（`docker/` + `scripts/init-macmini.sh`）整合到 `patra-api/infra/` 下，调整既有引用，并归档原仓库。

**Architecture:** 单 commit 完成 patra-api 这一侧的迁移（拷贝 + 改路径 + 调 gitignore），再独立完成原仓归档；不保留 patra-infra 的 commit 历史（归档仓库已能查阅）。

**Tech Stack:** Bash / Git / Gradle 9.2.1 / Docker Compose / gh CLI

**关联 spec:** [`docs/patra/specs/2026-05-18-patra-infra-migration-design.md`](../specs/2026-05-18-patra-infra-migration-design.md)

**工作位置:** 本 plan 在 patra-api 当前 worktree 执行。`阶段 4` 需要切换到 `/Users/linqibin/Projects/Products/Patra/patra-infra` 操作原仓库。

**前置条件:**
- patra-infra 仓库 HEAD 已合并到 `d7f8599`（chore/migrate-to-macmini 已合入 main）
- patra-api 工作目录干净

---

## 阶段 1：把内容搬到 patra-api/infra/

### Task 1: 创建 infra/ 目录骨架并拷贝 docker/ 全部内容

**Files:**
- Create: `infra/` （新目录）
- Create: `infra/docker/`（含 30 个文件、18 个目录，与源等同）

**说明：** 用 `cp -R` 一次性整目录拷贝。源是 `/Users/linqibin/Projects/Products/Patra/patra-infra/docker/`，目标是 patra-api 仓库根的 `infra/docker/`。源 docker 目录可能含被 gitignore 的 `opentelemetry-javaagent.jar`（23 MB），需要一起拷贝（工作目录持有，不入库）。

- [ ] **Step 1: 确认源目录干净，记录文件清单**

```bash
SRC=/Users/linqibin/Projects/Products/Patra/patra-infra
git -C "$SRC" status --porcelain
find "$SRC/docker" -type f | wc -l
find "$SRC/docker" -type d | wc -l
```

Expected:
- `git status` 空输出（工作目录干净）
- 文件数 `30`（含 `.env`、`.env.dev`、`.env.example`、`opentelemetry-javaagent.jar` 等）
- 目录数 `18`

- [ ] **Step 2: 创建 infra/ 顶层目录**

```bash
mkdir -p infra
```

Expected: 无输出，`ls -d infra` 应可见目录。

- [ ] **Step 3: 拷贝 docker/ 全部内容（含 dotfile 和大 jar）**

```bash
cp -R /Users/linqibin/Projects/Products/Patra/patra-infra/docker infra/docker
```

注意 `cp -R` 末尾不要写 `docker/`，否则会变成 `infra/docker/docker/`。

- [ ] **Step 4: 验证拷贝完整性**

```bash
diff -r /Users/linqibin/Projects/Products/Patra/patra-infra/docker infra/docker
echo "exit=$?"
```

Expected: 无差异输出，`exit=0`。

```bash
find infra/docker -type f | wc -l
find infra/docker -type d | wc -l
```

Expected: 文件数 `30`，目录数 `18`（与 Step 1 一致）。

```bash
ls -la infra/docker/opentelemetry-javaagent.jar
```

Expected: 文件存在，约 23 MB。

---

### Task 2: 拷贝并改名 init-macmini.sh → init-volumes.sh

**Files:**
- Create: `infra/scripts/init-volumes.sh` (重命名自源 `scripts/init-macmini.sh`)

- [ ] **Step 1: 创建 infra/scripts/ 目录**

```bash
mkdir -p infra/scripts
```

- [ ] **Step 2: 拷贝并改名（保留可执行权限）**

```bash
cp -p /Users/linqibin/Projects/Products/Patra/patra-infra/scripts/init-macmini.sh \
      infra/scripts/init-volumes.sh
```

`-p` 标志保留权限/时间戳。

- [ ] **Step 3: 验证可执行权限与文件大小**

```bash
ls -la infra/scripts/init-volumes.sh
```

Expected: 权限为 `-rwxr-xr-x`，大小约 2749 字节（与源一致）。

- [ ] **Step 4: bash 语法检查**

```bash
bash -n infra/scripts/init-volumes.sh
echo "exit=$?"
```

Expected: 无输出，`exit=0`。脚本不实际运行（Mac mini 已初始化过）。

- [ ] **Step 5: 确认脚本内部路径无需修改**

```bash
grep -nE 'patra-infra|init-macmini|/Users|/Projects' infra/scripts/init-volumes.sh
```

Expected: 无输出（脚本所有路径都基于 `${HOME}/.patra/docker/`，与仓库位置和文件名解耦）。

---

## 阶段 2：调整 patra-api 既有文件

### Task 3: 修复 gradle.properties OTel agent 相对路径

**Files:**
- Modify: `gradle.properties:51`

- [ ] **Step 1: 查看当前 OTel agent 路径配置**

```bash
sed -n '49,53p' gradle.properties
```

Expected:
```
# ============================================================
# OTel Agent JAR 路径（相对于项目根目录）
otel.agent.path=../patra-infra/docker/opentelemetry-javaagent.jar
# OTLP 导出端点
otel.exporter.endpoint=http://localhost:4317
```

- [ ] **Step 2: 修改路径**

把 `gradle.properties` 第 51 行从：
```
otel.agent.path=../patra-infra/docker/opentelemetry-javaagent.jar
```
改为：
```
otel.agent.path=infra/docker/opentelemetry-javaagent.jar
```

可用 Edit 工具或：
```bash
sed -i.bak 's|../patra-infra/docker/opentelemetry-javaagent.jar|infra/docker/opentelemetry-javaagent.jar|' gradle.properties
rm gradle.properties.bak
```

- [ ] **Step 3: 验证替换正确**

```bash
grep "otel.agent.path" gradle.properties
```

Expected: 仅一行 `otel.agent.path=infra/docker/opentelemetry-javaagent.jar`。

```bash
grep -c "patra-infra" gradle.properties
```

Expected: `0`（没有任何残留引用）。

- [ ] **Step 4: 验证 jar 文件在新路径下确实存在**

```bash
ls -la infra/docker/opentelemetry-javaagent.jar
```

Expected: 文件存在（Task 1 已拷贝）。

---

### Task 4: 调整 .gitignore 把 .env 限定为仓库顶层匹配

**Files:**
- Modify: `.gitignore` （"Env files (local only)" 段）

**说明：** patra-infra 当前把 `.env` / `.env.dev` / `.env.example` 三个文件全部入库（commit `e1d11bb` "docker compose up 默认即可工作"）。patra-api 现有 `.env` / `.env.*` 是任意层级匹配，会误伤 `infra/docker/.env*`。改为加 `/` 前缀，限定为仓库根匹配。

- [ ] **Step 1: 查看当前 .env 规则**

```bash
grep -n -A1 "Env files" .gitignore
```

Expected:
```
85:# Env files (local only)
86-##############################
87-.env
88-.env.*
```
（行号可能略有差异）

- [ ] **Step 2: 修改两条规则**

把 `.gitignore` 中的：
```
.env
.env.*
```
改为：
```
/.env
/.env.*
```

可用 Edit 工具精确替换两行（注意上下文：在 `# Env files (local only)` 段下方）。

- [ ] **Step 3: 验证规则正确**

```bash
grep -nE '^/?\.env' .gitignore
```

Expected: 输出两行，分别是 `/.env` 和 `/.env.*`，且**没有**不带前缀的 `.env` 或 `.env.*`。

- [ ] **Step 4: 验证 infra/docker/.env* 不再被忽略**

```bash
git check-ignore -v infra/docker/.env infra/docker/.env.dev infra/docker/.env.example 2>&1
echo "exit=$?"
```

Expected: 无忽略匹配（`exit=1`），表示三个文件可以入库。

- [ ] **Step 5: 验证 *.jar 规则仍然覆盖 OTel agent jar**

```bash
git check-ignore -v infra/docker/opentelemetry-javaagent.jar
```

Expected: 输出形如 `.gitignore:NN:*.jar  infra/docker/opentelemetry-javaagent.jar`，表示被 `*.jar` 规则覆盖。

---

### Task 5: 全面更新 infra/docker/README.md 中的路径与脚本引用

**Files:**
- Modify: `infra/docker/README.md`

**说明（对 spec §4.5 的扩充）：** spec 只显式提到 `init-macmini.sh` 一处改名，但 README 实际上还有 25+ 处仓库根相对路径需要同步：
- `~/Projects/patra-infra/` → `~/Projects/patra-api/`（4 处）
- `scripts/init-macmini.sh` → `infra/scripts/init-volumes.sh`（1 处）
- `docker/docker-compose.*.yaml` → `infra/docker/docker-compose.*.yaml`（多处）
- `docker/.env` → `infra/docker/.env`（1 处）

- [ ] **Step 1: 列出所有需要替换的位置**

```bash
grep -nE 'scripts/init-macmini|~/Projects/patra-infra|docker/docker-compose|docker/\.env' infra/docker/README.md
```

Expected: 输出 25+ 行，覆盖行 31, 62, 63, 67, 70, 73, 94, 96, 104, 105, 181, 184, 185, 188, 189, 192, 193, 213, 216, 219, 222, 225, 236, 264 等。

- [ ] **Step 2: 执行四组全局替换**

```bash
# 1. 仓库副本路径
sed -i.bak 's|~/Projects/patra-infra/|~/Projects/patra-api/|g' infra/docker/README.md
sed -i.bak 's|~/Projects/patra-infra|~/Projects/patra-api|g' infra/docker/README.md

# 2. 初始化脚本调用
sed -i.bak 's|bash scripts/init-macmini\.sh|bash infra/scripts/init-volumes.sh|g' infra/docker/README.md

# 3. docker-compose 文件相对路径
sed -i.bak 's|docker/docker-compose|infra/docker/docker-compose|g' infra/docker/README.md

# 4. .env 文件路径
sed -i.bak 's|docker/\.env\b|infra/docker/.env|g' infra/docker/README.md

# 清理备份
rm infra/docker/README.md.bak
```

- [ ] **Step 3: 验证全部替换成功**

```bash
grep -nE 'scripts/init-macmini|~/Projects/patra-infra' infra/docker/README.md
echo "exit=$?"
```

Expected: 无输出（`exit=1`）。

```bash
grep -cE 'infra/docker/docker-compose' infra/docker/README.md
```

Expected: 数字 >= 17（与原 `docker/docker-compose` 计数一致）。

```bash
grep -cE '^[^!]*docker/docker-compose' infra/docker/README.md
```

Expected: `0`（没有残留的旧路径形式，即任何 "docker/docker-compose" 都是被 "infra/" 前缀包住的）。

- [ ] **Step 4: 抽检几个关键段落**

```bash
sed -n '60,75p' infra/docker/README.md
```

Expected: 显示 Mac mini 部署步骤，路径已更新为 `~/Projects/patra-api`、`infra/scripts/init-volumes.sh`、`infra/docker/docker-compose.dev.yaml`。

---

## 阶段 3：验证 + commit

### Task 6: Gradle 构建验证

- [ ] **Step 1: 跑构建（跳过测试节约时间）**

```bash
./gradlew clean build -x test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`，无 task 失败。

> 注：gradle 不会真正解析 `otel.agent.path`，但构建任务流程不应被 gradle.properties 改动打断。

- [ ] **Step 2: 验证 IDE / run config 期望路径**

```bash
ls -la infra/docker/opentelemetry-javaagent.jar
grep "otel.agent.path" gradle.properties
```

Expected: jar 存在；gradle.properties 显示新相对路径。

---

### Task 7: Docker Compose 配置解析验证

**说明：** 不真启动容器（Mac mini 已经在跑这套服务，避免端口冲突）。

- [ ] **Step 1: 解析所有 compose 文件**

```bash
for f in core dev jobs observability search storage; do
  echo "=== $f ==="
  docker compose --env-file infra/docker/.env -f infra/docker/docker-compose.${f}.yaml config > /dev/null
  echo "exit=$?"
done
```

Expected: 6 个 `exit=0`。如果某个文件需要额外 `--env-file infra/docker/.env.dev`，按需补上。

- [ ] **Step 2: 抽检 jobs.yaml 简化后的 broker 命令**

```bash
wc -l infra/docker/docker-compose.jobs.yaml
```

Expected: 约 146 行（合并 chore/migrate-to-macmini 后的精简版）。

```bash
grep -c "host.docker.internal\|/proc/net/route" infra/docker/docker-compose.jobs.yaml
```

Expected: `0`（已无 IP 自动检测残留）。

---

### Task 8: 脚本语法验证

- [ ] **Step 1: 检查 init-volumes.sh 语法**

```bash
bash -n infra/scripts/init-volumes.sh
echo "exit=$?"
```

Expected: `exit=0`。

- [ ] **Step 2: 确认权限**

```bash
test -x infra/scripts/init-volumes.sh && echo "executable" || echo "NOT executable"
```

Expected: `executable`。

---

### Task 9: 提交前 diff 自检 + commit

- [ ] **Step 1: 查看 git status 概览**

```bash
git status --short
```

Expected:
- 新增 `infra/`（含 docker 与 scripts 子目录全部入库）
- 修改 `.gitignore`
- 修改 `gradle.properties`
- **不应**出现 `infra/docker/opentelemetry-javaagent.jar`（被 `*.jar` 忽略）

- [ ] **Step 2: 验证 gradle.properties 的 diff 仅一行**

```bash
git diff gradle.properties
```

Expected: 唯一一行变化是 OTel agent 路径。

- [ ] **Step 3: 验证 .gitignore 的 diff 仅两行加前缀**

```bash
git diff .gitignore
```

Expected: 仅 `.env` → `/.env`、`.env.*` → `/.env.*` 两处。

- [ ] **Step 4: 验证三个 .env 文件确实入库**

```bash
git status --short infra/docker/.env infra/docker/.env.dev infra/docker/.env.example
```

Expected: 三个文件全部显示为 `A` 或 `??`（待入库）。

- [ ] **Step 5: 抽检 OTel agent jar 不在变更列表里**

```bash
git status --short infra/docker/opentelemetry-javaagent.jar
echo "exit=$?"
```

Expected: 无输出。

- [ ] **Step 6: 添加全部变更并 commit**

```bash
git add infra/ .gitignore gradle.properties
git commit -m "$(cat <<'EOF'
chore(infra): 整合 patra-infra 仓库到 patra-api/infra/

把独立仓库 patra-infra（docker compose 部署配置 + Mac mini 初始化脚本）
整体合入 patra-api/infra/ 下，消除两仓物理位置耦合：

- infra/docker/             ← 整目录拷贝自 patra-infra/docker/
- infra/scripts/init-volumes.sh  ← 改名自 init-macmini.sh（脚本无 Mac mini 专属逻辑）
- gradle.properties: OTel agent 路径改为 infra/docker/ 相对路径
- .gitignore: .env / .env.* 加 / 前缀限定顶层，放行 infra/docker/.env*
- infra/docker/README.md: 25+ 处仓库相对路径同步更新

原 patra-infra 仓库归档到 ~/Projects/Archive/（后续独立操作）。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: commit 成功，pre-commit hook（如有）通过。

- [ ] **Step 7: 看一眼 commit 摘要**

```bash
git log --stat -1
```

Expected: 一个 commit 显示 30+ 个新文件 + 2 个修改文件。

---

## 阶段 4：归档原 patra-infra 仓库（独立可执行）

> **说明：** 这个阶段操作的不是 patra-api，而是 `/Users/linqibin/Projects/Products/Patra/patra-infra` 主仓库。它与阶段 1-3 解耦，可以在 patra-api 这边 PR 合入 main 之后再做。

### Task 10: 推送 patra-infra 已合并的 commit 到远程

**说明：** 之前用户选 C 暂未 push。归档前确保远程不丢 14 个 commit。

- [ ] **Step 1: 确认本地 main 与远程的差距**

```bash
cd /Users/linqibin/Projects/Products/Patra/patra-infra
git fetch origin
git log --oneline origin/main..main | wc -l
```

Expected: `14`（或更多，如果之间又新增）。

- [ ] **Step 2: push main**

```bash
git push origin main
```

Expected: push 成功，输出 `<old>..<new> main -> main`。

- [ ] **Step 3: 验证远程已同步**

```bash
git fetch origin
git log --oneline origin/main..main
```

Expected: 空输出（本地与远程一致）。

---

### Task 11: 清理 chore/migrate-to-macmini 分支与 worktree

- [ ] **Step 1: 列出现有 worktree**

```bash
git worktree list
```

Expected: 看到 `chore/migrate-to-macmini` 分支的 worktree 在 `/Users/linqibin/.claude-worktrees/patra-infra/migrate-to-macmini`。

- [ ] **Step 2: 删除该 worktree**

```bash
git worktree remove /Users/linqibin/.claude-worktrees/patra-infra/migrate-to-macmini
```

Expected: 无输出，`git worktree list` 应只剩主 worktree。

- [ ] **Step 3: 删除本地分支**

```bash
git branch -d chore/migrate-to-macmini
```

Expected: `Deleted branch chore/migrate-to-macmini (was d7f8599).`

> 若提示 not fully merged：执行 `git merge-base --is-ancestor chore/migrate-to-macmini main && echo OK || echo NOT MERGED` 复核。`OK` 则用 `-D` 强删，`NOT MERGED` 则停下来排查。

- [ ] **Step 4: 删除远程分支**

```bash
git push origin --delete chore/migrate-to-macmini
```

Expected: `- [deleted] chore/migrate-to-macmini`。

---

### Task 12: 把 patra-infra 物理移动到 Archive

- [ ] **Step 1: 确认 Archive 目录存在**

```bash
ls -d ~/Projects/Archive 2>&1
```

Expected: 目录存在。若不存在：`mkdir -p ~/Projects/Archive`。

- [ ] **Step 2: 确认目标位置无冲突**

```bash
ls -d ~/Projects/Archive/patra-infra 2>&1
```

Expected: `No such file or directory`。若已存在，停下来手动确认。

- [ ] **Step 3: 物理移动**

```bash
cd ~  # 离开 patra-infra 目录避免 mv 时持有句柄
mv ~/Projects/Products/Patra/patra-infra ~/Projects/Archive/patra-infra
```

Expected: 无输出。

- [ ] **Step 4: 验证移动结果**

```bash
ls -d ~/Projects/Products/Patra/patra-infra 2>&1
ls -d ~/Projects/Archive/patra-infra
git -C ~/Projects/Archive/patra-infra log --oneline -3
```

Expected:
- 原位置 `No such file or directory`
- 新位置存在
- git log 仍可读出 `d7f8599 ...`（仓库完整）

---

### Task 13: GitHub 远程仓库归档

- [ ] **Step 1: 确认 gh CLI 已登录**

```bash
gh auth status
```

Expected: 看到 `linqibin0826` 已认证。

- [ ] **Step 2: 归档远程仓库**

```bash
gh repo archive linqibin0826/patra-infra --yes
```

Expected: 输出 `✓ Archived repository linqibin0826/patra-infra`。

- [ ] **Step 3: 验证归档状态**

```bash
gh repo view linqibin0826/patra-infra --json isArchived
```

Expected: `{"isArchived":true}`。

---

## 完成后状态

- `patra-api/infra/docker/` 与 `patra-api/infra/scripts/init-volumes.sh` 入库
- `patra-api/gradle.properties` OTel 路径修正
- `patra-api/.gitignore` `.env` 规则限定顶层
- `patra-api/infra/docker/README.md` 路径全面更新
- 一个新 commit（阶段 3 Task 9）落到 patra-api 当前分支
- `~/Projects/Archive/patra-infra/` 持有原仓库；GitHub 标记 archived
- 原 `~/Projects/Products/Patra/patra-infra` 消失；本地分支与 worktree 清理完毕

---

## 自审记录（writing-plans 自检）

1. **Spec 覆盖**：spec §3 的 in-scope 全部映射到任务：源拷贝 (T1+T2) / gradle.properties (T3) / .gitignore (T4) / README 引用 (T5) / 归档操作 (T10-T13)。**发现 spec §4.5 描述不完整**（只提 init-macmini.sh 改名，未覆盖 README 中其他 25+ 处路径），plan 已在 Task 5 显式扩充并标注"对 spec §4.5 的扩充"。后续如需更新 spec，可以单独提一个补丁 commit。
2. **占位符扫描**：无 TBD / TODO / "适当处理" 类语句；每个步骤都给了具体命令和预期输出。
3. **类型一致性**：无类型/方法签名问题（非代码任务）。文件名 `init-volumes.sh` 在 T2 创建后被 T5 / T6 / T8 引用，名字统一。
