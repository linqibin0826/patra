# Patra 项目架构宪章

**版本**: 1.0.0
**批准日期**: 2025-01-09
**最后修订**: 2025-01-09

---

## 核心原则

### I. 六边形架构（Hexagonal Architecture）

**定义**: 所有微服务必须严格遵循六边形架构（端口-适配器模式），将业务逻辑与技术细节隔离。

**强制规则**:

1. **模块结构** (MUST):
   - 每个微服务必须包含 6 个子模块：
     - `{service}-boot`: Spring Boot 启动模块
     - `{service}-api`: 契约层（DTO、接口定义）
     - `{service}-domain`: 领域层（**纯 Java，无框架依赖**）
     - `{service}-app`: 应用层（Orchestrator、Coordinator）
     - `{service}-infra`: 基础设施层（Repository 实现）
     - `{service}-adapter`: 适配器层（Controller、Listener、Job）

2. **依赖方向** (MUST):
   ```
   Adapter → Application → Domain ← Infrastructure
                             ↑
                            API
   ```
   - ✅ Adapter 可以依赖 Application 和 API
   - ✅ Application 可以依赖 Domain 和 API
   - ✅ Infrastructure 可以依赖 Domain
   - ❌ **Domain 绝不依赖任何其他模块**
   - ❌ **Domain 绝不依赖任何框架**（Spring、MyBatis 等）

3. **Domain 层纯净性** (MUST):
   - ✅ 允许的依赖：
     - JDK 标准库
     - `patra-common-util`（工具类）
     - Lombok
     - Validation API (javax.validation / jakarta.validation)
   - ❌ **禁止的依赖**：
     - Spring Framework（@Component、@Service、@Autowired 等）
     - MyBatis-Plus
     - Jackson
     - 任何持久化框架
   - ✅ 使用接口定义 Repository，实现在 Infrastructure 层

**好的实践**:
```java
// ✅ GOOD: Domain 层的聚合根（纯 Java）
package com.patra.ingest.domain.model;

public class Article {
    private ArticleId id;
    private String title;

    public void updateMetadata(Metadata metadata) {
        // 纯业务逻辑，无框架依赖
        this.metadata = metadata;
        this.addEvent(new ArticleUpdated(this.id));
    }
}
```

**坏的实践**:
```java
// ❌ BAD: Domain 层依赖 Spring（违反）
package com.patra.ingest.domain.model;

import org.springframework.stereotype.Component; // ❌ 禁止

@Component  // ❌ 禁止
public class Article {
    @Autowired  // ❌ 禁止
    private ArticleRepository repository;
}
```

---

### II. DDD 战术设计（Domain-Driven Design Tactical Patterns）

**定义**: 使用 DDD 战术模式组织领域模型。

**关键概念**:

1. **聚合根（Aggregate Root）**:
   - 每个聚合有唯一的聚合根
   - 外部只能通过聚合根访问聚合内的实体
   - 聚合根负责维护聚合内的一致性
   - 聚合根必须有唯一标识（ID）

2. **实体（Entity）**:
   - 有唯一标识（ID）
   - 通过 ID 判断相等性

3. **值对象（Value Object）**:
   - 无标识，通过属性判断相等性
   - 不可变（immutable）
   - 使用 record 实现（推荐）

4. **领域事件（Domain Event）**:
   - 记录已经发生的业务事实
   - 过去时命名（如 `ArticleCreated`，而非 `CreateArticle`）
   - 不可变

**包结构规范**:
```
{service}-domain/src/main/java/com/patra/{service}/domain/
├── model/                  # 聚合根、实体、值对象
│   ├── Article.java       # 聚合根
│   ├── ArticleId.java     # 值对象（ID）
│   └── Metadata.java      # 值对象
├── event/                  # 领域事件
│   ├── ArticleCreated.java
│   └── ArticleUpdated.java
├── service/                # 领域服务（跨聚合的业务逻辑）
│   └── ArticleValidator.java
└── repository/             # 仓储接口（实现在 infra 层）
    └── ArticleRepository.java
```

**好的实践**:
```java
// ✅ GOOD: 值对象（不可变）
public record ArticleId(String value) {
    public ArticleId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ArticleId 不能为空");
        }
    }
}

// ✅ GOOD: 领域事件
public record ArticleCreated(
    ArticleId articleId,
    Instant occurredAt
) {}
```

---

### III. 单一事实来源（Single Source of Truth - SSOT）

**定义**: `patra-registry` 是 Provenance 配置、数据字典、元数据的唯一权威来源。

**强制规则**:

1. **配置管理** (MUST):
   - ✅ 所有 Provenance（数据源）配置必须从 `patra-registry` 获取
   - ❌ 禁止在其他服务中硬编码 Provenance 配置
   - ✅ 使用 `patra-registry-api` 模块访问配置

2. **数据字典** (MUST):
   - ✅ 枚举值、分类体系必须从 `patra-registry` 获取
   - ❌ 禁止在业务服务中重复定义数据字典
   - ✅ 运行时动态加载，支持热更新

3. **元数据** (MUST):
   - ✅ 字段映射、解析规则必须从 `patra-registry` 获取
   - ✅ 支持版本化管理

---

### IV. 测试策略（Testing Strategy）

**定义**: 分层测试策略，确保不同层次的质量。

**强制规则**:

1. **Domain 层** (MUST):
   - ✅ 单元测试覆盖率目标 ≥ 80%
   - ✅ 使用 JUnit 5 + AssertJ
   - ❌ 不使用 Spring Test（Domain 层无 Spring 依赖）

2. **Application 层** (MUST):
   - ✅ 单元测试覆盖率目标 ≥ 70%
   - ✅ 使用 Mockito 模拟 Repository
   - ✅ 测试用例编排逻辑和事务边界

3. **Infrastructure 层** (MUST):
   - ✅ IT 集成测试（使用 TestContainers）
   - ✅ 使用 `@MockitoBean` 替代 `@MockBean`
   - ✅ 测试 MyBatis-Plus Mapper 的 SQL 正确性
   - ✅ 测试 MapStruct Converter 的映射完整性

4. **Adapter 层** (MUST):
   - ✅ E2E 测试（使用 MockMvc + TestContainers）
   - ✅ 测试 REST API 契约
   - ✅ 测试异常处理和错误响应

**测试文件命名**:
- 单元测试: `XxxTest.java`
- IT 集成测试: `XxxIT.java`
- E2E 测试: `XxxE2ETest.java`

**好的实践**:
```java
// ✅ GOOD: Domain 层单元测试（无 Spring）
class ArticleTest {
    @Test
    void should_emit_event_when_update_metadata() {
        // Given
        Article article = new Article(new ArticleId("123"), "Title");

        // When
        article.updateMetadata(new Metadata("New metadata"));

        // Then
        assertThat(article.getEvents())
            .hasSize(1)
            .first()
            .isInstanceOf(ArticleUpdated.class);
    }
}

// ✅ GOOD: Infrastructure 层 IT 测试
@SpringBootTest
@Testcontainers
class ArticleRepositoryIT {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    private ArticleRepository repository;

    @Test
    void should_save_and_find_article() {
        // ...
    }
}
```

---

### V. 文档标准（Documentation Standards）

**定义**: 高质量的中文文档是项目可维护性的基础。

**强制规则**:

1. **语言规范** (MUST):
   - ✅ 所有文档、注释使用中文（UTF-8 无 BOM）
   - ✅ 代码标识符（类名、方法名、变量名）使用英文
   - ✅ Git 提交信息使用中文

2. **模块文档** (MUST):
   - ✅ 每个 `patra-{service}` 根目录必须有 `README.md`
   - ✅ README 必须包含：
     - 模块职责说明
     - 主要功能列表
     - 快速开始指南
     - 依赖关系说明

3. **包文档** (SHOULD):
   - ✅ 重要的 Java 包应该有 `package-info.java`
   - ✅ 说明包的职责和主要类

4. **API 文档** (MUST):
   - ✅ 使用 Swagger/OpenAPI 3.0 注解
   - ✅ 所有公开 API 必须有中文描述

**好的实践**:
```java
// ✅ GOOD: package-info.java
/**
 * 文章聚合根和相关领域模型。
 *
 * <p>本包包含：
 * <ul>
 *   <li>{@link Article} - 文章聚合根</li>
 *   <li>{@link ArticleId} - 文章唯一标识（值对象）</li>
 *   <li>{@link Metadata} - 文章元数据（值对象）</li>
 * </ul>
 */
package com.patra.ingest.domain.model;
```

---

## Constitution Check 验证项

所有新功能在进入实施阶段前必须通过以下验证：

### 架构验证

- [ ] **CHK-ARCH-001**: 模块结构符合 6 层规范（boot/api/domain/app/infra/adapter）
- [ ] **CHK-ARCH-002**: Domain 层 `pom.xml` 无任何框架依赖（仅 JDK + patra-common-util + Lombok + Validation API）
- [ ] **CHK-ARCH-003**: 依赖方向符合 `Adapter → App → Domain ← Infra`

### DDD 验证

- [ ] **CHK-DDD-001**: 识别出明确的聚合根（Aggregate Root）
- [ ] **CHK-DDD-002**: 聚合边界清晰（不跨聚合直接访问实体）
- [ ] **CHK-DDD-003**: 值对象设计为不可变（immutable）
- [ ] **CHK-DDD-004**: 领域事件使用过去时命名

### SSOT 验证

- [ ] **CHK-SSOT-001**: Provenance 配置从 `patra-registry` 获取（无硬编码）
- [ ] **CHK-SSOT-002**: 数据字典从 `patra-registry` 获取
- [ ] **CHK-SSOT-003**: 元数据和映射规则从 `patra-registry` 获取

### 测试验证

- [ ] **CHK-TEST-001**: Domain 层单元测试覆盖率 ≥ 80%
- [ ] **CHK-TEST-002**: Application 层单元测试覆盖率 ≥ 70%
- [ ] **CHK-TEST-003**: Infrastructure 层有 IT 集成测试
- [ ] **CHK-TEST-004**: Adapter 层有 E2E 测试

### 文档验证

- [ ] **CHK-DOC-001**: 模块根目录有 `README.md`
- [ ] **CHK-DOC-002**: 所有文档和注释使用中文
- [ ] **CHK-DOC-003**: API 有 Swagger 文档

---

## 治理规则

### 宪章修订

- 宪章修订需要技术负责人批准
- 修订需要更新版本号（MAJOR.MINOR.PATCH）
- 重大修订需要记录变更原因

### 违规处理

1. **Constitution Check 失败**:
   - ❌ 阻止进入实施阶段
   - ✅ 必须在 `plan.md` 的 Complexity Tracking 章节说明理由

2. **架构测试失败**:
   - ❌ 阻止合并到主分支
   - ✅ 必须修复架构违规

---

**版本**: 1.0.0
**批准日期**: 2025-01-09
**最后修订**: 2025-01-09
