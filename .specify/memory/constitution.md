# Patra 项目架构宪章

**版本**: 2.0.0
**批准日期**: 2025-01-09
**最后修订**: 2025-11-09
**变更说明**: 改为索引表模式，详细规范维护在 `.claude/skills/` 中

---

## 💡 核心理念

本宪章作为 Patra 项目架构原则的**索引和门禁**，详细规范和实施指南维护在各个 **Skills** 中。

**设计哲学**：
- ✅ **Skills 是 SSOT**：详细规范、代码示例、最佳实践在 Skills 中维护
- ✅ **Constitution 是索引**：验证项（CHK-*）汇总，引用 Skills
- ✅ **单一真相来源**：避免重复，确保一致性

---

## 📋 Architecture Check 验证项总览

所有新功能在进入实施阶段前必须通过以下验证。

### I. 六边形架构验证

**原则定义**：详见 → [java-hexagonal-architecture/SKILL.md](../../.claude/skills/java-hexagonal-architecture/SKILL.md)

| CHK 编号 | 验证项 | Skills 位置 |
|---------|--------|------------|
| **CHK-ARCH-001** | Domain 层是否纯 Java？（仅允许 Lombok、Hutool、patra-common） | [架构铁律 #1](../../.claude/skills/java-hexagonal-architecture/SKILL.md#架构铁律) |
| **CHK-ARCH-002** | 依赖方向是否正确？（Adapter → App → Domain ← Infra） | [架构铁律 #2](../../.claude/skills/java-hexagonal-architecture/SKILL.md#架构铁律) |
| **CHK-ARCH-003** | 事务边界是否在 Orchestrator？（@Transactional 仅在应用层） | [架构铁律 #3](../../.claude/skills/java-hexagonal-architecture/SKILL.md#架构铁律) |
| **CHK-ARCH-004** | DO 是否被正确封装？（DO 不离开 Infrastructure 层） | [架构铁律 #4](../../.claude/skills/java-hexagonal-architecture/SKILL.md#架构铁律) |
| **CHK-ARCH-005** | 是否存在循环依赖？ | [架构评审检查点](../../.claude/skills/java-hexagonal-architecture/SKILL.md#架构评审检查点) |

**代码审查参考** → [java-code-reviewer/SKILL.md](../../.claude/skills/java-code-reviewer/SKILL.md)

---

### II. DDD 战术设计验证

**原则定义**：详见 → [java-hexagonal-architecture/SKILL.md](../../.claude/skills/java-hexagonal-architecture/SKILL.md)

| CHK 编号 | 验证项 | Skills 位置 |
|---------|--------|------------|
| **CHK-DDD-001** | 聚合边界是否合理？ | [领域建模检查](../../.claude/skills/java-hexagonal-architecture/SKILL.md#领域建模检查) |
| **CHK-DDD-002** | 实体间关系是否正确？ | [领域建模检查](../../.claude/skills/java-hexagonal-architecture/SKILL.md#领域建模检查) |
| **CHK-DDD-003** | 领域事件是否完整？ | [领域建模检查](../../.claude/skills/java-hexagonal-architecture/SKILL.md#领域建模检查) |
| **CHK-DDD-004** | Port 接口是否抽象得当？ | [领域建模检查](../../.claude/skills/java-hexagonal-architecture/SKILL.md#领域建模检查) |

**实施指南参考**：
- 聚合设计模式 → [java-hexagonal-architecture/SKILL.md#聚合设计模式](../../.claude/skills/java-hexagonal-architecture/SKILL.md#常见架构模式)
- Port-Adapter 模式 → [java-hexagonal-architecture/SKILL.md#Port-Adapter模式](../../.claude/skills/java-hexagonal-architecture/SKILL.md#常见架构模式)

---

### III. 单一事实来源（SSOT）验证

**定义**: `patra-registry` 是 Provenance 配置、数据字典、元数据的唯一权威来源。

| CHK 编号 | 验证项 | 说明 |
|---------|--------|------|
| **CHK-SSOT-001** | Provenance 配置是否从 `patra-registry` 获取？ | 禁止硬编码数据源配置 |
| **CHK-SSOT-002** | 数据字典是否从 `patra-registry` 获取？ | 禁止重复定义枚举值、分类体系 |
| **CHK-SSOT-003** | 元数据和映射规则是否从 `patra-registry` 获取？ | 支持版本化管理 |

**实施方式**：使用 `patra-registry-api` 模块访问配置

---

### IV. 测试策略验证

**原则定义**：详见 → [java-test-architect/SKILL.md](../../.claude/skills/java-test-architect/SKILL.md)

| CHK 编号 | 验证项 | Skills 位置 |
|---------|--------|------------|
| **CHK-TEST-001** | Domain 层单元测试覆盖率 ≥ 80%？（无 Spring） | [测试策略](../../.claude/skills/java-test-architect/SKILL.md#六边形架构测试策略) |
| **CHK-TEST-002** | Application 层单元测试覆盖率 ≥ 70%？（Mockito） | [测试策略](../../.claude/skills/java-test-architect/SKILL.md#六边形架构测试策略) |
| **CHK-TEST-003** | Infrastructure 层是否有 IT 集成测试？（TestContainers） | [测试策略](../../.claude/skills/java-test-architect/SKILL.md#六边形架构测试策略) |
| **CHK-TEST-004** | Adapter 层是否有集成测试？（MockMvc） | [测试策略](../../.claude/skills/java-test-architect/SKILL.md#六边形架构测试策略) |
| **CHK-TEST-005** | 是否有架构测试？（ArchUnit） | [测试策略](../../.claude/skills/java-test-architect/SKILL.md#六边形架构测试策略) |
| **CHK-TEST-006** | IT 和 E2E 测试是否在 boot 模块？⚠️ | [测试模块位置规范](../../.claude/skills/java-test-architect/SKILL.md#测试模块位置规范) |

**测试模板参考** → [java-test-architect/SKILL.md](../../.claude/skills/java-test-architect/SKILL.md)

---

### V. 技术选型验证

**原则定义**：详见 → [java-hexagonal-architecture/SKILL.md](../../.claude/skills/java-hexagonal-architecture/SKILL.md#技术选型检查)

| CHK 编号 | 验证项 | Skills 位置 |
|---------|--------|------------|
| **CHK-TECH-001** | 技术栈是否与现有系统兼容？ | [技术选型检查](../../.claude/skills/java-hexagonal-architecture/SKILL.md#技术选型检查) |
| **CHK-TECH-002** | 是否有更好的替代方案？ | [技术选型检查](../../.claude/skills/java-hexagonal-architecture/SKILL.md#技术选型检查) |
| **CHK-TECH-003** | 性能影响是否可接受？ | [技术选型检查](../../.claude/skills/java-hexagonal-architecture/SKILL.md#技术选型检查) |
| **CHK-TECH-004** | 维护成本是否合理？ | [技术选型检查](../../.claude/skills/java-hexagonal-architecture/SKILL.md#技术选型检查) |

---

### VI. 代码质量验证

**原则定义**：详见 → [java-code-reviewer/SKILL.md](../../.claude/skills/java-code-reviewer/SKILL.md)

| CHK 编号 | 验证项 | Skills 位置 |
|---------|--------|------------|
| **CHK-CODE-001** | 命名是否符合规范？ | [命名规范](../../.claude/skills/java-code-reviewer/SKILL.md#命名规范) |
| **CHK-CODE-002** | 方法复杂度是否合理？（圈复杂度、行数） | [方法复杂度](../../.claude/skills/java-code-reviewer/SKILL.md#方法复杂度) |
| **CHK-CODE-003** | 是否避免了贫血模型？ | [常见反模式](../../.claude/skills/java-code-reviewer/SKILL.md#贫血模型) |
| **CHK-CODE-004** | 是否避免了循环依赖？ | [常见反模式](../../.claude/skills/java-code-reviewer/SKILL.md#循环依赖) |
| **CHK-CODE-005** | 是否避免了 N+1 查询？ | [性能相关审查](../../.claude/skills/java-code-reviewer/SKILL.md#n1-查询问题) |

**代码审查参考** → [java-code-reviewer/SKILL.md](../../.claude/skills/java-code-reviewer/SKILL.md)

---

### VII. 文档标准验证

**原则定义**：详见 → [java-documentation-architect/SKILL.md](../../.claude/skills/java-documentation-architect/SKILL.md)

| CHK 编号 | 验证项 | Skills 位置 |
|---------|--------|------------|
| **CHK-DOC-001** | 每个模块是否有 README.md？ | [文档维护检查清单](../../.claude/skills/java-documentation-architect/SKILL.md#文档维护检查清单) |
| **CHK-DOC-002** | 每个包是否有 package-info.java？ | [文档维护检查清单](../../.claude/skills/java-documentation-architect/SKILL.md#文档维护检查清单) |
| **CHK-DOC-003** | 公共 API 是否有 JavaDoc？ | [文档维护检查清单](../../.claude/skills/java-documentation-architect/SKILL.md#文档维护检查清单) |
| **CHK-DOC-004** | 重要决策是否有 ADR 记录？ | [ADR 模板](../../.claude/skills/java-documentation-architect/SKILL.md#架构决策记录adr模板) |

**文档模板参考** → [java-documentation-architect/SKILL.md](../../.claude/skills/java-documentation-architect/SKILL.md)

---

## 🎯 Constitution Check 使用指南

### 在 Spec-Kit 工作流中使用

#### 1. `/speckit.plan` 阶段（Phase 0: Constitution Check）

```markdown
## Phase 0: Constitution Check

**验证方法**：对照本 Constitution 验证项列表，逐项检查

**示例**：
- [ ] **CHK-ARCH-001**: Domain 层是否纯 Java？
  - 检查 `patra-{service}-domain/pom.xml`，确保无 Spring、MyBatis 依赖
  - 参考：[java-hexagonal-architecture/SKILL.md#架构铁律](../../.claude/skills/java-hexagonal-architecture/SKILL.md#架构铁律)

- [ ] **CHK-TEST-006**: IT 和 E2E 测试是否在 boot 模块？
  - 检查 spec.md 中的测试任务位置
  - 参考：[java-test-architect/SKILL.md#测试模块位置规范](../../.claude/skills/java-test-architect/SKILL.md#测试模块位置规范)

**结果**：PASS / FAIL
- PASS → 进入 Phase 1
- FAIL → 在 plan.md 的 "Complexity Tracking" 章节说明理由
```

#### 2. `/speckit.analyze` 阶段（一致性分析）

```markdown
## Constitution 对齐检查

**方法**：检查 plan.md 和 tasks.md 是否违反 MUST 原则

**示例**：
- ❌ **CRITICAL**: plan.md 第 45 行违反 CHK-ARCH-001（Domain 层引入 Spring）
- ⚠️ **WARNING**: tasks.md 的 T050 任务将 IT 测试放在 infra 模块（违反 CHK-TEST-006）
```

#### 3. `/speckit.implement` 阶段（实施中审查）

```markdown
## 实施中验证

**方法**：
1. 代码生成时参考 Skills 中的代码模板
2. 阶段完成后调用 `java-code-reviewer` 审查
3. 最后阶段调用 `java-documentation-architect` 生成文档

**Skills 调用示例**：
- Domain 层代码 → 参考 [java-hexagonal-architecture/SKILL.md#聚合设计模式](../../.claude/skills/java-hexagonal-architecture/SKILL.md#常见架构模式)
- Repository 实现 → 参考 [java-spring-development/SKILL.md#MyBatis-Plus数据访问](../../.claude/skills/java-spring-development/SKILL.md#mybatis-plus-数据访问)
- 测试代码 → 参考 [java-test-architect/SKILL.md](../../.claude/skills/java-test-architect/SKILL.md)
```

---

## 📚 Skills 索引

### 架构与设计
- [java-hexagonal-architecture/SKILL.md](../../.claude/skills/java-hexagonal-architecture/SKILL.md) - 六边形架构和 DDD 专家

### 开发实施
- [java-spring-development/SKILL.md](../../.claude/skills/java-spring-development/SKILL.md) - Spring Boot 微服务开发

### 质量保障
- [java-test-architect/SKILL.md](../../.claude/skills/java-test-architect/SKILL.md) - 测试生成专家
- [java-code-reviewer/SKILL.md](../../.claude/skills/java-code-reviewer/SKILL.md) - 代码审查专家
- [java-documentation-architect/SKILL.md](../../.claude/skills/java-documentation-architect/SKILL.md) - 文档架构师

### 运维支持
- [java-runtime-diagnostic/SKILL.md](../../.claude/skills/java-runtime-diagnostic/SKILL.md) - 运行时错误诊断

---

## 🔧 治理规则

### 宪章修订流程

1. **Skills 优先修改**：规范变更首先在 Skills 中更新
2. **Constitution 同步**：更新本索引表中的 CHK-* 验证项
3. **版本管理**：修订需要更新版本号（MAJOR.MINOR.PATCH）
4. **变更记录**：在文件头部记录变更说明

### 违规处理

| 违规类型 | 严重程度 | 处理方式 |
|---------|---------|---------|
| **Constitution Check 失败** | CRITICAL | 阻止进入实施阶段，必须在 plan.md 的 Complexity Tracking 说明理由 |
| **CHK-ARCH-*** 违规 | CRITICAL | 阻止合并到主分支 |
| **CHK-TEST-006** 违规 | HIGH | 必须修复测试位置 |
| **CHK-CODE-*** 违规 | MEDIUM | 代码审查阶段修复 |
| **CHK-DOC-*** 违规 | LOW | 补充文档 |

---

**版本**: 2.0.0
**批准日期**: 2025-01-09
**最后修订**: 2025-11-09
