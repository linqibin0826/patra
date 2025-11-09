---
description: 管理和同步项目文档，包括 package-info.java、README.md、API 文档和 JavaDoc
---

## 用户输入

```text
$ARGUMENTS
```

## 概述

`/speckit.document` 命令提供 4 种文档管理功能：

- **check**: 检查文档完整性和一致性
- **update [feature-number]**: 为指定特性增量更新文档
- **sync [feature-number]**: 同步文档与代码实现
- **generate [type]**: 生成缺失的文档

## 命令格式

```bash
/speckit.document check                    # 文档完整性检查
/speckit.document update 001               # 增量更新特性 #001 的文档
/speckit.document sync 001                 # 同步特性 #001 的文档
/speckit.document generate package-info    # 生成所有缺失的 package-info.java
/speckit.document generate readme          # 生成模块 README.md
/speckit.document generate api             # 生成 API 文档
/speckit.document generate javadoc         # 生成核心类 JavaDoc
```

---

## 🔍 功能 1: 文档完整性检查 (`check`)

### 执行步骤

**1. 检测项目类型**

```bash
# 判断是否为六边形架构项目
ls patra-*/pom.xml 2>/dev/null && echo "六边形架构" || echo "单体应用"
```

**2. 扫描现有文档**

使用 Glob 工具扫描：

```
# package-info.java
**/src/main/java/**/package-info.java

# Module README
patra-*/README.md

# API 文档
**/contracts/**/*.{md,yaml,json}

# JavaDoc
**/src/main/java/**/*.java (检查 /** */ 注释)
```

**3. 执行 CHK-DOC 检查项**

参考 `.specify/checklists/architecture-compliance-checklist.md` 中的 CHK-DOC-* 验证项：

- CHK-DOC-001: 每个包必须有 package-info.java
- CHK-DOC-002: 每个模块必须有 README.md
- CHK-DOC-003: Controller 必须有 API 文档
- CHK-DOC-004: 聚合根必须有 JavaDoc
- CHK-DOC-005: 文档必须有特性标记
- CHK-DOC-006: 文档版本必须与代码一致
- CHK-DOC-007: API 文档必须与 Controller 签名一致
- CHK-DOC-008: 外部链接必须可访问
- CHK-DOC-009: 中文文档必须使用 UTF-8 no BOM

**4. 生成检查报告**

输出格式：

```markdown
# 文档完整性检查报告

## ✅ 通过项 (X/9)

- [✓] CHK-DOC-001: package-info.java 完整性
  - domain: ✓
  - app: ✓
  - infra: ✓
  - adapter: ✓

## ❌ 失败项 (X/9)

- [✗] CHK-DOC-003: API 文档缺失
  - patra-ingest-adapter: IngestController.java 缺少 API 文档

## 📊 统计

- package-info.java: 23/24 (95.8%)
- README.md: 5/6 (83.3%)
- API 文档: 3/5 (60.0%)
- JavaDoc: 12/15 (80.0%)

## 🔧 修复建议

1. 运行 `/speckit.document generate package-info` 生成缺失的 package-info.java
2. 运行 `/speckit.document generate api` 生成缺失的 API 文档
3. 运行 `/speckit.document sync 001` 同步特性 #001 的文档
```

---

## 📝 功能 2: 增量更新文档 (`update [feature-number]`)

### 前置条件

- 特性已通过 `/speckit.plan` 生成设计文档
- 特性已通过 `/speckit.implement` 完成代码实现
- 存在 `.specify/features/###-feature-name/spec.md`

### 执行步骤

**1. 加载特性上下文**

```bash
# 从参数提取特性编号
FEATURE_NUM=$ARGUMENTS  # 例如 "001"

# 构造特性目录路径
FEATURE_DIR=".specify/features/$(ls .specify/features/ | grep "^${FEATURE_NUM}-")"

# 加载特性文件
SPEC_FILE="${FEATURE_DIR}/spec.md"
PLAN_FILE="${FEATURE_DIR}/plan.md"
TASKS_FILE="${FEATURE_DIR}/tasks.md"
```

读取这些文件以获取：
- 特性名称、范围、领域模型
- 技术决策、API 设计
- 已完成的任务列表

**2. 调用 java-documentation-architect skill**

```
使用 Skill 工具调用 java-documentation-architect
```

**3. 增量更新 package-info.java**

对于 `tasks.md` 中的每个 `T996-T999` 任务：

```bash
# 提取包路径和文件位置
PACKAGE_PATH=$(从任务描述提取)
FILE_PATH=$(从任务描述提取)

# 检查文件是否存在
if [ -f "$FILE_PATH" ]; then
  # 增量更新：在文件末尾添加特性章节
  在 "### 相关特性" 章节下添加:

  <!-- 特性 ###-feature-name -->
  - **[特性名称]** (#{feature-number})
    - 新增类: Class1, Class2
    - 核心组件: {@link ComponentName}
    - 设计考虑: [从 spec.md 提取]
  <!-- /特性 ###-feature-name -->
else
  # 全新生成
  使用 java-documentation-architect 的 package-info 模板生成
fi
```

**4. 增量更新模块 README.md**

```bash
# 检测模块
SERVICE_NAME=$(从 plan.md 的"微服务选择"章节提取)
README_PATH="patra-${SERVICE_NAME}/README.md"

if [ -f "$README_PATH" ]; then
  # 在 "🎯 核心类说明" 章节下添加特性章节

  ### 特性 ###-feature-name (#{feature-number})

  **功能概述**: [从 spec.md 的"概览"提取]

  **核心组件**:
  - AggregateRoot: [从 tasks.md 提取聚合根类名]
  - Orchestrator: [从 tasks.md 提取编排器类名]
  - Repository: [从 tasks.md 提取仓储类名]

  **设计亮点**: [从 plan.md 的"关键设计决策"提取]

  **使用示例**: [从 quickstart.md 提取]
else
  # 全新生成（新模块）
  使用 java-documentation-architect 的 module-readme 模板生成
fi
```

**5. 生成/更新 API 文档**

检测是否有 Controller 任务：

```bash
if grep -q "Controller" "${TASKS_FILE}"; then
  # 提取 Controller 类名
  CONTROLLER_CLASS=$(从 tasks.md 提取)

  # 扫描 Controller 的所有 @RequestMapping 方法
  使用 mcp__serena__find_symbol 工具查找 Controller 类

  # 生成 OpenAPI 规范
  输出到: ${FEATURE_DIR}/contracts/api-spec.yaml

  内容映射:
  - paths: 从 Controller 的 @RequestMapping 提取
  - schemas: 从 Controller 的请求/响应 DTO 提取
  - descriptions: 从 spec.md 的"功能需求" FR-* 提取
fi
```

**6. 生成核心类 JavaDoc**

对于 `tasks.md` 中的每个聚合根类：

```bash
# 提取聚合根类名
AGGREGATE_CLASS=$(从 tasks.md 提取)

# 读取类源码
使用 mcp__serena__find_symbol 读取类定义

# 检查是否已有 JavaDoc
if ! grep -q "^/\*\*" "$CLASS_FILE"; then
  # 生成 JavaDoc
  在类定义前添加:

  /**
   * [聚合根名称] - [从 spec.md 提取业务描述]
   *
   * <p>[从 spec.md 的"领域模型"章节提取设计说明]</p>
   *
   * <h2>业务不变量</h2>
   * <ul>
   *   <li>[从 spec.md 提取]</li>
   * </ul>
   *
   * <h2>状态转换</h2>
   * <p>[从 spec.md 的"状态机"章节提取]</p>
   *
   * @author Patra System
   * @since [当前版本]
   * @see [相关类]
   */
fi
```

**7. 更新版本标记**

在所有更新的文档中添加/更新元数据：

```markdown
<!-- 文档版本: v1.2.0 -->
<!-- 最后更新: 2025-01-09 -->
<!-- 关联特性: #001, #003 -->
```

**8. 生成更新报告**

```markdown
# 文档更新报告 - 特性 #001

## 更新内容

### ✅ package-info.java (4 个文件)
- ✓ com.patra.ingest.domain
- ✓ com.patra.ingest.app
- ✓ com.patra.ingest.infra
- ✓ com.patra.ingest.adapter

### ✅ Module README.md (1 个文件)
- ✓ patra-ingest/README.md
  - 新增: "特性 001-pubmed-ingest-pipeline" 章节

### ✅ API 文档 (1 个文件)
- ✓ .specify/features/001-pubmed-ingest-pipeline/contracts/api-spec.yaml

### ✅ JavaDoc (2 个文件)
- ✓ IngestTask.java
- ✓ IngestRecord.java

## 数据来源映射

| 目标文档 | 数据来源 | 章节 |
|---------|---------|------|
| package-info.java | spec.md | "领域模型" |
| README.md | spec.md | "概览"、"功能需求" |
| API 文档 | Controller.java | @RequestMapping |
| JavaDoc | spec.md | "领域模型"、"状态机" |
```

---

## 🔄 功能 3: 同步文档与代码 (`sync [feature-number]`)

### 目的

检测代码变更后文档是否过时，自动同步内容。

### 执行步骤

**1. 加载特性上下文**（同 `update`）

**2. 检测代码变更**

```bash
# 获取特性分支的最后提交时间
LAST_COMMIT_DATE=$(git log -1 --format=%cd --date=short -- ${FEATURE_DIR})

# 获取文档最后更新时间
DOC_UPDATE_DATE=$(grep "最后更新" patra-*/README.md | sed 's/.*: //')

# 比较时间戳
if [ "$LAST_COMMIT_DATE" \> "$DOC_UPDATE_DATE" ]; then
  echo "⚠️  文档过期，需要同步"
fi
```

**3. 比对 API 文档与 Controller 签名**

```bash
# 读取 API 文档中的端点定义
API_ENDPOINTS=$(从 api-spec.yaml 提取)

# 扫描 Controller 的实际方法
ACTUAL_ENDPOINTS=$(使用 mcp__serena__find_symbol 扫描 Controller)

# 对比差异
diff <(echo "$API_ENDPOINTS") <(echo "$ACTUAL_ENDPOINTS")
```

如果发现差异：
- 新增端点 → 更新 API 文档
- 删除端点 → 标记为 deprecated
- 参数变更 → 更新 schemas

**4. 比对 package-info.java 与实际类列表**

```bash
# 提取 package-info.java 中列出的类
DOCUMENTED_CLASSES=$(grep '@link' package-info.java | sed 's/.*@link //' | sed 's/}.*//')

# 扫描包内实际的类
ACTUAL_CLASSES=$(find src/main/java/com/patra/*/domain -name "*.java" -not -name "package-info.java")

# 找出未文档化的新类
NEW_CLASSES=$(comm -13 <(echo "$DOCUMENTED_CLASSES") <(echo "$ACTUAL_CLASSES"))

# 在 package-info.java 中添加新类的 @link 引用
```

**5. 比对 JavaDoc 与代码注释**

```bash
# 检查聚合根类的 JavaDoc 是否完整
对于每个聚合根类:
  - 检查类注释是否存在
  - 检查公共方法注释是否存在
  - 检查 @param、@return、@throws 是否完整
```

**6. 生成同步报告**

```markdown
# 文档同步报告 - 特性 #001

## 🔍 检测到的差异

### ❌ API 文档过期
- **IngestController.startIngest()**
  - API 文档: POST /ingest/start
  - 实际签名: POST /api/v1/ingest/start
  - 修复: 已更新 api-spec.yaml

### ⚠️  package-info.java 缺失新类
- **com.patra.ingest.domain**
  - 新增类: IngestResultVO (未文档化)
  - 修复: 已添加 @link 引用

### ✅ 已同步
- Module README.md: 无变更
- JavaDoc: 无变更

## 📝 建议

- 运行 `mvn javadoc:javadoc` 验证 JavaDoc 语法
- 运行 `/speckit.document check` 验证文档完整性
```

---

## 🛠️ 功能 4: 生成缺失文档 (`generate [type]`)

### 支持的文档类型

- `package-info`: 生成所有缺失的 package-info.java
- `readme`: 生成模块 README.md
- `api`: 生成 API 文档
- `javadoc`: 为核心类生成 JavaDoc

### 执行逻辑

**1. 扫描缺失项**

```bash
case "$ARGUMENTS" in
  package-info)
    # 找出所有没有 package-info.java 的包
    MISSING_PACKAGES=$(find src/main/java -type d -not -path "*/target/*" \
      -exec test ! -e {}/package-info.java \; -print)
    ;;
  readme)
    # 找出所有没有 README.md 的模块
    MISSING_MODULES=$(find patra-* -maxdepth 1 -type d \
      -exec test ! -e {}/README.md \; -print)
    ;;
  api)
    # 找出所有 Controller 但没有 API 文档的模块
    CONTROLLERS=$(find */adapter/src/main/java -name "*Controller.java")
    for ctrl in $CONTROLLERS; do
      if [ ! -f "相应的 api-spec.yaml" ]; then
        echo "$ctrl"
      fi
    done
    ;;
  javadoc)
    # 找出所有聚合根但没有 JavaDoc 的类
    AGGREGATES=$(grep -r "class.*AggregateRoot" */domain/src/main/java)
    for agg in $AGGREGATES; do
      if ! grep -q "^/\*\*" "$agg"; then
        echo "$agg"
      fi
    done
    ;;
esac
```

**2. 调用 java-documentation-architect skill**

**3. 批量生成文档**

对于每个缺失项：
- 使用相应的模板生成内容
- 写入文件
- 记录生成日志

**4. 生成报告**

```markdown
# 文档生成报告

## ✅ 生成成功 (12 个文件)

### package-info.java (8 个)
- ✓ com.patra.ingest.domain.model
- ✓ com.patra.ingest.domain.event
- ✓ com.patra.ingest.app.orchestrator
- ...

### README.md (2 个)
- ✓ patra-ingest/README.md
- ✓ patra-parser/README.md

### API 文档 (1 个)
- ✓ patra-ingest/contracts/api-spec.yaml

### JavaDoc (1 个)
- ✓ IngestTask.java

## ❌ 生成失败 (2 个文件)

- ✗ com.patra.registry.domain.config
  - 原因: 包内无任何类文件
  - 建议: 手动确认是否需要此包

## 📊 统计

- 总耗时: 45 秒
- 文档总行数: 1,247 行
- 平均每文件: 103 行

## 🔧 后续操作

1. 运行 `mvn javadoc:javadoc` 验证 JavaDoc 语法
2. 运行 `/speckit.document check` 验证文档完整性
3. 运行 `git add .` 提交新生成的文档
```

---

## 关键规则

1. **调用 java-documentation-architect skill**: 所有文档生成必须使用该 skill 的模板
2. **增量更新优先**: 已存在的文档使用增量更新，避免覆盖
3. **特性标记**: 所有增量更新必须添加 `<!-- 特性 ###-feature-name -->` 标记
4. **数据来源映射**: 明确标注文档内容来自哪个源文件的哪个章节
5. **中文编码**: 所有文档使用 UTF-8 no BOM 编码
6. **版本追踪**: 每次更新必须更新 "最后更新" 时间戳
7. **报告输出**: 每个功能执行后必须生成详细报告
8. **并行执行**: 批量操作时尽可能并行处理（如生成多个 package-info.java）

---

## 错误处理

### 错误 1: 特性目录不存在

```bash
if [ ! -d "$FEATURE_DIR" ]; then
  echo "❌ 错误: 特性 #${FEATURE_NUM} 不存在"
  echo "提示: 先运行 /speckit.plan 生成特性设计"
  exit 1
fi
```

### 错误 2: skill 调用失败

```bash
if ! 调用 java-documentation-architect skill 成功; then
  echo "⚠️  警告: 无法调用 java-documentation-architect skill"
  echo "降级: 使用内置基础模板生成文档"
  使用简化模板继续执行
fi
```

### 错误 3: Git 冲突

```bash
if git diff --name-only | grep -q "README.md\|package-info.java"; then
  echo "⚠️  警告: 检测到未提交的文档变更"
  echo "建议: 先提交或暂存当前变更，再运行文档同步"
  询问用户是否继续
fi
```

---

## 使用示例

### 场景 1: 新特性开发完成后更新文档

```bash
# 完成特性 #001 的代码实现
/speckit.implement 001

# 增量更新特性 #001 的所有文档
/speckit.document update 001

# 检查文档完整性
/speckit.document check
```

### 场景 2: 代码重构后同步文档

```bash
# 重构了 IngestController
git commit -m "refactor: optimize IngestController API"

# 同步文档
/speckit.document sync 001

# 检查差异
git diff patra-ingest/README.md
git diff .specify/features/001-*/contracts/api-spec.yaml
```

### 场景 3: 批量生成缺失文档

```bash
# 检查当前文档状态
/speckit.document check

# 生成所有缺失的 package-info.java
/speckit.document generate package-info

# 生成所有缺失的 API 文档
/speckit.document generate api

# 再次检查，验证完整性
/speckit.document check
```

### 场景 4: 新模块初始化

```bash
# 创建新模块 patra-parser
mkdir -p patra-parser/{domain,app,infra,adapter}

# 生成模块 README.md
/speckit.document generate readme

# 生成所有包的 package-info.java
/speckit.document generate package-info
```
