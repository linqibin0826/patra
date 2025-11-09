# 功能规格说明: 测试基础设施模块重构

**特性分支**: `001-test-infrastructure-refactor`
**创建日期**: 2025-11-09
**状态**: 草稿
**输入**: 用户描述: "测试基础设施模块重构 - 统一管理测试依赖、测试工具类和测试配置,提供可复用的测试基础设施"

## 澄清

### 会话 2025-11-09

- 问: 当开发者本地没有Docker环境时,集成测试应该如何处理? → 答: 直接失败并提示安装Docker
- 问: 当测试失败时(如TestContainers启动失败、断言失败),应该输出什么级别的调试信息? → 答: 提供可配置的日志级别(如logging.level.com.patra.test=DEBUG)
- 问: TestContainers需要的最低Docker版本是多少? → 答: Docker 20.10+
- 问: TestContainers(MySQL/Redis/Nacos)的资源限制(内存、CPU)应该如何配置? → 答: 使用TestContainers默认配置,不设置资源限制

## 用户场景与测试

### 用户故事 1 - 单元测试工具类复用 (优先级: P1)

开发者在任意模块(Domain/App/Infra/Adapter)编写单元测试时,可以直接使用统一的测试工具类(如 TestDataBuilder、MockDataFactory、AssertionHelper),无需在每个模块中重复编写相同的测试辅助代码。

**为什么是这个优先级**: 这是最基础的测试基础设施,影响所有模块的单元测试开发效率。单元测试是测试金字塔的基础,数量最多,复用性最强。

**独立测试**: 可以通过在任意模块(如 patra-registry-domain)中引入 patra-common-test,使用 TestDataBuilder 创建测试数据,验证工具类可用性和便利性。

**验收场景**:

1. **假设** 开发者在 patra-registry-domain 模块编写单元测试, **当** 引入 patra-common-test 依赖并使用 TestDataBuilder.aUser().build(), **那么** 可以快速创建测试用户对象,无需手动构造
2. **假设** 开发者在 patra-ingest-app 模块编写业务逻辑测试, **当** 使用 DomainAssertions 验证聚合根状态, **那么** 可以获得更清晰的断言失败信息
3. **假设** 多个模块都使用 patra-common-test, **当** 升级测试工具类版本, **那么** 只需在 patra-parent 中统一升级版本号,所有模块自动生效

---

### 用户故事 2 - 集成测试环境自动配置 (优先级: P1)

开发者在 patra-{service}-boot 模块编写集成测试(IT)或 E2E 测试时,只需引入 patra-spring-boot-starter-test 依赖,即可自动获得 TestContainers(MySQL, Redis, Nacos)、MockMvc、WireMock 等测试环境配置,无需手动编写复杂的测试配置代码。

**为什么是这个优先级**: 集成测试和 E2E 测试是验证系统正确性的关键环节,自动配置可以显著降低测试环境搭建的复杂度和出错概率。

**独立测试**: 可以通过在 patra-registry-boot 模块编写一个简单的集成测试,继承 BaseIntegrationTest 基类,验证 MySQL TestContainer 自动启动,数据库操作正常执行。

**验收场景**:

1. **假设** 开发者在 patra-registry-boot 编写集成测试类 RegistryRepositoryIT, **当** 继承 BaseIntegrationTest 基类并执行测试, **那么** MySQL TestContainer 自动启动,测试数据可以正常插入和查询
2. **假设** 开发者在 patra-ingest-boot 编写 E2E 测试类 IngestFlowE2E, **当** 继承 BaseE2ETest 并使用 @Autowired MockMvc, **那么** 可以发起 HTTP 请求并验证完整的采集流程
3. **假设** 开发者需要测试外部 API 调用(如 PubMed API), **当** 使用 WireMock 自动配置, **那么** 可以模拟外部 API 响应,无需连接真实的外部服务

---

### 用户故事 3 - 测试依赖版本统一管理 (优先级: P2)

架构师和技术负责人可以在 patra-parent 的 dependencyManagement 中统一管理所有测试依赖的版本(JUnit, Mockito, AssertJ, TestContainers 等),确保所有模块使用相同版本的测试框架,避免版本冲突和不一致性。

**为什么是这个优先级**: 版本管理是测试基础设施的质量保障,但不直接影响日常开发。统一管理后可以降低维护成本,避免潜在的版本冲突问题。

**独立测试**: 可以通过检查 patra-parent 的 pom.xml 中 dependencyManagement 配置,验证所有测试依赖版本都在此集中定义。然后在多个模块的 pom.xml 中验证测试依赖无需指定 version。

**验收场景**:

1. **假设** 需要升级 TestContainers 版本, **当** 在 patra-parent 的 dependencyManagement 中修改版本号, **那么** 所有引用 TestContainers 的模块自动使用新版本,无需逐个修改
2. **假设** 新增一个微服务模块 patra-analysis, **当** 在其 boot 模块引入 patra-spring-boot-starter-test 依赖, **那么** 自动继承所有测试依赖的版本配置,无需手动指定版本号
3. **假设** 两个开发者在不同模块同时开发, **当** 他们都引入 Mockito 依赖, **那么** 使用的是相同的 Mockito 版本,不会因版本不一致导致测试行为差异

---

### 用户故事 4 - 新模块快速搭建测试环境 (优先级: P3)

新团队成员或开发者创建新的微服务模块时,只需在 pom.xml 中引入 patra-spring-boot-starter-test(boot 模块)和 patra-common-test(其他模块),即可获得完整的测试基础设施,无需学习和复制现有模块的测试配置。

**为什么是这个优先级**: 这是前面三个用户故事的综合效果,主要体现在降低学习成本和提升新模块创建效率上,优先级相对较低。

**独立测试**: 可以通过创建一个全新的示例微服务模块(如 patra-demo),仅引入测试基础设施依赖,验证可以立即编写和运行单元测试、集成测试和 E2E 测试。

**验收场景**:

1. **假设** 新成员加入团队并负责开发新模块 patra-export, **当** 参照 README 文档引入测试依赖, **那么** 可以在 30 分钟内编写并运行第一个单元测试和集成测试
2. **假设** 开发者创建新的领域层 patra-export-domain, **当** 仅引入 patra-common-test 依赖, **那么** 可以编写纯 Java 单元测试,无需依赖 Spring 框架
3. **假设** 开发者创建新的 boot 模块 patra-export-boot, **当** 引入 patra-spring-boot-starter-test, **那么** TestContainers、MockMvc、WireMock 等测试环境自动可用,无需额外配置

---

### 边界情况

- **当现有模块已有自定义测试工具类时会发生什么?** 迁移时保留业务特定的测试用例和 Mock 对象,仅迁移通用的测试工具类(如 TestDataBuilder、AssertionHelper)。业务特定的测试代码继续保留在原模块中。
- **当 TestContainers 启动失败时系统如何处理?** 测试基类应提供清晰的错误信息,指引开发者检查 Docker 环境和网络连接。测试将直接失败,并提示开发者安装和启动 Docker,确保本地环境满足集成测试的前置条件。
- **当某个模块需要特殊的测试配置时如何处理?** patra-spring-boot-starter-test 提供的是默认配置,模块可以通过 @TestConfiguration 覆盖或扩展默认配置,如自定义 TestContainers 初始化参数。
- **当迁移后原有测试用例失败时如何处理?** 分阶段迁移,先在单个模块验证,确保所有测试通过后再推广到其他模块。如果发现兼容性问题,优先修复测试基础设施,而非回退。

## 需求

### 功能需求

- **FR-001**: 系统必须在 patra-common 下创建子模块 patra-common-test,提供纯 Java 测试工具类(TestDataBuilder、MockDataFactory、AssertionHelper、DomainAssertions、BaseUnitTest、TestConstants)
- **FR-002**: 系统必须在项目根目录创建 patra-spring-boot-starter-test 模块,提供 Spring Boot 自动配置的测试基础设施(TestContainers、MockMvc、WireMock、BaseIntegrationTest、BaseE2ETest)
- **FR-003**: patra-common-test 模块必须仅依赖纯 Java 测试框架(JUnit 5、AssertJ、Mockito),不得引入任何 Spring 框架依赖
- **FR-004**: patra-spring-boot-starter-test 模块必须依赖 patra-common-test,并提供 Spring Boot Starter 自动配置机制
- **FR-005**: 系统必须将 patra-registry-boot、patra-ingest-app、patra-gateway-boot 等模块中的通用测试配置迁移到 patra-spring-boot-starter-test
- **FR-006**: 系统必须将各模块中的通用测试工具类迁移到 patra-common-test,删除原模块中的重复代码
- **FR-007**: 系统必须更新所有模块的 pom.xml,在 patra-{service}-domain/app/infra/adapter 中引入 patra-common-test,在 patra-{service}-boot 中引入 patra-spring-boot-starter-test
- **FR-008**: 系统必须在 patra-parent 的 dependencyManagement 中统一管理所有测试依赖版本(JUnit 5、Mockito 5.x、AssertJ 3.x、TestContainers 1.19.x)
- **FR-009**: 系统必须为 patra-common-test 和 patra-spring-boot-starter-test 提供清晰的使用文档(README.md)和包说明(package-info.java)
- **FR-010**: 系统必须为常用测试场景提供示例代码,包括单元测试、集成测试(IT)、E2E 测试的完整示例
- **FR-011**: patra-spring-boot-starter-test 必须使用 @TestConfiguration 管理测试专用 Bean,避免污染生产环境配置

### 非功能需求

**架构约束**:
- **六边形架构层次**: 此功能主要涉及测试基础设施层,不直接属于六边形架构的 Domain/Application/Infrastructure/Adapter 层,但服务于所有层的测试需求
- **依赖方向**: patra-common-test 作为底层测试工具库,不依赖任何业务模块。patra-spring-boot-starter-test 依赖 patra-common-test,但不依赖任何业务模块。业务模块通过 test scope 引入测试基础设施
- **SSOT 遵守**: 所有测试依赖版本在 patra-parent 的 dependencyManagement 中统一定义,作为测试依赖版本的单一事实来源(SSOT)

**性能要求**:
- **NFR-001**: 引入测试基础设施后,单元测试启动时间增加不超过原有时间的 5%
- **NFR-002**: TestContainers(MySQL/Redis/Nacos)总启动时间不超过 10 秒,使用TestContainers默认资源配置,不设置自定义资源限制(内存、CPU)
- **NFR-003**: 测试工具类(TestDataBuilder、MockDataFactory)创建测试数据的性能开销可忽略不计(微秒级)

**兼容性要求**:
- **NFR-004**: 迁移后所有现有测试用例必须通过,测试覆盖率不得降低
- **NFR-005**: 测试基础设施必须兼容 Java 25、Spring Boot 3.5.7、Spring Cloud 2025.0.0
- **NFR-006**: TestContainers 必须支持主流操作系统(macOS、Linux、Windows with WSL2),要求 Docker 20.10+ 版本

**可维护性要求**:
- **NFR-007**: 测试基础设施模块必须提供清晰的版本管理和升级指南
- **NFR-008**: 每个测试工具类必须有充分的 JavaDoc 注释和使用示例

**可观察性要求**:
- **NFR-009**: 测试基础设施必须支持可配置的日志级别,开发者可以通过配置(如`logging.level.com.patra.test=DEBUG`)调整测试执行时的日志详细程度
- **NFR-010**: 当TestContainers启动失败时,必须输出清晰的错误信息,包括容器类型、失败原因和建议的排查步骤
- **NFR-011**: 测试基类必须在DEBUG级别输出测试上下文信息(Spring Beans加载状态、TestContainers配置、数据库连接信息),便于问题排查

### 领域模型

此功能为测试基础设施重构,不涉及业务领域模型。主要涉及的是测试技术架构和工具抽象:

**测试工具抽象**:
- **TestDataBuilder**: 测试数据构建器,提供链式 API 构造测试对象(如 User、Article)
- **MockDataFactory**: Mock 数据工厂,批量生成测试数据(如生成 100 个随机用户)
- **DomainAssertions**: 领域断言工具,提供业务语义化的断言(如 assertUserIsActive、assertArticleIsPublished)

**测试配置抽象**:
- **BaseIntegrationTest**: 集成测试基类,自动配置 TestContainers 和 Spring Context
- **BaseE2ETest**: E2E 测试基类,自动配置 MockMvc 和完整的应用上下文
- **TestContainersConfiguration**: TestContainers 自动配置类,管理 MySQL/Redis/Nacos 容器生命周期

**SSOT 提示** ⚠️:
此功能不涉及以下内容,无需从 patra-registry 获取:
- [ ] Provenance 配置
- [ ] 数据字典
- [ ] 元数据

## 成功标准

### 可衡量的结果

- **SC-001**: 开发者在任意模块引入 patra-common-test 后,可以在 5 分钟内使用 TestDataBuilder 编写并运行第一个单元测试
- **SC-002**: 开发者在 patra-{service}-boot 模块引入 patra-spring-boot-starter-test 后,可以在 10 分钟内编写并运行第一个集成测试,无需手动配置 TestContainers
- **SC-003**: 所有现有测试用例(单元测试、集成测试、E2E 测试)在迁移后全部通过,测试覆盖率保持不变或提升
- **SC-004**: 新创建的微服务模块可以在 30 分钟内搭建完整的测试环境,包括单元测试、集成测试和 E2E 测试框架
- **SC-005**: 测试依赖版本升级时,只需修改 patra-parent 中的版本号,所有模块(10+ 个模块)自动生效,无需逐个修改
- **SC-006**: 测试代码重复率降低 70% 以上,通用测试工具类和配置都集中在测试基础设施模块中
- **SC-007**: TestContainers 启动时间在 10 秒以内,单元测试启动时间增加小于 5%
- **SC-008**: 90% 的开发者在使用测试基础设施后,反馈编写测试的效率提升明显,降低了测试环境搭建的复杂度

## 假设

1. **假设** 所有开发者的本地环境已安装 Docker 并正常运行,TestContainers 可以正常启动容器
2. **假设** 现有模块中的测试工具类足够通用,可以直接迁移到 patra-common-test,不需要大量定制化修改
3. **假设** 项目使用 Maven 作为构建工具,熟悉 Maven 的 dependencyManagement 和 Spring Boot Starter 自动配置机制
4. **假设** 开发团队遵循测试金字塔原则:单元测试(70%) > 集成测试(20%) > E2E 测试(10%),因此单元测试工具的优先级最高
5. **假设** 所有模块都遵循统一的测试命名约定:单元测试(*Test.java)、集成测试(*IT.java)、E2E 测试(*E2E.java)
6. **假设** 测试基础设施模块的升级频率较低(每季度 1-2 次),不会频繁引入破坏性变更

## 范围界限

### 范围内

1. 创建 patra-common-test 和 patra-spring-boot-starter-test 两个新模块
2. 迁移现有模块中的通用测试工具类和测试配置
3. 更新所有模块的测试依赖,引入新的测试基础设施
4. 在 patra-parent 中统一管理测试依赖版本
5. 提供测试基础设施的使用文档和示例代码
6. 验证所有现有测试用例在迁移后仍然通过

### 范围外

1. 不包括为现有模块编写新的测试用例(仅迁移测试基础设施,不增加测试覆盖率)
2. 不包括测试报告和测试覆盖率可视化工具的集成(如 JaCoCo 报告聚合)
3. 不包括性能测试框架的集成(如 JMeter、Gatling)
4. 不包括测试数据管理工具的集成(如 DBUnit、Testcontainers 初始化 SQL 脚本管理)
5. 不包括 CI/CD 流水线中的测试执行策略优化(如并行测试、测试分片)
6. 不包括测试环境的云端化部署(如基于 Kubernetes 的测试环境)

## 依赖

### 外部依赖

1. **Docker 环境**: 开发者本地需要安装 Docker 20.10+ 版本,用于运行 TestContainers。支持 macOS、Linux、Windows with WSL2
2. **Maven 仓库**: 需要能够访问 Maven 中央仓库,下载测试依赖(JUnit、Mockito、TestContainers 等)
3. **Spring Boot 自动配置机制**: 依赖 Spring Boot 的自动配置特性,实现 patra-spring-boot-starter-test 的自动配置

### 内部依赖

1. **patra-parent**: 在其 dependencyManagement 中统一定义所有测试依赖版本
2. **patra-common**: patra-common-test 作为其子模块,继承其基础配置
3. **所有业务模块**: 需要更新 pom.xml,引入新的测试基础设施依赖

### 前置条件

1. 在开始迁移前,需要全面梳理现有模块中的测试工具类和测试配置,识别哪些是通用的(可迁移),哪些是业务特定的(保留)
2. 需要确保所有现有测试用例都能在当前环境下正常运行,建立迁移前的基线
3. 需要准备回滚方案,如果迁移后出现大量测试失败,可以快速恢复到迁移前的状态

## 风险

1. **测试用例兼容性风险(高)**: 迁移后可能导致部分测试用例失败,需要逐个分析和修复
   - **缓解措施**: 分模块逐步迁移,先在单个模块验证成功后再推广到其他模块
2. **TestContainers 环境依赖风险(中)**: 部分开发者本地可能没有 Docker 环境,导致集成测试无法运行
   - **缓解措施**: 在文档中明确说明 Docker 环境为强制要求,提供详细的 Docker 安装指南和环境验证步骤。测试失败时提供清晰的错误信息,指引开发者安装 Docker
3. **版本冲突风险(中)**: 统一测试依赖版本后,可能与某些模块现有的测试依赖版本冲突
   - **缓解措施**: 在 patra-parent 中优先选择最新稳定版本,迁移时逐个模块验证兼容性
4. **学习曲线风险(低)**: 开发者需要学习新的测试基础设施使用方法
   - **缓解措施**: 提供详细的文档和示例代码,组织内部培训或分享会
5. **性能回归风险(低)**: 引入新的测试基础设施可能导致测试启动时间增加
   - **缓解措施**: 在迁移前后对比测试启动时间,如果增加超过 5%,需要优化测试基类的初始化逻辑

## 后续工作

1. **测试覆盖率提升**: 在测试基础设施完善后,推动各模块提升单元测试和集成测试覆盖率
2. **测试报告聚合**: 集成 JaCoCo 或 SonarQube,实现多模块测试覆盖率报告聚合
3. **性能测试框架**: 引入性能测试框架(如 Gatling),为关键业务流程提供性能基准测试
4. **测试数据管理**: 引入测试数据管理工具,如 DBUnit 或 Testcontainers 的 SQL 初始化脚本管理
5. **CI/CD 集成**: 在 CI/CD 流水线中优化测试执行策略,如并行测试、测试分片,缩短流水线执行时间
6. **契约测试**: 引入契约测试框架(如 Spring Cloud Contract),为微服务间的 API 契约提供自动化测试
