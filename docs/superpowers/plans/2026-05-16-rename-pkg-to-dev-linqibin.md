# 包名重命名实施计划（com.patra → dev.linqibin）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 patra-api 项目顶层包从 `com.patra` 重命名为 `dev.linqibin`，并把通用基础设施（CQRS / DDD / 错误处理 / JSON / 对象存储等）从 `patra-common-*` 抽离到独立的 `linqibin-commons-*` 模块。

**Architecture:** 自下而上的 5 阶段重构 — Phase 0 baseline + 分支；Phase 1 创建 Gradle 模块壳（不动源码）；Phase 2 拆分 patra-common-* 内容到 linqibin-commons-*；Phase 3 业务服务 + patra 专用 starter 包名 rename；Phase 4 通用 starter 包名 + artifact ID rename；Phase 5 全局配置 + 验证。每个 task 内：操作→编译验证→commit。

**Tech Stack:** Java 25, Spring Boot 4.0.1, Gradle 9.2.1 (Kotlin DSL + Convention Plugins), JPA/Hibernate, RestClient, Spring Cloud Consul

**Spec 参考:** [docs/superpowers/specs/2026-05-15-rename-package-to-dev-linqibin-design.md](../specs/2026-05-15-rename-package-to-dev-linqibin-design.md)（commit e518b7ecc）

---

## TDD 在纯重构中的适配

本计划是**纯重构**（保持行为不变，改变结构）。Skill 默认的"failing test → impl → passing test" TDD 模式不直接适用 — 因为没有新增行为可测。本计划的 TDD 适配：

- **"failing test"**：每个 task 操作前，确保 `./gradlew :affected-module:compileJava` 或全量 `./gradlew compileJava` 通过（baseline 绿）
- **"impl"**：执行 task 描述的具体操作（git mv、sed 替换、新建文件等）
- **"passing test"**：执行后 `./gradlew :affected-module:compileJava` 仍然通过 + 相关 `grep` 零残留 + Phase 5 末尾跑全量业务测试

每个 task 结束时 commit。Phase 末尾跑该 Phase 的 spec "验证 gate"。

---

## 平台前提

- **OS**：macOS（Darwin 25.4.0）
- **sed**：使用 BSD sed 语法（`sed -i ''` 双引号空字符串作 inplace 备份）
- **Shell**：zsh
- 所有命令假设 working directory 是 `/Users/linqibin/Projects/Products/Patra/patra-api`
- **Docker desktop**：执行任一 baseline / 验证 build 前必须运行（Testcontainers 集成测试依赖 `/var/run/docker.sock`）
- **SpotBugs 跳过**：所有 `./gradlew build` 都加 `-x spotbugsMain -x spotbugsTest`。原因：main 分支上 `spotbugs-exclude.xml` 存在已知的 class 名错位（如 filter 写 `JsonNormalizer$Result` 但实际类是 `JsonNormalizerResult`；`DomainException` 的 `CT_CONSTRUCTOR_THROW` 排除只覆盖 `domain.policy` 包但类在 `common.error`）。这些与本次重构无关，留待独立 PR 修复

---

## File Structure 概览

本次重构影响的文件类别（来自 spec Section 4 与 Explore 扫描）：

| 类别 | 数量 | 处理 Phase |
|------|------|-----------|
| Java 源文件（package + import + 字面量） | 1,755 | Phase 2-4 |
| 物理目录（`src/*/java/com/patra/`） | 78 | Phase 2-4 git mv |
| Gradle 模块 | 21 → 22（+3 -2） | Phase 1（新建/重命名）+ Phase 2（删除） |
| settings.gradle.kts | 1 | Phase 1 + Phase 2 |
| gradle.properties | 1 | Phase 1（新增 commonsGroup）+ Phase 5（修改 patraGroup） |
| build-logic Kotlin 源 | 数十个 | Phase 1 |
| `META-INF/.../AutoConfiguration.imports` | 51 | Phase 4 |
| `application*.yml` | 6 | Phase 5 |
| `logback*.xml` | ~80 | Phase 5 |
| `.run/*.xml` | 10 | Phase 5 |
| `.md` 文档 | 16 | Phase 5 |

---

## Phase 0：基线 + 准备

### Task 0.1：Baseline build + 创建 refactor 分支

**Files:**
- 无文件改动；仅 git 分支操作

- [ ] **Step 1：确认 working tree 干净**

Run:
```bash
git status -s
```

Expected：空输出。若有未提交内容，先 stash 或 commit。

- [ ] **Step 2：跑 baseline build**

**前置：确认 Docker desktop 已启动**（执行 `docker ps` 不报 socket 错即可）。

Run:
```bash
./gradlew build -x spotbugsMain -x spotbugsTest --no-daemon
```

Expected：`BUILD SUCCESSFUL`（约 5-15 分钟）。若失败，必须先修复 baseline 再开始重构。

注：跳过 SpotBugs 详见"平台前提"节的说明。

- [ ] **Step 3：记录 baseline commit SHA 作回滚锚点**

Run:
```bash
git rev-parse HEAD | tee /tmp/baseline-sha.txt
```

Expected：输出一个 commit SHA，写到 `/tmp/baseline-sha.txt`。

- [ ] **Step 4：创建并切换到 refactor 分支**

Run:
```bash
git checkout -b refactor/rename-pkg-to-dev-linqibin
git branch --show-current
```

Expected：当前分支为 `refactor/rename-pkg-to-dev-linqibin`。

- [ ] **Step 5：Phase 0 不产生新 commit**（仅切分支）

Verification gate：进入 Phase 1 前 `git log -1 --oneline` 输出与 baseline SHA 一致。

---

## Phase 1：Gradle 模块结构调整（shell-only，不动源码）

**目标**：所有 Gradle 模块结构调整完毕，但 src 源码仍然在旧位置使用 com.patra 包名 — 项目仍然完整编译通过。这是个稳定的中间态。

### Task 1.1：创建 3 个新模块的 skeleton

**Files:**
- Create: `linqibin-commons-core/build.gradle.kts`
- Create: `linqibin-commons-core/src/main/java/dev/linqibin/commons/.gitkeep`
- Create: `linqibin-commons-storage/build.gradle.kts`
- Create: `linqibin-commons-storage/src/main/java/dev/linqibin/commons/storage/.gitkeep`
- Create: `patra-common-enums/build.gradle.kts`
- Create: `patra-common-enums/src/main/java/dev/linqibin/patra/common/enums/.gitkeep`

- [ ] **Step 1：参考现有 patra-common-core/build.gradle.kts 的内容**

Run:
```bash
cat patra-common-core/build.gradle.kts
```

记下它的 plugin、dependencies、其他配置 — 新模块 build.gradle.kts 大体类似（剥离业务依赖）。

- [ ] **Step 2：创建 linqibin-commons-core 模块壳**

Run:
```bash
mkdir -p linqibin-commons-core/src/main/java/dev/linqibin/commons
mkdir -p linqibin-commons-core/src/test/java/dev/linqibin/commons
touch linqibin-commons-core/src/main/java/dev/linqibin/commons/.gitkeep
touch linqibin-commons-core/src/test/java/dev/linqibin/commons/.gitkeep
```

写入 `linqibin-commons-core/build.gradle.kts`（参照 patra-common-core 风格，移除 patra 特有依赖）：

```kotlin
plugins {
    id("patra.hexagonal-domain")  // Phase 1.3 后改为 linqibin.hexagonal-domain
}

dependencies {
    // 通用基础设施依赖：Jackson + Lombok 等基础库
    // 不依赖任何 patra-* 模块
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)
    implementation(libs.hutool.all)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
```

注：libs.* 引用见 `gradle/libs.versions.toml`，与 patra-common-core 用的引用保持一致。

- [ ] **Step 3：创建 linqibin-commons-storage 模块壳**

Run:
```bash
mkdir -p linqibin-commons-storage/src/main/java/dev/linqibin/commons/storage
touch linqibin-commons-storage/src/main/java/dev/linqibin/commons/storage/.gitkeep
```

写入 `linqibin-commons-storage/build.gradle.kts`（参照 patra-common-storage）：

```kotlin
plugins {
    id("patra.hexagonal-domain")  // Phase 1.3 后改为 linqibin.hexagonal-domain
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
```

- [ ] **Step 4：创建 patra-common-enums 模块壳**

Run:
```bash
mkdir -p patra-common-enums/src/main/java/dev/linqibin/patra/common/enums
touch patra-common-enums/src/main/java/dev/linqibin/patra/common/enums/.gitkeep
```

写入 `patra-common-enums/build.gradle.kts`：

```kotlin
plugins {
    id("patra.hexagonal-domain")  // Phase 1.3 后改为 linqibin.hexagonal-domain
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
```

- [ ] **Step 5：暂时不在 settings.gradle.kts 注册新模块**（Phase 1.5 统一注册）

无需操作。

- [ ] **Step 6：Commit**

```bash
git add linqibin-commons-core linqibin-commons-storage patra-common-enums
git commit -m "$(cat <<'EOF'
refactor(rename): Phase 1.1 创建 linqibin-commons-{core,storage} + patra-common-enums 模块壳

为后续 Phase 2 内容迁移做准备，当前模块为空壳，未注册到 settings。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.2：重命名 11 个通用 starter 物理目录

**Files:**
- Rename: `patra-spring-boot-starter-{core,web,jpa,batch,rest-client,object-storage,observability,redisson,test,http-interface,openapi}` → `linqibin-spring-boot-starter-{name}`

- [ ] **Step 1：列出需要重命名的 11 个目录**

Run:
```bash
ls -d patra-spring-boot-starter-* | grep -vE 'provenance|expr'
```

Expected：列出 11 个目录（不含 provenance 和 expr）。

- [ ] **Step 2：执行 11 次 git mv**

Run:
```bash
for name in core web jpa batch rest-client object-storage observability redisson test http-interface openapi; do
  git mv "patra-spring-boot-starter-$name" "linqibin-spring-boot-starter-$name"
done
```

- [ ] **Step 3：验证目录已 rename**

Run:
```bash
ls -d linqibin-spring-boot-starter-* | wc -l
ls -d patra-spring-boot-starter-* 2>/dev/null | wc -l
```

Expected：第一条输出 `11`；第二条输出 `2`（仅剩 provenance 和 expr）。

- [ ] **Step 4：此时 settings.gradle.kts 中的 include 路径还指向旧目录，编译会失败**

不要在这一步跑编译 — 在 Task 1.5 完成 settings 更新后再验证。

- [ ] **Step 5：Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(rename): Phase 1.2 重命名 11 个通用 starter 目录 patra-* → linqibin-*

settings.gradle.kts 的 include 路径在 Task 1.5 中同步更新，本 commit 后编译尚未恢复。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.3：build-logic Kotlin 包名 + Plugin ID rename

**Files:**
- Rename: `build-logic/src/main/kotlin/com/patra/buildlogic/` → `build-logic/src/main/kotlin/dev/linqibin/patra/buildlogic/`
- Modify: 所有 Kotlin 文件的 `package com.patra.buildlogic` 声明
- Modify: `build-logic/build.gradle.kts`（或 `gradlePlugin` 块所在文件）的 plugin ID 定义

- [ ] **Step 1：查看 build-logic 现有结构**

Run:
```bash
find build-logic/src -type d -name "patra" -o -name "buildlogic" 2>/dev/null
find build-logic -name "*.gradle.kts" -exec grep -l "patra\.hexagonal" {} \;
find build-logic -name "*.kt" | head -20
```

记下当前 Kotlin 包结构与 plugin ID 定义位置。

- [ ] **Step 2：物理迁移 Kotlin 包目录**

Run:
```bash
mkdir -p build-logic/src/main/kotlin/dev/linqibin/patra
git mv build-logic/src/main/kotlin/com/patra/buildlogic build-logic/src/main/kotlin/dev/linqibin/patra/buildlogic
# 清理空残留目录
rmdir build-logic/src/main/kotlin/com/patra 2>/dev/null
rmdir build-logic/src/main/kotlin/com 2>/dev/null
```

- [ ] **Step 3：替换所有 Kotlin 文件的 package 声明**

Run:
```bash
find build-logic/src -name "*.kt" -exec sed -i '' 's|^package com\.patra\.buildlogic|package dev.linqibin.patra.buildlogic|g' {} +
```

验证：
```bash
grep -rn "^package " build-logic/src/main/kotlin/ | grep -v "dev.linqibin.patra.buildlogic" | head
```

Expected：空输出（所有 Kotlin 文件都已是新包名）。

- [ ] **Step 4：替换 Kotlin 文件中的 import 引用**

Run:
```bash
find build-logic/src -name "*.kt" -exec sed -i '' 's|import com\.patra\.buildlogic|import dev.linqibin.patra.buildlogic|g' {} +
```

- [ ] **Step 5：替换 plugin ID 定义**

找到 plugin ID 声明文件（可能是 build-logic/build.gradle.kts 或专门的 gradlePlugin 配置）：

```bash
grep -rn 'id = "patra\.hexagonal' build-logic/
```

对每个匹配文件做替换：
```bash
find build-logic -name "*.gradle.kts" -exec sed -i '' 's|"patra\.hexagonal-|"linqibin.hexagonal-|g' {} +
```

验证：
```bash
grep -rn "patra\.hexagonal" build-logic/ | head
```

Expected：空输出。

- [ ] **Step 6：单独构建 build-logic 验证**

Run:
```bash
./gradlew :build-logic:assemble --no-daemon
```

Expected：`BUILD SUCCESSFUL`。

注：此时主项目编译仍然会失败（因为 21 个 module 的 build.gradle.kts 仍然引用旧的 patra.hexagonal-* plugin ID）— Task 1.4 修复。

- [ ] **Step 7：Commit**

```bash
git add build-logic
git commit -m "$(cat <<'EOF'
refactor(rename): Phase 1.3 build-logic Kotlin 包 + Plugin ID 改名

- Kotlin 包：com.patra.buildlogic.* → dev.linqibin.patra.buildlogic.*
- Plugin IDs：patra.hexagonal-* → linqibin.hexagonal-*

主项目模块在 Task 1.4 同步更新 plugin id 引用。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.4：更新 21 模块的 build.gradle.kts plugin ID 引用

**Files:**
- Modify: 全项目所有 `build.gradle.kts` 中含 `id("patra.hexagonal-*")` 的行

- [ ] **Step 1：列出受影响的文件**

Run:
```bash
grep -rln 'id("patra\.hexagonal' --include="*.gradle.kts" .
```

Expected：列出 ~21 个模块的 build.gradle.kts。记下数量。

- [ ] **Step 2：批量替换**

Run:
```bash
find . -name "build.gradle.kts" -not -path './build/*' -not -path './.gradle/*' \
  -exec sed -i '' 's|id("patra\.hexagonal-|id("linqibin.hexagonal-|g' {} +
```

- [ ] **Step 3：验证零残留**

Run:
```bash
grep -rn 'patra\.hexagonal' --include="*.gradle.kts" . | head
```

Expected：空输出。

- [ ] **Step 4：尝试 `./gradlew projects` 验证 plugin 解析**

Run:
```bash
./gradlew projects --no-daemon
```

Expected：列出所有 module。可能会因为 Task 1.5 中 settings.gradle.kts 的 include 路径过时而部分失败 — 本 step 重点是看 plugin 是否被找到（错误是 "project not found" 而非 "plugin not found"）。

- [ ] **Step 5：Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(rename): Phase 1.4 更新 21 模块 build.gradle.kts 中的 plugin id 引用 linqibin.hexagonal-*

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.5：settings.gradle.kts + gradle.properties 同步

**Files:**
- Modify: `settings.gradle.kts`（include 路径更新 + 新模块注册）
- Modify: `gradle.properties`（新增 commonsGroup）

- [ ] **Step 1：查看当前 settings.gradle.kts**

Run:
```bash
cat settings.gradle.kts
```

记下：
- 11 个通用 starter 当前的 include 路径（应为 `patra-spring-boot-starter-{X}`）
- 是否有其他配置（pluginManagement、dependencyResolutionManagement 等）

- [ ] **Step 2：替换 11 个通用 starter 的 include**

Run:
```bash
sed -i '' \
  -e 's|patra-spring-boot-starter-core|linqibin-spring-boot-starter-core|g' \
  -e 's|patra-spring-boot-starter-web|linqibin-spring-boot-starter-web|g' \
  -e 's|patra-spring-boot-starter-jpa|linqibin-spring-boot-starter-jpa|g' \
  -e 's|patra-spring-boot-starter-batch|linqibin-spring-boot-starter-batch|g' \
  -e 's|patra-spring-boot-starter-rest-client|linqibin-spring-boot-starter-rest-client|g' \
  -e 's|patra-spring-boot-starter-object-storage|linqibin-spring-boot-starter-object-storage|g' \
  -e 's|patra-spring-boot-starter-observability|linqibin-spring-boot-starter-observability|g' \
  -e 's|patra-spring-boot-starter-redisson|linqibin-spring-boot-starter-redisson|g' \
  -e 's|patra-spring-boot-starter-test|linqibin-spring-boot-starter-test|g' \
  -e 's|patra-spring-boot-starter-http-interface|linqibin-spring-boot-starter-http-interface|g' \
  -e 's|patra-spring-boot-starter-openapi|linqibin-spring-boot-starter-openapi|g' \
  settings.gradle.kts
```

- [ ] **Step 3：注册 3 个新模块**

编辑 `settings.gradle.kts`，在合适位置加入：

```kotlin
include("linqibin-commons-core")
include("linqibin-commons-storage")
include("patra-common-enums")
```

（位置应该跟 `linqibin-spring-boot-starter-*` 等通用模块一致。具体位置看现有 settings.gradle.kts 的组织方式。）

- [ ] **Step 4：gradle.properties 新增 commonsGroup**

查看当前 properties：
```bash
cat gradle.properties
```

在 `patraGroup=com.patra` 下面新增一行：
```
commonsGroup=dev.linqibin.commons
```

Run（用 Edit 工具或手动编辑）：
```bash
# 注：可以用 sed 在 patraGroup 后插入，或手动编辑
echo "" >> gradle.properties
echo "# Phase 1.5: 通用模块（linqibin-commons-* + linqibin-spring-boot-starter-*）的 group" >> gradle.properties
echo "commonsGroup=dev.linqibin.commons" >> gradle.properties
```

- [ ] **Step 5：验证 ./gradlew projects 列出 24 个模块**

Run:
```bash
./gradlew projects --no-daemon 2>&1 | tee /tmp/projects.log
```

Expected：
- BUILD SUCCESSFUL
- 列表中含 `:linqibin-commons-core`、`:linqibin-commons-storage`、`:patra-common-enums`、`:linqibin-spring-boot-starter-{X}` 共 14 个新名字
- 不含 `:patra-spring-boot-starter-core/web/jpa/...` 等 11 个旧名字

- [ ] **Step 6：跑全量 compile 验证**

Run:
```bash
./gradlew compileJava --no-daemon
```

Expected：`BUILD SUCCESSFUL`。源码包名仍然是 com.patra（Phase 2 后才改），只是模块结构调整了。如果失败，检查 settings.gradle.kts 是否漏改某个 starter 名。

- [ ] **Step 7：Commit + Phase 1 验证 gate**

```bash
git add settings.gradle.kts gradle.properties
git commit -m "$(cat <<'EOF'
refactor(rename): Phase 1.5 settings.gradle.kts + gradle.properties 同步 — Phase 1 完成

- 11 个通用 starter include 路径改名 linqibin-*
- 注册 3 个新模块 linqibin-commons-{core,storage} + patra-common-enums
- gradle.properties 新增 commonsGroup=dev.linqibin.commons

Phase 1 验证 gate：./gradlew compileJava BUILD SUCCESSFUL（源码仍是 com.patra）。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2：patra-common-* 内容拆分

**目标**：把 26 个通用类从 patra-common-core 迁到 linqibin-commons-core；4 个业务枚举迁到 patra-common-enums；patra-common-storage 整体迁到 linqibin-commons-storage；剩余 patra-common-{model,provenance-api} 重命名包；删除空的 patra-common-{core,storage} 模块。

每个 Task 内采用"复制 + 改 import + 验证 + 删旧"四步微步骤。

### Task 2.1：迁移 26 个通用类 patra-common-core → linqibin-commons-core

**Files:**
- Git-mv 目录：
  - `patra-common-core/src/main/java/com/patra/common/cqrs/` → `linqibin-commons-core/src/main/java/dev/linqibin/commons/cqrs/`
  - `patra-common-core/src/main/java/com/patra/common/domain/` → `linqibin-commons-core/src/main/java/dev/linqibin/commons/domain/`
  - `patra-common-core/src/main/java/com/patra/common/error/` → `linqibin-commons-core/src/main/java/dev/linqibin/commons/error/`
  - `patra-common-core/src/main/java/com/patra/common/json/` → `linqibin-commons-core/src/main/java/dev/linqibin/commons/json/`
  - `patra-common-core/src/main/java/com/patra/common/query/` → `linqibin-commons-core/src/main/java/dev/linqibin/commons/query/`
  - `patra-common-core/src/main/java/com/patra/common/messaging/` → `linqibin-commons-core/src/main/java/dev/linqibin/commons/messaging/`
  - `patra-common-core/src/main/java/com/patra/common/type/` → `linqibin-commons-core/src/main/java/dev/linqibin/commons/type/`
  - `patra-common-core/src/main/java/com/patra/common/util/` → `linqibin-commons-core/src/main/java/dev/linqibin/commons/util/`
- 单独迁移 Priority.java、SortDirection.java（在 `patra-common-core/src/main/java/com/patra/common/enums/` 下，但只迁这两个通用枚举）

- [ ] **Step 1：确认 patra-common-core 当前内容**

Run:
```bash
find patra-common-core/src/main/java/com/patra/common -type f -name "*.java" | sort
```

确认看到的类与 spec Section 4.1 的列表一致（26 通用 + 4 业务枚举）。

- [ ] **Step 2：git mv 8 个通用子包**

Run:
```bash
for pkg in cqrs domain error json query messaging type util; do
  src="patra-common-core/src/main/java/com/patra/common/$pkg"
  dst="linqibin-commons-core/src/main/java/dev/linqibin/commons/$pkg"
  if [ -d "$src" ]; then
    mkdir -p "$(dirname $dst)"
    git mv "$src" "$dst"
  fi
done
```

- [ ] **Step 3：单独迁移 Priority + SortDirection（通用枚举）**

注：`patra-common-core/src/main/java/com/patra/common/enums/` 整个目录里既有通用枚举（Priority、SortDirection）也有业务枚举（ProvenanceCode、IngestDateType、RegistryConfigScope、DataType），所以不能整目录 mv。

Run:
```bash
mkdir -p linqibin-commons-core/src/main/java/dev/linqibin/commons/enums
git mv patra-common-core/src/main/java/com/patra/common/enums/Priority.java \
       linqibin-commons-core/src/main/java/dev/linqibin/commons/enums/Priority.java
git mv patra-common-core/src/main/java/com/patra/common/enums/SortDirection.java \
       linqibin-commons-core/src/main/java/dev/linqibin/commons/enums/SortDirection.java
```

- [ ] **Step 4：批量替换迁移文件的 package 声明**

Run:
```bash
find linqibin-commons-core/src/main/java/dev/linqibin/commons -name "*.java" -exec sed -i '' \
  -e 's|^package com\.patra\.common\.cqrs|package dev.linqibin.commons.cqrs|g' \
  -e 's|^package com\.patra\.common\.domain|package dev.linqibin.commons.domain|g' \
  -e 's|^package com\.patra\.common\.error|package dev.linqibin.commons.error|g' \
  -e 's|^package com\.patra\.common\.json|package dev.linqibin.commons.json|g' \
  -e 's|^package com\.patra\.common\.query|package dev.linqibin.commons.query|g' \
  -e 's|^package com\.patra\.common\.messaging|package dev.linqibin.commons.messaging|g' \
  -e 's|^package com\.patra\.common\.type|package dev.linqibin.commons.type|g' \
  -e 's|^package com\.patra\.common\.util|package dev.linqibin.commons.util|g' \
  -e 's|^package com\.patra\.common\.enums|package dev.linqibin.commons.enums|g' \
  {} +
```

验证：
```bash
grep -rn "^package " linqibin-commons-core/src/main/java/ | grep -v "dev\.linqibin\.commons" | head
```

Expected：空输出。

- [ ] **Step 5：全局批量替换 import**

Run（关键 step — 影响所有 consumer 模块）：

```bash
find . -name "*.java" -not -path "./build/*" -not -path "./.gradle/*" -not -path "./out/*" -exec sed -i '' \
  -e 's|import com\.patra\.common\.cqrs\.|import dev.linqibin.commons.cqrs.|g' \
  -e 's|import com\.patra\.common\.domain\.|import dev.linqibin.commons.domain.|g' \
  -e 's|import com\.patra\.common\.error\.|import dev.linqibin.commons.error.|g' \
  -e 's|import com\.patra\.common\.json\.|import dev.linqibin.commons.json.|g' \
  -e 's|import com\.patra\.common\.query\.|import dev.linqibin.commons.query.|g' \
  -e 's|import com\.patra\.common\.messaging\.|import dev.linqibin.commons.messaging.|g' \
  -e 's|import com\.patra\.common\.type\.|import dev.linqibin.commons.type.|g' \
  -e 's|import com\.patra\.common\.util\.|import dev.linqibin.commons.util.|g' \
  -e 's|import com\.patra\.common\.enums\.Priority|import dev.linqibin.commons.enums.Priority|g' \
  -e 's|import com\.patra\.common\.enums\.SortDirection|import dev.linqibin.commons.enums.SortDirection|g' \
  {} +
```

- [ ] **Step 6：更新所有依赖 patra-common-core 的模块的 build.gradle.kts，把通用类需求改为 linqibin-commons-core**

Run：
```bash
grep -rln 'project(":patra-common-core")' --include="*.gradle.kts" .
```

对每个匹配文件：检查它实际使用的是通用部分还是业务枚举。如果用通用部分，加上：
```kotlin
implementation(project(":linqibin-commons-core"))
```

注：暂时**保留** `implementation(project(":patra-common-core"))` 不删除，因为 patra-common-core 里还有 4 个业务枚举（在 Task 2.2 才迁出）。Task 2.4 删除模块时再清理引用。

- [ ] **Step 7：编译验证**

Run:
```bash
./gradlew :linqibin-commons-core:compileJava --no-daemon
./gradlew compileJava --no-daemon
```

Expected：两条都 `BUILD SUCCESSFUL`。

如果失败：检查报错的 Java 文件，可能是 import 替换漏了边角；或者 dependency 缺失（某个模块用了 dev.linqibin.commons.* 但 build.gradle.kts 没声明 linqibin-commons-core 依赖）。

- [ ] **Step 8：grep 验证零残留**

Run:
```bash
grep -rn "com\.patra\.common\.cqrs" --include="*.java" . | head
grep -rn "com\.patra\.common\.domain" --include="*.java" . | head
grep -rn "com\.patra\.common\.error" --include="*.java" . | head
grep -rn "com\.patra\.common\.json" --include="*.java" . | head
grep -rn "com\.patra\.common\.query" --include="*.java" . | head
grep -rn "com\.patra\.common\.messaging" --include="*.java" . | head
grep -rn "com\.patra\.common\.type" --include="*.java" . | head
grep -rn "com\.patra\.common\.util" --include="*.java" . | head
```

Expected：每条都空输出。

- [ ] **Step 9：Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(rename): Phase 2.1 迁移 26 通用类 patra-common-core → linqibin-commons-core

包含：cqrs、domain、error、json、query、messaging、type、util 8 个子包
加 enums.Priority + enums.SortDirection 2 个通用枚举。
patra-common-core 中剩余 4 个业务枚举待 Task 2.2 迁移。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.2：迁移 4 个业务枚举 patra-common-core → patra-common-enums

**Files:**
- Git-mv：
  - `patra-common-core/src/main/java/com/patra/common/enums/ProvenanceCode.java` → `patra-common-enums/src/main/java/dev/linqibin/patra/common/enums/ProvenanceCode.java`
  - `IngestDateType.java`、`RegistryConfigScope.java`、`DataType.java` 同样位置

- [ ] **Step 1：确认 patra-common-core 仍然存在的枚举**

Run:
```bash
ls patra-common-core/src/main/java/com/patra/common/enums/
```

Expected：4 个 .java 文件（ProvenanceCode、IngestDateType、RegistryConfigScope、DataType）。

- [ ] **Step 2：git mv 4 个枚举**

Run:
```bash
for enum in ProvenanceCode IngestDateType RegistryConfigScope DataType; do
  src="patra-common-core/src/main/java/com/patra/common/enums/${enum}.java"
  dst="patra-common-enums/src/main/java/dev/linqibin/patra/common/enums/${enum}.java"
  git mv "$src" "$dst"
done
```

- [ ] **Step 3：替换 package 声明**

Run:
```bash
find patra-common-enums/src/main/java -name "*.java" -exec sed -i '' \
  's|^package com\.patra\.common\.enums|package dev.linqibin.patra.common.enums|g' {} +
```

- [ ] **Step 4：全局批量替换 import**

Run:
```bash
find . -name "*.java" -not -path "./build/*" -not -path "./.gradle/*" -exec sed -i '' \
  -e 's|import com\.patra\.common\.enums\.ProvenanceCode|import dev.linqibin.patra.common.enums.ProvenanceCode|g' \
  -e 's|import com\.patra\.common\.enums\.IngestDateType|import dev.linqibin.patra.common.enums.IngestDateType|g' \
  -e 's|import com\.patra\.common\.enums\.RegistryConfigScope|import dev.linqibin.patra.common.enums.RegistryConfigScope|g' \
  -e 's|import com\.patra\.common\.enums\.DataType|import dev.linqibin.patra.common.enums.DataType|g' \
  {} +
```

- [ ] **Step 5：更新 build.gradle.kts 加 patra-common-enums 依赖**

Run:
```bash
grep -rln 'ProvenanceCode\|IngestDateType\|RegistryConfigScope\|DataType' --include="*.java" . | xargs -I {} dirname {} | sort -u | head -20
```

找出使用方所在模块，确认其 build.gradle.kts 含 `implementation(project(":patra-common-enums"))`。如缺，手动添加。

- [ ] **Step 6：编译验证**

Run:
```bash
./gradlew :patra-common-enums:compileJava --no-daemon
./gradlew compileJava --no-daemon
```

Expected：BUILD SUCCESSFUL。

- [ ] **Step 7：grep 验证**

Run:
```bash
grep -rn "com\.patra\.common\.enums" --include="*.java" . | head
```

Expected：空输出。

- [ ] **Step 8：Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(rename): Phase 2.2 迁移 4 业务枚举 patra-common-core → patra-common-enums

ProvenanceCode、IngestDateType、RegistryConfigScope、DataType 迁到
dev.linqibin.patra.common.enums.*。patra-common-core 此时应为空。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.3：迁移 patra-common-storage → linqibin-commons-storage

**Files:**
- Git-mv：`patra-common-storage/src/main/java/com/patra/common/storage/` → `linqibin-commons-storage/src/main/java/dev/linqibin/commons/storage/`

- [ ] **Step 1：检查 patra-common-storage 现有内容**

Run:
```bash
find patra-common-storage/src/main/java -name "*.java"
```

Expected：4 个文件（ObjectKeyGenerator、DatePartitionedKeyGenerator、ObjectKeyContext、ObjectKeyTemplate）。

- [ ] **Step 2：git mv 整个目录**

Run:
```bash
# linqibin-commons-storage/src/main/java/dev/linqibin/commons 目录已在 Task 1.1 创建
git mv patra-common-storage/src/main/java/com/patra/common/storage \
       linqibin-commons-storage/src/main/java/dev/linqibin/commons/storage

# 清理空残留目录
rmdir patra-common-storage/src/main/java/com/patra/common 2>/dev/null
rmdir patra-common-storage/src/main/java/com/patra 2>/dev/null
rmdir patra-common-storage/src/main/java/com 2>/dev/null
```

如果有 test 目录：
```bash
[ -d "patra-common-storage/src/test/java/com/patra/common/storage" ] && \
  git mv patra-common-storage/src/test/java/com/patra/common/storage \
         linqibin-commons-storage/src/test/java/dev/linqibin/commons/storage
```

- [ ] **Step 3：替换 package 声明**

Run:
```bash
find linqibin-commons-storage/src -name "*.java" -exec sed -i '' \
  's|^package com\.patra\.common\.storage|package dev.linqibin.commons.storage|g' {} +
```

- [ ] **Step 4：全局批量替换 import**

Run:
```bash
find . -name "*.java" -not -path "./build/*" -not -path "./.gradle/*" -exec sed -i '' \
  's|import com\.patra\.common\.storage\.|import dev.linqibin.commons.storage.|g' {} +
```

- [ ] **Step 5：更新使用方 build.gradle.kts 依赖**

Run:
```bash
grep -rln 'project(":patra-common-storage")' --include="*.gradle.kts" .
```

每个匹配文件中：把 `project(":patra-common-storage")` 改为 `project(":linqibin-commons-storage")`：

```bash
find . -name "build.gradle.kts" -not -path './build/*' -exec sed -i '' \
  's|project(":patra-common-storage")|project(":linqibin-commons-storage")|g' {} +
```

- [ ] **Step 6：编译验证**

Run:
```bash
./gradlew :linqibin-commons-storage:compileJava --no-daemon
./gradlew compileJava --no-daemon
```

Expected：BUILD SUCCESSFUL。

- [ ] **Step 7：Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(rename): Phase 2.3 迁移 patra-common-storage 内容 → linqibin-commons-storage

4 个对象存储工具类整体迁移，依赖坐标同步从 :patra-common-storage 改为 :linqibin-commons-storage。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.4：删除空的 patra-common-core + patra-common-storage 模块

**Files:**
- Delete: `patra-common-core/`（整个目录）
- Delete: `patra-common-storage/`（整个目录）
- Modify: `settings.gradle.kts`（移除 include）

- [ ] **Step 1：验证 patra-common-core src 目录已空**

Run:
```bash
find patra-common-core/src -name "*.java" 2>/dev/null
find patra-common-storage/src -name "*.java" 2>/dev/null
```

Expected：两条都空输出。

- [ ] **Step 2：从 settings.gradle.kts 移除 include**

编辑 `settings.gradle.kts`，删除：
```kotlin
include("patra-common-core")
include("patra-common-storage")
```

可用 sed：
```bash
sed -i '' '/include("patra-common-core")/d' settings.gradle.kts
sed -i '' '/include("patra-common-storage")/d' settings.gradle.kts
```

- [ ] **Step 3：git rm 模块目录**

Run:
```bash
git rm -r patra-common-core
git rm -r patra-common-storage
```

- [ ] **Step 4：移除使用方 build.gradle.kts 中残留的 patra-common-core 依赖**

Run:
```bash
grep -rln 'project(":patra-common-core")' --include="*.gradle.kts" .
```

对每个匹配文件：删除 `implementation(project(":patra-common-core"))` 这一行（如果 Task 2.1-2.2 后这些模块本来只需要 linqibin-commons-core 或 patra-common-enums）。具体每个模块如何替换需要看它实际使用：

- 用了 CQRS/error/JSON 等通用类的 → 加 `implementation(project(":linqibin-commons-core"))`
- 用了 ProvenanceCode 等业务枚举的 → 加 `implementation(project(":patra-common-enums"))`
- 不再使用 patra-common-core 任何类的 → 直接删 dependency

如果某个模块仍 import `com.patra.common.*` 任何东西，说明 Task 2.1/2.2 漏迁了，回头补。

- [ ] **Step 5：编译验证**

Run:
```bash
./gradlew compileJava --no-daemon
```

Expected：BUILD SUCCESSFUL。

- [ ] **Step 6：Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(rename): Phase 2.4 删除已掏空的 patra-common-core + patra-common-storage 模块

内容已分别迁移至 linqibin-commons-core / linqibin-commons-storage / patra-common-enums。
所有 :patra-common-core 依赖坐标在使用方 build.gradle.kts 中替换为相应新模块。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.5：重命名 patra-common-model + patra-common-provenance-api 包

**Files:**
- Git-mv：`patra-common-model/src/main/java/com/patra/common/model/` → `patra-common-model/src/main/java/dev/linqibin/patra/common/model/`
- Git-mv：`patra-common-provenance-api/src/main/java/com/patra/common/provenance/api/` → `patra-common-provenance-api/src/main/java/dev/linqibin/patra/common/provenance/api/`

- [ ] **Step 1：git mv patra-common-model 包目录**

Run:
```bash
mkdir -p patra-common-model/src/main/java/dev/linqibin/patra/common
git mv patra-common-model/src/main/java/com/patra/common/model \
       patra-common-model/src/main/java/dev/linqibin/patra/common/model

# 同 test 目录
[ -d "patra-common-model/src/test/java/com/patra/common/model" ] && {
  mkdir -p patra-common-model/src/test/java/dev/linqibin/patra/common
  git mv patra-common-model/src/test/java/com/patra/common/model \
         patra-common-model/src/test/java/dev/linqibin/patra/common/model
}

# 清理空残留
rmdir patra-common-model/src/main/java/com/patra/common 2>/dev/null
rmdir patra-common-model/src/main/java/com/patra 2>/dev/null
rmdir patra-common-model/src/main/java/com 2>/dev/null
```

- [ ] **Step 2：替换 patra-common-model 包声明**

Run:
```bash
find patra-common-model/src -name "*.java" -exec sed -i '' \
  -e 's|^package com\.patra\.common\.model|package dev.linqibin.patra.common.model|g' \
  {} +
```

- [ ] **Step 3：同样处理 patra-common-provenance-api**

Run:
```bash
mkdir -p patra-common-provenance-api/src/main/java/dev/linqibin/patra/common/provenance
git mv patra-common-provenance-api/src/main/java/com/patra/common/provenance/api \
       patra-common-provenance-api/src/main/java/dev/linqibin/patra/common/provenance/api

[ -d "patra-common-provenance-api/src/test/java/com/patra/common/provenance/api" ] && {
  mkdir -p patra-common-provenance-api/src/test/java/dev/linqibin/patra/common/provenance
  git mv patra-common-provenance-api/src/test/java/com/patra/common/provenance/api \
         patra-common-provenance-api/src/test/java/dev/linqibin/patra/common/provenance/api
}

rmdir patra-common-provenance-api/src/main/java/com/patra/common/provenance 2>/dev/null
rmdir patra-common-provenance-api/src/main/java/com/patra/common 2>/dev/null
rmdir patra-common-provenance-api/src/main/java/com/patra 2>/dev/null
rmdir patra-common-provenance-api/src/main/java/com 2>/dev/null

find patra-common-provenance-api/src -name "*.java" -exec sed -i '' \
  -e 's|^package com\.patra\.common\.provenance\.api|package dev.linqibin.patra.common.provenance.api|g' \
  {} +
```

- [ ] **Step 4：全局批量替换 import**

Run:
```bash
find . -name "*.java" -not -path "./build/*" -not -path "./.gradle/*" -exec sed -i '' \
  -e 's|import com\.patra\.common\.model\.|import dev.linqibin.patra.common.model.|g' \
  -e 's|import com\.patra\.common\.provenance\.api\.|import dev.linqibin.patra.common.provenance.api.|g' \
  {} +
```

- [ ] **Step 5：编译验证 + Phase 2 验证 gate**

Run:
```bash
./gradlew compileJava --no-daemon
```

Expected：全量 BUILD SUCCESSFUL。

Run（Phase 2 验证 gate，零残留检查）：
```bash
grep -rn "com\.patra\.common" --include="*.java" . | head
```

Expected：空输出（所有 com.patra.common.* 已迁移完毕）。

- [ ] **Step 6：Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(rename): Phase 2.5 重命名 patra-common-{model,provenance-api} 包 — Phase 2 完成

- patra-common-model：com.patra.common.model.* → dev.linqibin.patra.common.model.*
- patra-common-provenance-api：com.patra.common.provenance.api.* → dev.linqibin.patra.common.provenance.api.*

Phase 2 验证 gate：./gradlew compileJava BUILD SUCCESSFUL；
grep -rn "com\.patra\.common" --include="*.java" 零残留。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3：业务服务 + patra 专用 starter 包名 rename

**目标**：4 个业务服务（registry/ingest/catalog/gateway）+ 2 个 patra 专用 starter（provenance/expr）的 Java 包从 `com.patra.{...}` 改为 `dev.linqibin.patra.{...}`。

每个 task 处理一个服务/starter，模式相同（git mv 物理目录 + sed 改包名 + sed 改 import + 编译）。

### Task 3.1：rename patra-registry 包

**Files:**
- Git-mv：`patra-registry-*/src/{main,test}/java/com/patra/registry/` → `patra-registry-*/src/{main,test}/java/dev/linqibin/patra/registry/`

- [ ] **Step 1：列出受影响的子模块**

Run:
```bash
ls -d patra-registry/patra-registry-* 2>/dev/null
```

Expected：列出所有 patra-registry-{domain,app,infra,adapter,api,boot}（约 5-6 个子模块）。

- [ ] **Step 2：对每个子模块的 src/main/java/com/patra/registry 执行 git mv**

Run:
```bash
for moddir in patra-registry/patra-registry-*; do
  for src_type in main test; do
    src="${moddir}/src/${src_type}/java/com/patra/registry"
    dst="${moddir}/src/${src_type}/java/dev/linqibin/patra/registry"
    if [ -d "$src" ]; then
      mkdir -p "$(dirname $dst)"
      git mv "$src" "$dst"
    fi
  done
done
```

- [ ] **Step 3：清理空残留 com/patra/ 目录**

Run:
```bash
find patra-registry -type d -empty -path "*/com/patra*" -delete 2>/dev/null
find patra-registry -type d -empty -path "*/com" -delete 2>/dev/null
```

- [ ] **Step 4：替换 package 声明 + import**

Run:
```bash
find patra-registry -name "*.java" -exec sed -i '' \
  -e 's|^package com\.patra\.registry|package dev.linqibin.patra.registry|g' \
  -e 's|import com\.patra\.registry\.|import dev.linqibin.patra.registry.|g' \
  {} +
```

注意还需要处理**其他模块**对 patra-registry-api 的引用：

```bash
find . -name "*.java" -not -path "./patra-registry/*" -not -path "./build/*" -not -path "./.gradle/*" -exec sed -i '' \
  's|import com\.patra\.registry\.|import dev.linqibin.patra.registry.|g' \
  {} +
```

- [ ] **Step 5：编译验证**

Run:
```bash
./gradlew :patra-registry:patra-registry-domain:compileJava --no-daemon
./gradlew compileJava --no-daemon
```

Expected：BUILD SUCCESSFUL。

- [ ] **Step 6：grep 验证**

Run:
```bash
grep -rn "com\.patra\.registry" --include="*.java" . | head
```

Expected：空输出。

- [ ] **Step 7：Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(rename): Phase 3.1 patra-registry 包名 com.patra.registry → dev.linqibin.patra.registry

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.2：rename patra-ingest 包

模式同 Task 3.1，把 `com.patra.ingest` → `dev.linqibin.patra.ingest`。

- [ ] **Step 1：git mv 物理目录**

Run:
```bash
for moddir in patra-ingest/patra-ingest-*; do
  for src_type in main test; do
    src="${moddir}/src/${src_type}/java/com/patra/ingest"
    dst="${moddir}/src/${src_type}/java/dev/linqibin/patra/ingest"
    if [ -d "$src" ]; then
      mkdir -p "$(dirname $dst)"
      git mv "$src" "$dst"
    fi
  done
done
find patra-ingest -type d -empty -path "*/com/patra*" -delete 2>/dev/null
find patra-ingest -type d -empty -path "*/com" -delete 2>/dev/null
```

- [ ] **Step 2：替换 package + import（含跨模块引用）**

Run:
```bash
find patra-ingest -name "*.java" -exec sed -i '' \
  -e 's|^package com\.patra\.ingest|package dev.linqibin.patra.ingest|g' \
  -e 's|import com\.patra\.ingest\.|import dev.linqibin.patra.ingest.|g' \
  {} +

find . -name "*.java" -not -path "./patra-ingest/*" -not -path "./build/*" -not -path "./.gradle/*" -exec sed -i '' \
  's|import com\.patra\.ingest\.|import dev.linqibin.patra.ingest.|g' \
  {} +
```

- [ ] **Step 3：编译验证**

Run:
```bash
./gradlew compileJava --no-daemon
```

- [ ] **Step 4：grep 验证 + Commit**

Run:
```bash
grep -rn "com\.patra\.ingest" --include="*.java" . | head
git add -A
git commit -m "refactor(rename): Phase 3.2 patra-ingest 包名 → dev.linqibin.patra.ingest

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3.3：rename patra-catalog 包

模式同 Task 3.1，把 `com.patra.catalog` → `dev.linqibin.patra.catalog`。

- [ ] **Step 1-4：git mv + sed + 编译 + commit**

Run:
```bash
for moddir in patra-catalog/patra-catalog-*; do
  for src_type in main test; do
    src="${moddir}/src/${src_type}/java/com/patra/catalog"
    dst="${moddir}/src/${src_type}/java/dev/linqibin/patra/catalog"
    if [ -d "$src" ]; then
      mkdir -p "$(dirname $dst)"
      git mv "$src" "$dst"
    fi
  done
done
find patra-catalog -type d -empty -path "*/com/patra*" -delete 2>/dev/null
find patra-catalog -type d -empty -path "*/com" -delete 2>/dev/null

find patra-catalog -name "*.java" -exec sed -i '' \
  -e 's|^package com\.patra\.catalog|package dev.linqibin.patra.catalog|g' \
  -e 's|import com\.patra\.catalog\.|import dev.linqibin.patra.catalog.|g' \
  {} +

find . -name "*.java" -not -path "./patra-catalog/*" -not -path "./build/*" -not -path "./.gradle/*" -exec sed -i '' \
  's|import com\.patra\.catalog\.|import dev.linqibin.patra.catalog.|g' \
  {} +

./gradlew compileJava --no-daemon

grep -rn "com\.patra\.catalog" --include="*.java" . | head

git add -A
git commit -m "refactor(rename): Phase 3.3 patra-catalog 包名 → dev.linqibin.patra.catalog

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3.4：rename patra-gateway-boot 包

模式同上，但 gateway 只有一个 boot 模块（无子模块）。

- [ ] **Step 1-4：git mv + sed + 编译 + commit**

Run:
```bash
for src_type in main test; do
  src="patra-gateway-boot/src/${src_type}/java/com/patra/gateway"
  dst="patra-gateway-boot/src/${src_type}/java/dev/linqibin/patra/gateway"
  if [ -d "$src" ]; then
    mkdir -p "$(dirname $dst)"
    git mv "$src" "$dst"
  fi
done
find patra-gateway-boot -type d -empty -path "*/com/patra*" -delete 2>/dev/null
find patra-gateway-boot -type d -empty -path "*/com" -delete 2>/dev/null

find patra-gateway-boot -name "*.java" -exec sed -i '' \
  -e 's|^package com\.patra\.gateway|package dev.linqibin.patra.gateway|g' \
  -e 's|import com\.patra\.gateway\.|import dev.linqibin.patra.gateway.|g' \
  {} +

find . -name "*.java" -not -path "./patra-gateway-boot/*" -not -path "./build/*" -not -path "./.gradle/*" -exec sed -i '' \
  's|import com\.patra\.gateway\.|import dev.linqibin.patra.gateway.|g' \
  {} +

./gradlew compileJava --no-daemon

grep -rn "com\.patra\.gateway" --include="*.java" . | head

git add -A
git commit -m "refactor(rename): Phase 3.4 patra-gateway 包名 → dev.linqibin.patra.gateway

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3.5：rename patra-spring-boot-starter-{provenance,expr} 包

- [ ] **Step 1：处理 provenance starter**

Run:
```bash
for src_type in main test; do
  src="patra-spring-boot-starter-provenance/src/${src_type}/java/com/patra/starter/provenance"
  dst="patra-spring-boot-starter-provenance/src/${src_type}/java/dev/linqibin/patra/starter/provenance"
  if [ -d "$src" ]; then
    mkdir -p "$(dirname $dst)"
    git mv "$src" "$dst"
  fi
done

find patra-spring-boot-starter-provenance -name "*.java" -exec sed -i '' \
  -e 's|^package com\.patra\.starter\.provenance|package dev.linqibin.patra.starter.provenance|g' \
  -e 's|import com\.patra\.starter\.provenance\.|import dev.linqibin.patra.starter.provenance.|g' \
  {} +

find . -name "*.java" -not -path "./patra-spring-boot-starter-provenance/*" -not -path "./build/*" -not -path "./.gradle/*" -exec sed -i '' \
  's|import com\.patra\.starter\.provenance\.|import dev.linqibin.patra.starter.provenance.|g' \
  {} +
```

- [ ] **Step 2：处理 expr starter**

Run:
```bash
for src_type in main test; do
  src="patra-spring-boot-starter-expr/src/${src_type}/java/com/patra/starter/expr"
  dst="patra-spring-boot-starter-expr/src/${src_type}/java/dev/linqibin/patra/starter/expr"
  if [ -d "$src" ]; then
    mkdir -p "$(dirname $dst)"
    git mv "$src" "$dst"
  fi
done

find patra-spring-boot-starter-expr -name "*.java" -exec sed -i '' \
  -e 's|^package com\.patra\.starter\.expr|package dev.linqibin.patra.starter.expr|g' \
  -e 's|import com\.patra\.starter\.expr\.|import dev.linqibin.patra.starter.expr.|g' \
  {} +

find . -name "*.java" -not -path "./patra-spring-boot-starter-expr/*" -not -path "./build/*" -not -path "./.gradle/*" -exec sed -i '' \
  's|import com\.patra\.starter\.expr\.|import dev.linqibin.patra.starter.expr.|g' \
  {} +
```

- [ ] **Step 3：清理空残留目录**

Run:
```bash
for module in patra-spring-boot-starter-provenance patra-spring-boot-starter-expr; do
  find "$module" -type d -empty -path "*/com/patra*" -delete 2>/dev/null
  find "$module" -type d -empty -path "*/com" -delete 2>/dev/null
done
```

- [ ] **Step 4：编译验证 + grep + Commit**

Run:
```bash
./gradlew compileJava --no-daemon
grep -rn "com\.patra\.starter\.\(provenance\|expr\)" --include="*.java" . | head

git add -A
git commit -m "refactor(rename): Phase 3.5 patra-spring-boot-starter-{provenance,expr} 包名 → dev.linqibin.patra.starter.*

Phase 3 验证 gate：./gradlew compileJava BUILD SUCCESSFUL；
业务侧 com.patra.{registry,ingest,catalog,gateway} + com.patra.starter.{provenance,expr} 零残留。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

Phase 3 完成。`grep -rn "com\.patra\." --include="*.java"` 应该只剩 11 个通用 starter 的残留。

---

## Phase 4：通用 starter 包名 + artifact ID rename

**目标**：11 个通用 starter（`linqibin-spring-boot-starter-{name}`，Task 1.2 已改目录名）的包名从 `com.patra.starter.{name}` 改为 `dev.linqibin.starter.{name}`，并把所有 dependency 坐标、AutoConfiguration.imports、boot mainClass 同步更新。

### Task 4.1：11 通用 starter 包名 rename

**Files:**
- 11 个 `linqibin-spring-boot-starter-{name}` 模块的 src 物理目录与 .java 源

- [ ] **Step 1：执行 11 次目录 git mv + sed**

Run:
```bash
for name in core web jpa batch rest-client object-storage observability redisson test http-interface openapi; do
  module="linqibin-spring-boot-starter-$name"
  pkg_path=$(echo "$name" | tr '-' '_')  # rest-client → rest_client
  # 实际：Java 包名通常用驼峰或全小写，看现有结构
  # 假设当前结构是 com/patra/starter/{name-no-dash} 或 com/patra/starter/{name with subpkg}
  # 用 find 自动发现：
  src=$(find "$module/src" -type d -path "*/com/patra/starter/*" | grep -v "/test/" | head -1)
  if [ -n "$src" ]; then
    rel_pkg=$(echo "$src" | sed 's|.*/com/patra/starter/||')
    dst="$module/src/main/java/dev/linqibin/starter/$rel_pkg"
    mkdir -p "$(dirname $dst)"
    git mv "$src" "$dst"
  fi
  # test 目录同样处理
  src_test=$(find "$module/src" -type d -path "*/com/patra/starter/*" 2>/dev/null | head -1)
  if [ -n "$src_test" ]; then
    rel_pkg=$(echo "$src_test" | sed 's|.*/com/patra/starter/||')
    dst_test="$module/src/test/java/dev/linqibin/starter/$rel_pkg"
    mkdir -p "$(dirname $dst_test)"
    git mv "$src_test" "$dst_test" 2>/dev/null
  fi
done

# 清理空残留
find linqibin-spring-boot-starter-* -type d -empty -path "*/com/patra*" -delete 2>/dev/null
find linqibin-spring-boot-starter-* -type d -empty -path "*/com" -delete 2>/dev/null
```

- [ ] **Step 2：批量替换 package 声明 + import**

Run:
```bash
find linqibin-spring-boot-starter-* -name "*.java" -exec sed -i '' \
  -e 's|^package com\.patra\.starter\.|package dev.linqibin.starter.|g' \
  -e 's|import com\.patra\.starter\.|import dev.linqibin.starter.|g' \
  {} +

# 全局更新其他模块对这些 starter 的 import
find . -name "*.java" -not -path "./build/*" -not -path "./.gradle/*" -exec sed -i '' \
  's|import com\.patra\.starter\.\(core\|web\|jpa\|batch\|restclient\|objectstorage\|observability\|redisson\|test\|httpinterface\|openapi\)|import dev.linqibin.starter.\1|g' \
  {} +
```

注：具体子包名（`com.patra.starter.X` 中的 X）需要根据实际包结构调整。可能不是简单的 `core/web/jpa` — 比如 `restclient` 而非 `rest-client`（Java 包名不能含 dash）。先用 `find` 看实际结构再决定 sed 模式。

- [ ] **Step 3：编译验证**

Run:
```bash
./gradlew compileJava --no-daemon
```

- [ ] **Step 4：Commit**

Run:
```bash
git add -A
git commit -m "refactor(rename): Phase 4.1 11 通用 starter 包名 com.patra.starter.* → dev.linqibin.starter.*

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4.2：更新 AutoConfiguration.imports + 内部 dependency 坐标

**Files:**
- 51 个 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件
- 多个 `build.gradle.kts` 中的 `project(":patra-spring-boot-starter-*")` 引用

- [ ] **Step 1：批量更新 AutoConfiguration.imports**

Run:
```bash
find . -name "AutoConfiguration.imports" -not -path "./build/*" -exec sed -i '' \
  's|^com\.patra\.starter\.|dev.linqibin.starter.|g' \
  {} +

# 验证零残留
grep -rn "com\.patra\.starter" --include="*.imports" . | head
```

Expected：grep 输出空。

- [ ] **Step 2：批量更新 build.gradle.kts 中的 project 坐标**

Run:
```bash
for name in core web jpa batch rest-client object-storage observability redisson test http-interface openapi; do
  find . -name "build.gradle.kts" -not -path './build/*' -exec sed -i '' \
    "s|project(\":patra-spring-boot-starter-${name}\")|project(\":linqibin-spring-boot-starter-${name}\")|g" \
    {} +
done

# 验证零残留
grep -rn 'project(":patra-spring-boot-starter-' --include="*.gradle.kts" . | grep -vE 'provenance|expr' | head
```

Expected：grep 输出空（保留 provenance + expr 引用）。

- [ ] **Step 3：编译验证**

Run:
```bash
./gradlew compileJava --no-daemon
```

- [ ] **Step 4：Commit**

```bash
git add -A
git commit -m "refactor(rename): Phase 4.2 AutoConfiguration.imports + 内部 dependency 坐标更新 linqibin-spring-boot-starter-*

51 个 .imports 文件中的 FQN 与所有 :patra-spring-boot-starter-X (除 provenance/expr) 坐标同步更新。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4.3：更新 5 个 boot 的 mainClass 配置

**Files:**
- `patra-registry/patra-registry-boot/build.gradle.kts`
- `patra-ingest/patra-ingest-boot/build.gradle.kts`
- `patra-catalog/patra-catalog-boot/build.gradle.kts`
- `patra-gateway-boot/build.gradle.kts`
- `patra-object-storage-boot/build.gradle.kts`（如有）

- [ ] **Step 1：列出受影响文件**

Run:
```bash
grep -rln 'mainClass' --include="*.gradle.kts" .
```

- [ ] **Step 2：批量替换 mainClass 字符串**

Run:
```bash
find . -name "build.gradle.kts" -not -path './build/*' -exec sed -i '' \
  -e 's|"com\.patra\.registry\.|"dev.linqibin.patra.registry.|g' \
  -e 's|"com\.patra\.ingest\.|"dev.linqibin.patra.ingest.|g' \
  -e 's|"com\.patra\.catalog\.|"dev.linqibin.patra.catalog.|g' \
  -e 's|"com\.patra\.gateway\.|"dev.linqibin.patra.gateway.|g' \
  -e 's|"com\.patra\.objectstorage\.|"dev.linqibin.patra.objectstorage.|g' \
  {} +
```

- [ ] **Step 3：验证零残留**

Run:
```bash
grep -rn '"com\.patra\.' --include="*.gradle.kts" . | head
```

- [ ] **Step 4：dry-run 启动验证（每个 boot 至少一次）**

Run:
```bash
for boot in patra-registry-boot patra-ingest-boot patra-catalog-boot patra-gateway-boot; do
  echo "=== $boot ==="
  ./gradlew :${boot}:bootRun --dry-run --no-daemon 2>&1 | grep -E "(Main|mainClass)" | head
done
```

注：`--dry-run` 标志在 Gradle bootRun 上行为可能不同 — 实际验证方式可能是 `./gradlew :X:bootJar` 然后 inspect jar 的 MANIFEST.MF。或者直接 `bootJar` 看是否能打成包：

```bash
./gradlew :patra-catalog-boot:bootJar --no-daemon
```

- [ ] **Step 5：Phase 4 验证 gate + Commit**

```bash
./gradlew compileJava --no-daemon

# 全局 com.patra.starter 在 .imports 和 .java 中应为零残留
grep -rn "com\.patra\.starter" --include="*.java" --include="*.imports" . | head

git add -A
git commit -m "refactor(rename): Phase 4.3 5 boot mainClass 更新 — Phase 4 完成

Phase 4 验证 gate：./gradlew compileJava BUILD SUCCESSFUL；
com.patra.starter.* 在 .java 与 .imports 中零残留。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 5：全局配置 + 最终验证

**目标**：处理 yml/xml/md/imports/.run 配置文件中的 `com.patra` 引用；调整 scanBasePackages；最终验证全量 build + 4 boot 启动。

### Task 5.1：application.yml 配置更新

**Files:**
- 6 个 `application*.yml` 文件（位于各 boot 模块 `src/main/resources/`）

- [ ] **Step 1：列出受影响文件**

Run:
```bash
grep -rln "com\.patra" --include="application*.yml" --include="application*.yaml" .
```

- [ ] **Step 2：批量替换 logger 配置 + FQN**

Run:
```bash
find . \( -name "application*.yml" -o -name "application*.yaml" \) -not -path "./build/*" -exec sed -i '' \
  -e 's|com\.patra\.registry\.|dev.linqibin.patra.registry.|g' \
  -e 's|com\.patra\.ingest\.|dev.linqibin.patra.ingest.|g' \
  -e 's|com\.patra\.catalog\.|dev.linqibin.patra.catalog.|g' \
  -e 's|com\.patra\.gateway\.|dev.linqibin.patra.gateway.|g' \
  -e 's|com\.patra\.starter\.|dev.linqibin.starter.|g' \
  -e 's|com\.patra\.common\.|dev.linqibin.commons.|g' \
  -e 's|com\.patra\b|dev.linqibin|g' \
  {} +
```

注：最后一条 `com\.patra\b` 用 word boundary 兜底任何没被前面分支匹配的 `com.patra` 单独出现（如 `logging.level.com.patra: DEBUG`）。

- [ ] **Step 3：grep 验证**

Run:
```bash
grep -rn "com\.patra" --include="application*.yml" --include="application*.yaml" . | head
```

Expected：空输出。

- [ ] **Step 4：Commit**

```bash
git add -A
git commit -m "refactor(rename): Phase 5.1 application*.yml com.patra → dev.linqibin

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5.2：logback.xml 更新

**Files:**
- ~80 个 `logback*.xml` 文件

- [ ] **Step 1：列出受影响文件**

Run:
```bash
grep -rln "com\.patra" --include="logback*.xml" . | wc -l
```

- [ ] **Step 2：批量替换 logger name**

Run:
```bash
find . -name "logback*.xml" -not -path "./build/*" -exec sed -i '' \
  -e 's|com\.patra\.registry|dev.linqibin.patra.registry|g' \
  -e 's|com\.patra\.ingest|dev.linqibin.patra.ingest|g' \
  -e 's|com\.patra\.catalog|dev.linqibin.patra.catalog|g' \
  -e 's|com\.patra\.gateway|dev.linqibin.patra.gateway|g' \
  -e 's|com\.patra\.starter|dev.linqibin.starter|g' \
  -e 's|com\.patra\.common|dev.linqibin.commons|g' \
  -e 's|com\.patra|dev.linqibin|g' \
  {} +
```

注：最后一条兜底 `<logger name="com.patra"/>` 这种通配根。

- [ ] **Step 3：验证 + Commit**

Run:
```bash
grep -rn "com\.patra" --include="logback*.xml" . | head

git add -A
git commit -m "refactor(rename): Phase 5.2 logback*.xml logger name com.patra → dev.linqibin

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5.3：.md 文档 + .run/*.xml IDE 配置更新

**Files:**
- 16 个 `.md` 文档（README、CLAUDE.md、SKILL.md 等）
- 10 个 `.run/*.xml` IDE Run Config

- [ ] **Step 1：列出 .md 文件**

Run:
```bash
grep -rln "com\.patra" --include="*.md" . | head -20
```

- [ ] **Step 2：批量替换 .md**

Run:
```bash
find . -name "*.md" -not -path "./build/*" -not -path "./.gradle/*" -exec sed -i '' \
  -e 's|com\.patra\.registry|dev.linqibin.patra.registry|g' \
  -e 's|com\.patra\.ingest|dev.linqibin.patra.ingest|g' \
  -e 's|com\.patra\.catalog|dev.linqibin.patra.catalog|g' \
  -e 's|com\.patra\.gateway|dev.linqibin.patra.gateway|g' \
  -e 's|com\.patra\.starter|dev.linqibin.starter|g' \
  -e 's|com\.patra\.common|dev.linqibin.commons|g' \
  -e 's|com\.patra|dev.linqibin|g' \
  -e 's|patra-spring-boot-starter-\(core\|web\|jpa\|batch\|rest-client\|object-storage\|observability\|redisson\|test\|http-interface\|openapi\)|linqibin-spring-boot-starter-\1|g' \
  -e 's|patra-common-core|linqibin-commons-core|g' \
  -e 's|patra-common-storage|linqibin-commons-storage|g' \
  {} +
```

注：.md 中可能有 agent 工作指引提到具体 artifact ID，所以也要替换。

- [ ] **Step 3：批量替换 .run/*.xml**

Run:
```bash
find .run -name "*.xml" -exec sed -i '' \
  -e 's|com\.patra\.registry|dev.linqibin.patra.registry|g' \
  -e 's|com\.patra\.ingest|dev.linqibin.patra.ingest|g' \
  -e 's|com\.patra\.catalog|dev.linqibin.patra.catalog|g' \
  -e 's|com\.patra\.gateway|dev.linqibin.patra.gateway|g' \
  -e 's|com\.patra|dev.linqibin|g' \
  {} +
```

- [ ] **Step 4：验证 + Commit**

Run:
```bash
grep -rn "com\.patra" --include="*.md" . | head
grep -rn "com\.patra" --include="*.xml" .run/ | head

git add -A
git commit -m "refactor(rename): Phase 5.3 .md 文档 + .run IDE 配置同步 com.patra → dev.linqibin

包含 README、CLAUDE.md、SKILL.md 等 agent 工作指引中的 artifact ID
（patra-spring-boot-starter-* → linqibin-spring-boot-starter-*）。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5.4：scanBasePackages + gradle.properties patraGroup 更新

**Files:**
- 4-5 个 boot 服务的 `@SpringBootApplication` 注解类
- `gradle.properties`

- [ ] **Step 1：列出 @SpringBootApplication 类**

Run:
```bash
grep -rln '@SpringBootApplication' --include="*.java" .
```

- [ ] **Step 2：替换 scanBasePackages**

对每个 @SpringBootApplication 类的 `scanBasePackages` 参数（如果有），改为 `"dev.linqibin"`：

```bash
find . -name "*.java" -not -path "./build/*" -exec sed -i '' \
  -e 's|scanBasePackages = "com\.patra"|scanBasePackages = "dev.linqibin"|g' \
  -e 's|scanBasePackages = "com\.patra\.[a-z]*"|scanBasePackages = "dev.linqibin"|g' \
  {} +
```

如果原本没有 scanBasePackages（默认根扫描），需要显式添加（因为默认根包变成了 `dev.linqibin.patra.{svc}`，不会自动覆盖 `dev.linqibin.commons` / `dev.linqibin.starter`）。

对每个 boot 主类（手动 Edit 或写一个一次性脚本）：
```java
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class CatalogApplication { ... }
```

- [ ] **Step 3：更新 gradle.properties 的 patraGroup**

Run:
```bash
sed -i '' 's|^patraGroup=com\.patra$|patraGroup=dev.linqibin.patra|g' gradle.properties

cat gradle.properties | grep -E "Group"
```

Expected：
```
patraGroup=dev.linqibin.patra
commonsGroup=dev.linqibin.commons
```

- [ ] **Step 4：编译验证**

Run:
```bash
./gradlew compileJava --no-daemon
```

- [ ] **Step 5：Commit**

```bash
git add -A
git commit -m "refactor(rename): Phase 5.4 scanBasePackages + gradle.properties patraGroup 终态切换

- 4 个 boot @SpringBootApplication(scanBasePackages = \"dev.linqibin\")
- patraGroup: com.patra → dev.linqibin.patra

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5.5：CI/CD 配置 grep + 最终全量验证（Definition of Done）

**Files:**
- `.github/workflows/*.yml`、`Dockerfile`、`docker-compose.yml`、shell 脚本（如有）

- [ ] **Step 1：CI/CD 配置 grep**

Run:
```bash
grep -rln "com\.patra" --include="*.yml" --include="*.yaml" --include="Dockerfile*" --include="*.sh" .github/ 2>/dev/null
find . -name "Dockerfile" -o -name "docker-compose*.yml" -o -name "*.sh" | xargs grep -l "com\.patra" 2>/dev/null
```

如有匹配，sed 替换：
```bash
find . \( -name "Dockerfile" -o -name "docker-compose*.yml" -o -name "*.sh" \) -not -path "./build/*" -exec sed -i '' \
  's|com\.patra|dev.linqibin|g' \
  {} +
```

- [ ] **Step 2：全仓库零残留检查（Definition of Done）**

Run:
```bash
# 排除历史文档中合理的 mention（如 changelog、本 spec 自身）
grep -rn "com\.patra" \
  --include="*.java" --include="*.kt" --include="*.kts" \
  --include="*.yml" --include="*.yaml" --include="*.xml" \
  --include="*.md" --include="*.imports" --include="*.properties" \
  --include="Dockerfile*" --include="*.sh" \
  . | grep -v "docs/superpowers/specs" | grep -v "docs/superpowers/plans" | head
```

Expected：空输出，或仅剩 spec/plan 文档中"本次重构 com.patra → dev.linqibin"这种 mention（合理保留）。

- [ ] **Step 3：clean build 全量验证**

**前置：确认 Docker desktop 已启动**。

Run:
```bash
./gradlew clean build -x spotbugsMain -x spotbugsTest --no-daemon
```

Expected：`BUILD SUCCESSFUL`。包含全部编译 + 单元测试 + Testcontainers 集成测试。可能 5-15 分钟。

注：跳过 SpotBugs 详见"平台前提"节。

- [ ] **Step 4：4 个 boot 启动验证**

Run（对每个 boot 服务）：
```bash
# 启动并跑大约 10 秒看是否成功初始化
timeout 30 ./gradlew :patra-catalog-boot:bootRun --no-daemon 2>&1 | tee /tmp/catalog-bootrun.log
grep -E "Started .* in .* seconds" /tmp/catalog-bootrun.log
# 或看 autoconfig report
grep -E "AutoConfiguration|Conditional" /tmp/catalog-bootrun.log | head -30
```

类似处理 patra-registry-boot、patra-ingest-boot、patra-gateway-boot。

如果配置了 actuator，可以启动后 curl `/actuator/conditions` 看 starter 是否被识别。

- [ ] **Step 5：至少一个 boot 跑通一个集成测试**

Run:
```bash
./gradlew :patra-catalog-boot:integrationTest --no-daemon
# 或者根据现有 test 配置
./gradlew :patra-catalog-boot:test --no-daemon
```

Expected：测试通过。

- [ ] **Step 6：Final commit**

```bash
git add -A
git commit -m "refactor(rename): Phase 5.5 CI/CD 扫描 + Definition of Done 验证

- CI/CD 配置零残留确认
- 全仓库 com.patra 零残留（除 spec/plan 历史 mention）
- ./gradlew clean build BUILD SUCCESSFUL
- 4 boot bootRun 启动成功，starter autoconfig 正常加载
- 集成测试通过

重构完成。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 7：提示用户做最后的人工动作**

告诉用户：
> 重构分支已 ready。请你：
> 1. IDEA 里 `File → Invalidate Caches`（重启 IDE）
> 2. Review `git log refactor/rename-pkg-to-dev-linqibin --oneline`，确认 commit 链清晰
> 3. 决定是否 merge 到 main 或先 PR review
> 4. Merge 后建议跑一次完整集成测试 + 启动所有 boot 验证

---

## Phase 5 验证 gate 总结（Definition of Done）

- [x] `grep -rn "com\.patra"` 全仓库零残留（除 spec/plan 历史 mention）
- [x] `./gradlew clean build` BUILD SUCCESSFUL
- [x] 4 boot bootRun 启动成功
- [x] 至少 1 个集成测试通过
- [x] CI/CD 配置已检查
- [x] gradle.properties 双 group 配置就绪：patraGroup + commonsGroup

---

## 回滚策略

任何 Phase 失败：

```bash
# 回到上一 Phase commit
git reset --hard HEAD~  # 回退一个 commit
# 或回到 baseline
git reset --hard $(cat /tmp/baseline-sha.txt)
```

整体放弃重构：
```bash
git checkout main
git branch -D refactor/rename-pkg-to-dev-linqibin
```

---

## 已知风险点 cross-reference

详细见 spec Section 6。本计划在以下 task 中针对性缓解：

- **AutoConfiguration.imports 静默失效**：Task 4.2 + Task 5.5 二次 grep 验证 + bootRun 验证
- **scanBasePackages 漏配**：Task 5.4 改为 `"dev.linqibin"` 单 root，覆盖三个根包前缀
- **build-logic plugin ID 漏改**：Task 1.4 完成后 `./gradlew projects` 验证
- **logback 通配 logger**：Task 5.2 兜底 sed 命令处理 `com.patra` 单独出现
- **Phase 2 中间态破坏编译**：每个 Phase 2 task 内"git mv → sed → 编译验证"四步顺序严格执行

---

## 备注

本计划由 `superpowers:writing-plans` 技能基于已 commit 的 spec 文档（`docs/superpowers/specs/2026-05-15-rename-package-to-dev-linqibin-design.md`，commit e518b7ecc）生成。

执行者选项：
- **subagent-driven-development**（推荐）：每个 task 派 fresh subagent 执行，主 session review 后进入下一 task
- **executing-plans**：在当前 session 内批量执行，含 checkpoint review

任何步骤遇到非预期失败（编译失败、grep 残留、测试失败）：**停下来**，不要 fix-forward 太久。优先回滚到上一 commit，再排查根因。
