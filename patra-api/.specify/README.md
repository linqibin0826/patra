# Spec-Kit 使用指南

> **规范驱动开发工作流** - 从需求到实现的结构化开发流程

---

## 🚀 快速开始

### 前置条件

```bash
# 创建特性分支（必须是 ###-feature-name 格式）
git checkout -b 001-feature-name
```

### 最简流程（5 个核心命令）

```bash
/speckit.specify      # 1. 定义需求和用户场景
/speckit.plan         # 2. 生成技术实施计划
/speckit.tasks        # 3. 分解为可执行任务
/speckit.implement    # 4. 执行所有任务
/speckit.document     # 5. 生成项目文档（Patra 扩展）
```

**输出文件位置**：`.specify/specs/001-feature-name/`

---

## 📋 完整流程（含可选步骤）

### Phase 0: 项目治理（首次使用）

```bash
/speckit.constitution
```

创建项目宪章，定义架构原则和开发规范。

**输出**：`.specify/memory/constitution.md`

---

### Phase 1: 需求定义

```bash
# 1. 生成功能规范
/speckit.specify
```

**输入**：用户场景、功能需求、验收标准
**输出**：`spec.md`

```bash
# 2. 澄清模糊需求（推荐）
/speckit.clarify
```

AI 提出 5 个澄清问题，更新 `spec.md`

```bash
# 3. 质量检查清单（可选）
/speckit.checklist
```

验证规范完整性，生成 `checklist.md`

---

### Phase 2: 技术设计

```bash
/speckit.plan
```

**输出**：
- `plan.md` - 技术实施计划
- `data-model.md` - 领域模型设计
- `contracts/` - API 契约定义

**包含**：Constitution Check（验证 31 个 CHK-* 规则）

---

### Phase 3: 任务分解

```bash
/speckit.tasks
```

**输出**：`tasks.md` - 按六边形架构分层排序的任务清单

**任务标签**：
- `[P]` - 可并行执行
- `[Domain/App/Infra/Adapter]` - 架构层标记
- `[US#FR-xxx]` - 关联的功能需求

---

### Phase 4: 实施前验证

```bash
/speckit.analyze
```

检查 spec/plan/tasks 之间的一致性和 Constitution 合规性。

**输出**：终端显示分析报告

---

### Phase 5: 执行实施

```bash
/speckit.implement
```

**执行过程**：
1. TDD 驱动：先生成测试，再生成实现
2. 按 Domain → App → Infra → Adapter 顺序执行
3. 阶段性代码审查（调用 `java-code-reviewer`）
4. 自动运行测试验证

---

### Phase 6: 文档管理（Patra 扩展）

```bash
# 检查文档完整性
/speckit.document check

# 生成缺失的文档
/speckit.document generate package-info
/speckit.document generate javadoc

# 同步文档与代码
/speckit.document sync 001
```

**生成内容**：
- `package-info.java` - 包级文档
- JavaDoc - 公共 API 文档
- README.md - 模块说明

---

## 📁 目录结构

```
.specify/
├── memory/
│   └── constitution.md          # 项目宪章（v2.0.0）
├── specs/
│   └── 001-feature-name/        # 特性规范目录
│       ├── spec.md              # 功能规范
│       ├── plan.md              # 技术计划
│       ├── tasks.md             # 任务清单
│       ├── data-model.md        # 领域模型
│       ├── contracts/           # API 契约
│       ├── checklist.md         # 质量检查清单
│       └── research.md          # 调研文档（可选）
├── templates/                   # 文档模板
├── scripts/                     # 工具脚本
└── README.md                    # 本文件
```

---

## 🎯 Patra 特定规则

### Constitution 验证项（31 个 CHK-*）

所有特性在 `/speckit.plan` 阶段会自动检查：

| 类别 | 验证项 | 说明 |
|------|-------|------|
| **六边形架构** | CHK-ARCH-001~005 | Domain 纯 Java、依赖方向、事务边界 |
| **DDD 设计** | CHK-DDD-001~004 | 聚合边界、实体关系、领域事件 |
| **SSOT 原则** | CHK-SSOT-001~003 | 配置从 patra-registry 获取 |
| **测试策略** | CHK-TEST-001~005 | 5 层测试策略，覆盖率要求 |
| **技术选型** | CHK-TECH-001~004 | 兼容性、性能、维护成本 |
| **代码质量** | CHK-CODE-001~005 | 命名规范、复杂度、反模式检查 |
| **文档标准** | CHK-DOC-001~004 | README、package-info、JavaDoc |

**详细规范**：参见 `.claude/skills/` 中的各个 Skill 文档

---

## 💡 使用建议

### 什么时候使用完整流程？

✅ **适合**：
- 新数据源接入（如 DOAJ、EMBASE）
- 新微服务模块
- 跨模块架构变更

❌ **不适合**：
- 配置调整
- 简单 bug 修复
- 单个方法优化

### 可选步骤的价值

| 命令 | 何时跳过 | 何时使用 |
|------|---------|---------|
| `/speckit.clarify` | 需求明确 | 存在模糊点或隐式假设 |
| `/speckit.checklist` | 简单功能 | 复杂需求，需要验证完整性 |
| `/speckit.analyze` | 高度自信 | 多人协作，需要一致性保证 |

---

## 🔧 环境变量

| 变量 | 用途 |
|------|------|
| `SPECIFY_FEATURE` | 非 Git 环境下指定特性目录（如 `001-feature-name`） |

**示例**：
```bash
export SPECIFY_FEATURE=001-photo-albums
/speckit.plan  # 使用指定的特性目录
```

---

## 📚 参考资源

- **官方文档**: https://github.com/github/spec-kit
- **Constitution 详细规范**: `.specify/memory/constitution.md`
- **Skills 文档**: `.claude/skills/`
- **命令定义**: `.claude/commands/speckit.*.md`

---

**版本**: Spec-Kit v1.0 + Patra 定制
**最后更新**: 2025-11-09
