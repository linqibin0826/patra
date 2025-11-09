# 架构合规性检查清单

**目的**: 验证功能实施是否符合 Patra 项目的架构约束
**创建日期**: [日期]
**功能**: [链接到 spec.md]

**说明**: 此检查清单基于 `.specify/memory/constitution.md` 中定义的架构规范，用于确保代码遵循六边形架构和 DDD 原则。

---

## 🏗️ 六边形架构验证

### 模块结构

- [ ] **CHK-ARCH-001**: 微服务包含完整的 6 层模块结构
  - [ ] `patra-{service}-boot` 存在
  - [ ] `patra-{service}-api` 存在
  - [ ] `patra-{service}-domain` 存在
  - [ ] `patra-{service}-app` 存在
  - [ ] `patra-{service}-infra` 存在
  - [ ] `patra-{service}-adapter` 存在

- [ ] **CHK-ARCH-002**: Domain 层 `pom.xml` 依赖符合规范
  - [ ] ✅ 仅包含：JDK、patra-common-util、Lombok、Validation API
  - [ ] ❌ 不包含：Spring Framework（@Component/@Service/@Autowired）
  - [ ] ❌ 不包含：MyBatis-Plus
  - [ ] ❌ 不包含：Jackson
  - [ ] ❌ 不包含：任何持久化框架

### 依赖方向

- [ ] **CHK-ARCH-003**: 依赖方向符合 `Adapter → App → Domain ← Infra`
  - [ ] Adapter 层仅依赖 Application 和 API
  - [ ] Application 层仅依赖 Domain 和 API
  - [ ] Infrastructure 层仅依赖 Domain
  - [ ] Domain 层不依赖任何其他模块
  - [ ] API 层无依赖（纯定义）

- [ ] **CHK-ARCH-004**: 可通过 ArchUnit 测试验证（如果已实施）
  - 测试文件: `patra-{service}-boot/src/test/java/.../ArchitectureTest.java`

### 包结构

- [ ] **CHK-ARCH-005**: Domain 层包结构符合 DDD 规范
  - [ ] `domain/model/` 包含聚合根、实体、值对象
  - [ ] `domain/event/` 包含领域事件
  - [ ] `domain/service/` 包含领域服务（如需要）
  - [ ] `domain/repository/` 包含仓储接口

- [ ] **CHK-ARCH-006**: Application 层包结构符合规范
  - [ ] `app/orchestrator/` 包含复杂编排器
  - [ ] `app/coordinator/` 包含简单协调器
  - [ ] `app/assembler/` 包含 DTO 组装器

- [ ] **CHK-ARCH-007**: Infrastructure 层包结构符合规范
  - [ ] `infra/repository/` 包含仓储实现
  - [ ] `infra/repository/mapper/` 包含 MyBatis Mapper
  - [ ] `infra/converter/` 包含 MapStruct 转换器

- [ ] **CHK-ARCH-008**: Adapter 层包结构符合规范
  - [ ] `adapter/controller/` 包含 REST 控制器
  - [ ] `adapter/listener/` 包含事件监听器（如需要）
  - [ ] `adapter/job/` 包含定时任务（如需要）

---

## 🎯 DDD 战术设计验证

### 聚合根与实体

- [ ] **CHK-DDD-001**: 识别出明确的聚合根
  - 聚合根列表: [列出所有聚合根]
  - [ ] 每个聚合根有唯一标识（ID）
  - [ ] 聚合根是公开的入口点

- [ ] **CHK-DDD-002**: 聚合边界清晰
  - [ ] 外部只能通过聚合根访问聚合内的实体
  - [ ] 不跨聚合直接访问实体
  - [ ] 跨聚合调用通过 Application 层

- [ ] **CHK-DDD-003**: 实体设计符合规范
  - [ ] 每个实体有唯一标识
  - [ ] 通过 ID 判断相等性
  - [ ] 实体属于某个聚合

### 值对象

- [ ] **CHK-DDD-004**: 值对象设计为不可变（immutable）
  - 值对象列表: [列出所有值对象]
  - [ ] 使用 Java `record` 实现（推荐）或不可变类
  - [ ] 通过属性判断相等性（无标识）
  - [ ] 构造时进行验证

### 领域事件

- [ ] **CHK-DDD-005**: 领域事件使用过去时命名
  - 事件列表: [列出所有领域事件]
  - [ ] 命名格式：`[名词][动词过去时]`（例如：`ArticleCreated`）
  - [ ] 事件不可变（使用 record 或 final 类）
  - [ ] 事件记录业务事实

### 仓储接口

- [ ] **CHK-DDD-006**: 仓储接口在 Domain 层定义
  - [ ] 接口位于 `domain/repository/` 包
  - [ ] 实现位于 `infra/repository/` 包
  - [ ] 每个聚合根有对应的仓储接口

---

## 📦 单一事实来源 (SSOT) 验证

### Provenance 配置

- [ ] **CHK-SSOT-001**: Provenance 配置从 `patra-registry` 获取
  - [ ] 无硬编码的 Provenance 配置
  - [ ] 使用 `patra-registry-api` 模块访问
  - [ ] 配置支持运行时更新

### 数据字典

- [ ] **CHK-SSOT-002**: 数据字典从 `patra-registry` 获取
  - 字典类型列表: [列出需要的数据字典]
  - [ ] 枚举值从 registry 动态加载
  - [ ] 无重复定义数据字典
  - [ ] 支持热更新

### 元数据和映射规则

- [ ] **CHK-SSOT-003**: 元数据和映射规则从 `patra-registry` 获取
  - [ ] 字段映射规则从 registry 获取
  - [ ] 解析规则支持版本化
  - [ ] 无硬编码映射规则

---

## 🔧 技术实现验证

### Spring Boot 配置

- [ ] **CHK-TECH-001**: 配置管理符合规范
  - [ ] `application.yml` 配置合理
  - [ ] `bootstrap.yml` 配置 Nacos
  - [ ] 敏感信息无硬编码（使用环境变量或 Nacos）

### MyBatis-Plus 使用

- [ ] **CHK-TECH-002**: MyBatis-Plus 使用符合规范
  - [ ] Mapper 位于 `infra/repository/mapper/`
  - [ ] 不在 Domain 层使用 MyBatis 注解
  - [ ] SQL 语句清晰可读

### MapStruct 使用

- [ ] **CHK-TECH-003**: MapStruct 使用符合规范
  - [ ] Converter 位于 `infra/converter/`
  - [ ] 映射规则清晰明确
  - [ ] 复杂映射有单元测试

---

## 📝 文档验证

### 模块文档

- [ ] **CHK-DOC-001**: 模块根目录有完整的 `README.md`
  - [ ] 包含模块职责说明
  - [ ] 包含主要功能列表
  - [ ] 包含快速开始指南
  - [ ] 包含依赖关系说明

### 包文档

- [ ] **CHK-DOC-002**: 重要的包有 `package-info.java`
  - [ ] `domain/model/` 包有文档
  - [ ] `domain/event/` 包有文档（如存在）
  - [ ] `app/orchestrator/` 包有文档（如存在）

### 语言规范

- [ ] **CHK-DOC-003**: 所有文档和注释使用中文
  - [ ] README.md 使用中文
  - [ ] JavaDoc 注释使用中文
  - [ ] package-info.java 使用中文
  - [ ] 代码标识符使用英文

---

## ✅ 检查清单摘要

**总计**: [检查项数量]
**通过**: [通过数量]
**失败**: [失败数量]
**N/A**: [不适用数量]

**整体状态**: [PASS / FAIL / PARTIAL]

---

## 📋 备注

- 检查项标记为完成: `[x]`
- 添加发现的问题和修复建议
- 链接到相关代码或文档
- 如有违规，必须在 plan.md 的 Complexity Tracking 章节说明理由
