---
description: 代码变更后的完整质量检查（架构审查、代码审查、文档更新、测试编译）
argument-hint: [模块名 | committed | uncommitted | 空]
---

## 参数说明

**用户传入的参数**: `$ARGUMENTS`

**参数解析规则**（按优先级判断）：

1. **无参数**（`$ARGUMENTS` 为空）→ 检查所有本地变更（未提交 + 已提交未推送）
2. **`committed`** 或 **`已提交未推送`** → 只检查已提交但未推送到远程的变更
3. **`uncommitted`** 或 **`未提交`** → 只检查工作区未提交的变更
4. **模块名**（如 `patra-spring-boot-starter-web`）→ 只检查指定模块的变更

---

## Git 变更上下文

**当前仓库状态：**
!`git status`

**未提交的变更（diff）：**
!`git diff HEAD --stat`

**已提交未推送的提交：**
!`git log origin/main..HEAD --oneline`

**所有本地变更汇总（相对于远程 main）：**
!`git diff origin/main...HEAD --stat`

---

## 任务目标

根据参数 `$ARGUMENTS` 确定审查范围，然后对该范围内的代码变更执行全面的质量检查。

### 范围确定逻辑

**请先解析参数 `$ARGUMENTS`，确定要审查的范围：**

| 参数值 | 审查范围 | 使用的 Git 命令 |
|--------|----------|----------------|
| 空 | 所有本地变更 | `git diff origin/main...HEAD` |
| `committed` / `已提交未推送` | 已提交未推送 | `git diff origin/main..HEAD` |
| `uncommitted` / `未提交` | 工作区未提交 | `git diff HEAD` |
| 模块名（如 `patra-ingest`） | 指定模块变更 | `git diff origin/main...HEAD -- <模块路径>` |

### 质量检查目标

确保审查范围内的代码变更：
1. ✅ 符合六边形架构 + DDD 规范
2. ✅ 代码质量达标，无反模式和安全问题
3. ✅ 相关文档已更新（README.md）
4. ✅ 测试代码可以编译通过

---

## 执行指令

### 第零阶段：解析参数并确定审查范围

**🚨 首先解析 `$ARGUMENTS` 参数，确定本次审查的范围**

```
用户参数: $ARGUMENTS
```

**根据参数值执行不同的范围识别：**

1. **参数为空** → 使用「Git 变更上下文」中的所有信息，审查全部本地变更
2. **参数为 `committed` 或 `已提交未推送`** → 只关注「已提交未推送的提交」部分，忽略未提交的工作区变更
3. **参数为 `uncommitted` 或 `未提交`** → 只关注「未提交的变更」部分，忽略已提交的变更
4. **参数为模块名**（如 `patra-spring-boot-starter-web`）→ 从变更文件中筛选出属于该模块的文件

---

### 第一阶段：编译与架构测试（质量门槛）

**🚨 必须首先执行编译测试和架构测试，全部通过后才能进行后续审查**

#### 步骤 1：识别变更的模块

根据**第零阶段确定的审查范围**，识别涉及的 Gradle 模块。

**模块识别方式：**

- **如果参数是具体模块名** → 直接使用该模块名作为编译目标
- **否则** → 基于审查范围内的变更文件路径，自动识别涉及的模块

**路径到模块的映射规则：**
- 查看变更文件的路径前缀（如 `patra-ingest/`, `patra-registry/`, `patra-spring-boot-starter-provenance/`）
- 提取对应的 `-boot` 模块作为编译目标
- 常见模块映射：
  - `patra-ingest/*` → `patra-ingest-boot`
  - `patra-registry/*` → `patra-registry-boot`
  - `patra-spring-boot-starter-*/*` → 该 starter 本身
  - `patra-common/*` → `patra-common`

#### 步骤 2：执行测试编译

对识别出的模块执行编译：

```bash
./gradlew :模块1:testClasses :模块2:testClasses
```

**示例：**
```bash
./gradlew :patra-ingest:patra-ingest-boot:testClasses :patra-registry:patra-registry-boot:testClasses
```

#### 步骤 3：执行架构测试

**🚨 编译通过后，必须运行相关模块的架构测试（ArchUnit）**

对识别出的模块执行架构测试：

```bash
./gradlew :模块1:test --tests "*ArchTest" --tests "*ArchitectureTest"
```

**架构测试命名规范：**
- `*ArchTest.java` - 架构规则测试
- `*ArchitectureTest.java` - 架构约束测试

**示例：**
```bash
./gradlew :patra-ingest:patra-ingest-boot:test :patra-registry:patra-registry-boot:test --tests "*ArchTest" --tests "*ArchitectureTest"
```

#### 步骤 4：测试结果判断

- ✅ **编译成功 + 架构测试通过** → 继续执行第二阶段（并行启动三个 agents）
- ❌ **编译失败** → 报告编译错误，询问用户：
  ```
  ⚠️ 测试编译失败！

  错误信息：
  [错误日志摘要]

  🔧 建议：
  1. [具体的修复建议]
  2. [可能的原因分析]

  ❓ 是否需要我修复编译错误后再继续质量检查？
  ```
- ❌ **架构测试失败** → 报告架构违规，询问用户：
  ```
  ⚠️ 架构测试失败！

  违规信息：
  [架构测试失败日志摘要]

  🔧 建议：
  1. [具体的架构违规说明]
  2. [修复建议]

  ❓ 是否需要我修复架构违规后再继续质量检查？
  ```

---

### 第二阶段：并行质量审查（仅在编译和架构测试都通过后执行）
**🚨 编译和架构测试都通过后，必须在单个响应中使用三个 Task 工具调用来并行启动以下三个 subagents（你不应该制定规则和输出格式，你只要说明审查范围即可）：**

#### 1. 代码质量与架构合规性审查
```
subagent_type: code-reviewer
description: 代码质量与架构合规性审查
prompt: |
  审查范围：[第零阶段确定的范围描述]
  审查以下变更的代码文件：[列出审查范围内的文件]
```

#### 2. 测试质量检查
```
subagent_type: test-checker
description: 测试质量检查
prompt: |
  审查范围：[第零阶段确定的范围描述]
  检查以下变更文件的测试质量：[列出审查范围内的文件]
```

#### 3. 文档完整性检查
```
subagent_type: doc-checker
description: 文档完整性检查
prompt: |
  审查范围：[第零阶段确定的范围描述]
  检查以下变更文件对应的文档是否同步：[列出审查范围内的文件]
```


---

## 最终报告

**仅在第二阶段完成后**（即编译通过且三个 agents 都返回结果后），生成综合报告：

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 代码变更质量检查报告
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🎯 审查范围：[参数解析结果，如"所有本地变更"/"已提交未推送"/"模块 xxx"]
📦 涉及模块：[模块列表]

🔨 测试编译：✅ 通过
   - 编译的模块：[具体模块]

🏛️ 架构测试：✅ 通过
   - 测试的模块：[具体模块]

🏗️ 架构合规性：[优秀/良好/需改进/严重问题]
   - [code-reviewer agent 的主要发现摘要]

🧪 测试质量：[优秀/良好/需改进/严重问题]
   - [test-checker agent 的主要发现摘要]

📚 文档完整性：[完整/部分缺失/严重缺失]
   - [doc-checker agent 的主要发现摘要]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
总体评估：[✅ 可以提交 / ⚠️ 需要改进 / ❌ 存在严重问题]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 注意事项

- **🚨 参数优先**：必须先解析 `$ARGUMENTS` 参数确定审查范围（第零阶段），再执行后续操作
- **🚨 严格的执行顺序**：必须先完成编译测试（第一阶段），编译通过后才能启动三个 agents（第二阶段）
- **智能模块识别**：如果参数是模块名则直接使用，否则根据审查范围内的变更文件路径自动识别
- **并行执行**：第二阶段的三个 agents 必须在同一个响应中启动，不要等待一个完成再启动下一个
- **编译失败处理**：如果编译失败，不要继续执行第二阶段，而是报告错误并询问用户是否需要修复
- **完整性**：即使某个 agent 失败，也要继续执行其他检查
- **实用性**：如果发现问题，除了指出问题外，还要提供可执行的修复建议
- **自动修复**：对于文档缺失问题，documentation-architect agent 应该直接更新文档，而不仅仅是报告

## 使用示例

```bash
# 检查所有本地变更（默认）
/post-code

# 只检查已提交但未推送的变更
/post-code committed
/post-code 已提交未推送

# 只检查工作区未提交的变更
/post-code uncommitted
/post-code 未提交

# 只检查特定模块的变更
/post-code patra-spring-boot-starter-web
/post-code patra-ingest
```
