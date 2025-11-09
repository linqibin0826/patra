# 任务: 测试基础设施模块重构

**输入**: 来自 `/specs/001-test-infrastructure-refactor/` 的设计文档
**前置条件**: plan.md、spec.md、test-infrastructure-model.md、test-api.md、quickstart.md

**组织**: 任务按用户故事分组,以实现每个故事的独立实施和测试。

## 格式: `[ID] [P?] [Layer?] [Story] 描述`

- **[P]**: 可并行运行(不同文件,无依赖)
- **[Layer]**: 测试基础设施层标签
  - `[Core]`: 核心工具库(patra-common-test)
  - `[Starter]`: Spring Boot Starter(patra-spring-boot-starter-test)
  - `[Config]`: 配置和依赖管理
  - `[Meta]`: 元测试(测试测试工具)
  - `[Example]`: 示例测试代码
- **[Story]**: 此任务属于哪个用户故事(例如,US1、US2、US3)

---

## 阶段 1: 设置(共享基础设施)

**目的**: 创建测试基础设施模块骨架和依赖管理

- [X] T001 在 patra-common 下创建 patra-common-test 子模块,初始化 pom.xml
- [X] T002 [P] 创建 patra-spring-boot-starter-test 模块,初始化 pom.xml
- [X] T003 [P] 在 patra-parent 的 dependencyManagement 中添加测试依赖版本管理(JUnit 5、Mockito 5.x、AssertJ 3.x、TestContainers 1.19.x)

---

## 阶段 2: 基础(阻塞前置条件)

**目的**: 创建核心测试工具类和配置,所有用户故事依赖的基础

**⚠️ 关键**: 在此阶段完成之前,不能开始任何用户故事工作

### 配置和依赖

- [X] T004 [P] [Config] 配置 patra-common-test 的 pom.xml 依赖(JUnit 5、Mockito、AssertJ、Lombok)
- [X] T005 [P] [Config] 配置 patra-spring-boot-starter-test 的 pom.xml 依赖(patra-common-test、Spring Boot Test、TestContainers)
- [X] T006 [Config] 创建 Spring Boot 自动配置文件 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

### 核心工具类骨架

- [X] T007 [P] [Core] 创建 TestDataBuilder 抽象基类 in patra-common-test/src/main/java/com/patra/common/test/builder/TestDataBuilder.java
- [X] T008 [P] [Core] 创建 MockDataFactory 工具类 in patra-common-test/src/main/java/com/patra/common/test/factory/MockDataFactory.java
- [X] T009 [P] [Core] 创建 DomainAssertions 断言工具类 in patra-common-test/src/main/java/com/patra/common/test/assertion/DomainAssertions.java
- [X] T010 [P] [Core] 创建 AssertionHelper 通用断言工具类 in patra-common-test/src/main/java/com/patra/common/test/assertion/AssertionHelper.java
- [X] T011 [P] [Core] 创建 BaseUnitTest 单元测试基类 in patra-common-test/src/main/java/com/patra/common/test/base/BaseUnitTest.java
- [X] T012 [P] [Core] 创建 TestConstants 测试常量类 in patra-common-test/src/main/java/com/patra/common/test/constant/TestConstants.java

### TestContainers 容器定义

- [X] T013 [P] [Starter] 创建 MySQLTestContainer 容器类 in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/container/MySQLTestContainer.java
- [X] T014 [P] [Starter] 创建 RedisTestContainer 容器类 in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/container/RedisTestContainer.java
- [X] T015 [P] [Starter] 创建 NacosTestContainer 容器类 in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/container/NacosTestContainer.java

### 自动配置和测试基类

- [X] T016 [Starter] 创建 TestcontainersConfiguration 自动配置类 in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/config/TestcontainersConfiguration.java(依赖 T013-T015)
- [X] T017 [P] [Starter] 创建 TestContainersAutoConfiguration 自动配置类 in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/autoconfigure/TestContainersAutoConfiguration.java
- [X] T018 [P] [Starter] 创建 MockMvcAutoConfiguration 自动配置类 in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/autoconfigure/MockMvcAutoConfiguration.java
- [X] T019 [P] [Starter] 创建 WireMockAutoConfiguration 自动配置类 in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/autoconfigure/WireMockAutoConfiguration.java
- [X] T020 [Starter] 创建 BaseIntegrationTest 集成测试基类 in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/base/BaseIntegrationTest.java(依赖 T016)
- [X] T021 [Starter] 创建 BaseE2ETest E2E测试基类 in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/base/BaseE2ETest.java(依赖 T016)

**检查点**: 基础就绪 - 现在可以开始并行实施用户故事

---

## 阶段 3: 用户故事 1 - 单元测试工具类复用 (优先级: P1) 🎯 MVP

**目标**: 开发者在任意模块编写单元测试时,可以直接使用统一的测试工具类(TestDataBuilder、MockDataFactory、AssertionHelper),无需重复编写测试辅助代码

**独立测试**: 在任意模块(如 patra-registry-domain)中引入 patra-common-test,使用 TestDataBuilder 创建测试数据,验证工具类可用性和便利性

### 用户故事 1 的测试(TDD) ⚠️

> **注意: 首先编写这些测试,确保在实施之前它们失败**

- [ ] T022 [P] [Meta] [US1] 为 TestDataBuilder 编写单元测试 in patra-common-test/src/test/java/com/patra/common/test/builder/TestDataBuilderTest.java
- [ ] T023 [P] [Meta] [US1] 为 MockDataFactory 编写单元测试 in patra-common-test/src/test/java/com/patra/common/test/factory/MockDataFactoryTest.java
- [ ] T024 [P] [Meta] [US1] 为 DomainAssertions 编写单元测试 in patra-common-test/src/test/java/com/patra/common/test/assertion/DomainAssertionsTest.java
- [ ] T025 [P] [Meta] [US1] 为 AssertionHelper 编写单元测试 in patra-common-test/src/test/java/com/patra/common/test/assertion/AssertionHelperTest.java
- [ ] T026 [P] [Meta] [US1] 为 BaseUnitTest 编写示例测试 in patra-common-test/src/test/java/com/patra/common/test/base/BaseUnitTestExample.java

### 用户故事 1 的实施

**按依赖顺序执行: MockDataFactory → TestDataBuilder → Assertions → BaseUnitTest**

#### 实现 MockDataFactory 核心方法

- [ ] T027 [P] [Core] [US1] 实现 MockDataFactory.randomString() 方法
- [ ] T028 [P] [Core] [US1] 实现 MockDataFactory.randomUuid() 方法
- [ ] T029 [P] [Core] [US1] 实现 MockDataFactory.randomInt() 方法
- [ ] T030 [P] [Core] [US1] 实现 MockDataFactory.randomLong() 方法
- [ ] T031 [P] [Core] [US1] 实现 MockDataFactory.randomDateTime() 方法
- [ ] T032 [P] [Core] [US1] 实现 MockDataFactory.randomDate() 方法
- [ ] T033 [P] [Core] [US1] 实现 MockDataFactory.randomEnum() 方法
- [ ] T034 [P] [Core] [US1] 实现 MockDataFactory.randomBoolean() 方法
- [ ] T035 [P] [Core] [US1] 实现 MockDataFactory.randomEmail() 方法
- [ ] T036 [P] [Core] [US1] 实现 MockDataFactory.randomUrl() 方法

#### 实现 TestDataBuilder 核心方法

- [ ] T037 [Core] [US1] 实现 TestDataBuilder.build() 抽象方法定义(依赖 T027-T036)
- [ ] T038 [Core] [US1] 实现 TestDataBuilder.buildList() 方法
- [ ] T039 [Core] [US1] 实现 TestDataBuilder.buildAndSave() 方法
- [ ] T040 [Core] [US1] 实现 TestDataBuilder.reset() 方法

#### 实现 DomainAssertions 核心方法

- [ ] T041 [P] [Core] [US1] 实现 DomainAssertions.assertAggregateStatus() 方法
- [ ] T042 [P] [Core] [US1] 实现 DomainAssertions.assertDomainEventPublished() 方法
- [ ] T043 [P] [Core] [US1] 实现 DomainAssertions.assertValueObjectEquals() 方法
- [ ] T044 [P] [Core] [US1] 实现 DomainAssertions.assertCollectionSize() 方法
- [ ] T045 [P] [Core] [US1] 实现 DomainAssertions.assertEntityEquals() 方法
- [ ] T046 [P] [Core] [US1] 实现 DomainAssertions.assertRepositorySaved() 方法

#### 实现 AssertionHelper 和 BaseUnitTest

- [ ] T047 [P] [Core] [US1] 实现 AssertionHelper 核心断言方法
- [ ] T048 [Core] [US1] 实现 BaseUnitTest.verifyMockInteraction() 方法(依赖 T011)
- [ ] T049 [Core] [US1] 实现 BaseUnitTest.resetMocks() 方法
- [ ] T050 [Core] [US1] 为 BaseUnitTest 添加 TestWatcher 规则

#### 验证测试通过

- [ ] T051 [US1] 运行所有元测试,确保 patra-common-test 的测试覆盖率 ≥ 80%

**检查点**: 此时,patra-common-test 模块完全可用,开发者可以在任意模块的单元测试中使用

---

## 阶段 4: 用户故事 2 - 集成测试环境自动配置 (优先级: P1)

**目标**: 开发者在 patra-{service}-boot 模块编写集成测试(IT)或 E2E 测试时,只需引入 patra-spring-boot-starter-test 依赖,即可自动获得 TestContainers(MySQL, Redis, Nacos)、MockMvc、WireMock 等测试环境配置

**独立测试**: 在 patra-registry-boot 模块编写一个简单的集成测试,继承 BaseIntegrationTest 基类,验证 MySQL TestContainer 自动启动,数据库操作正常执行

### 用户故事 2 的测试(TDD) ⚠️

> **注意: 首先编写这些测试,确保在实施之前它们失败**

- [ ] T052 [P] [Meta] [US2] 为 TestContainersAutoConfiguration 编写集成测试 in patra-spring-boot-starter-test/src/test/java/com/patra/spring/boot/starter/test/autoconfigure/TestContainersAutoConfigurationTest.java
- [ ] T053 [P] [Meta] [US2] 为 BaseIntegrationTest 编写元测试 in patra-spring-boot-starter-test/src/test/java/com/patra/spring/boot/starter/test/base/BaseIntegrationTestTest.java
- [ ] T054 [P] [Meta] [US2] 为 BaseE2ETest 编写元测试 in patra-spring-boot-starter-test/src/test/java/com/patra/spring/boot/starter/test/base/BaseE2ETestTest.java

### 用户故事 2 的实施

**按依赖顺序执行: Containers → Configuration → AutoConfiguration → BaseTests**

#### 实现 TestContainers 容器

- [ ] T055 [P] [Starter] [US2] 实现 MySQLTestContainer.start() 方法(MySQL 8.0.36 + tmpfs + Reusable)
- [ ] T056 [P] [Starter] [US2] 实现 MySQLTestContainer.getConnectionUrl() 方法
- [ ] T057 [P] [Starter] [US2] 实现 MySQLTestContainer.isHealthy() 方法
- [ ] T058 [P] [Starter] [US2] 实现 RedisTestContainer.start() 方法(Redis 7-alpine + Reusable)
- [ ] T059 [P] [Starter] [US2] 实现 RedisTestContainer.getConnectionString() 方法
- [ ] T060 [P] [Starter] [US2] 实现 NacosTestContainer.start() 方法(Nacos 2.3.0 standalone + Reusable)
- [ ] T061 [P] [Starter] [US2] 实现 NacosTestContainer.getServerAddr() 方法
- [ ] T062 [Starter] [US2] 实现 NacosTestContainer 健康检查(Wait.forHttp)

#### 实现 TestcontainersConfiguration

- [ ] T063 [Starter] [US2] 实现 TestcontainersConfiguration.mysqlContainer() Bean(依赖 T055-T057)
- [ ] T064 [Starter] [US2] 实现 TestcontainersConfiguration.redisContainer() Bean(依赖 T058-T059)
- [ ] T065 [Starter] [US2] 实现 TestcontainersConfiguration.nacosContainer() Bean(依赖 T060-T062)
- [ ] T066 [Starter] [US2] 为 Nacos 容器添加 @DynamicPropertySource 配置方法

#### 实现自动配置

- [ ] T067 [P] [Starter] [US2] 实现 TestContainersAutoConfiguration 条件化配置(@ConditionalOnClass, @ConditionalOnMissingBean)
- [ ] T068 [P] [Starter] [US2] 实现 MockMvcAutoConfiguration 自动配置
- [ ] T069 [P] [Starter] [US2] 实现 WireMockAutoConfiguration 自动配置
- [ ] T070 [Starter] [US2] 在 AutoConfiguration.imports 中注册所有自动配置类(依赖 T067-T069)

#### 实现测试基类

- [ ] T071 [Starter] [US2] 实现 BaseIntegrationTest.cleanRedis() 方法(依赖 T063-T065)
- [ ] T072 [Starter] [US2] 实现 BaseIntegrationTest.executeSqlScript() 方法
- [ ] T073 [Starter] [US2] 实现 BaseIntegrationTest.contextLoads() 测试方法
- [ ] T074 [Starter] [US2] 实现 BaseE2ETest.performGet() 方法
- [ ] T075 [Starter] [US2] 实现 BaseE2ETest.performPost() 方法
- [ ] T076 [Starter] [US2] 实现 BaseE2ETest.performPut() 方法
- [ ] T077 [Starter] [US2] 实现 BaseE2ETest.performDelete() 方法
- [ ] T078 [Starter] [US2] 实现 BaseE2ETest.extractJsonValue() 方法

#### 验证测试通过

- [ ] T079 [US2] 运行所有元测试,确保 patra-spring-boot-starter-test 的集成测试通过,TestContainers 启动成功

**检查点**: 此时,patra-spring-boot-starter-test 模块完全可用,开发者可以在 boot 模块中编写集成测试和 E2E 测试

---

## 阶段 5: 用户故事 3 - 测试依赖版本统一管理 (优先级: P2)

**目标**: 架构师和技术负责人可以在 patra-parent 的 dependencyManagement 中统一管理所有测试依赖的版本(JUnit, Mockito, AssertJ, TestContainers 等),确保所有模块使用相同版本的测试框架

**独立测试**: 检查 patra-parent 的 pom.xml 中 dependencyManagement 配置,验证所有测试依赖版本都在此集中定义。然后在多个模块的 pom.xml 中验证测试依赖无需指定 version

### 用户故事 3 的实施

**按依赖顺序执行: Parent → Common-Test → Starter-Test**

- [ ] T080 [P] [Config] [US3] 在 patra-parent 的 dependencyManagement 中添加 JUnit 5.11.x 版本管理
- [ ] T081 [P] [Config] [US3] 在 patra-parent 的 dependencyManagement 中添加 Mockito 5.x 版本管理
- [ ] T082 [P] [Config] [US3] 在 patra-parent 的 dependencyManagement 中添加 AssertJ 3.x 版本管理
- [ ] T083 [P] [Config] [US3] 在 patra-parent 的 dependencyManagement 中添加 TestContainers 1.19.x 版本管理
- [ ] T084 [P] [Config] [US3] 在 patra-parent 的 dependencyManagement 中添加 Spring Boot Test 3.5.7 版本管理
- [ ] T085 [P] [Config] [US3] 在 patra-parent 的 dependencyManagement 中添加 patra-common-test 版本管理
- [ ] T086 [P] [Config] [US3] 在 patra-parent 的 dependencyManagement 中添加 patra-spring-boot-starter-test 版本管理
- [ ] T087 [Config] [US3] 验证 patra-common-test 的 pom.xml 中测试依赖无需指定 version(依赖 T080-T084)
- [ ] T088 [Config] [US3] 验证 patra-spring-boot-starter-test 的 pom.xml 中测试依赖无需指定 version(依赖 T085-T086)

**检查点**: 此时,所有测试依赖版本在 patra-parent 中统一管理,所有模块自动继承版本配置

---

## 阶段 6: 用户故事 4 - 新模块快速搭建测试环境 (优先级: P3)

**目标**: 新团队成员或开发者创建新的微服务模块时,只需在 pom.xml 中引入 patra-spring-boot-starter-test(boot 模块)和 patra-common-test(其他模块),即可获得完整的测试基础设施

**独立测试**: 创建一个全新的示例微服务模块(如 patra-demo),仅引入测试基础设施依赖,验证可以立即编写和运行单元测试、集成测试和 E2E 测试

### 用户故事 4 的实施

**按依赖顺序执行: Registry 示例 → 文档 → 推广**

#### 在 patra-registry 中创建示例测试

- [ ] T089 [Example] [US4] 在 patra-registry-domain 的 pom.xml 中引入 patra-common-test 依赖
- [ ] T090 [Example] [US4] 在 patra-registry-app 的 pom.xml 中引入 patra-common-test 依赖
- [ ] T091 [Example] [US4] 在 patra-registry-infra 的 pom.xml 中引入 patra-common-test 依赖
- [ ] T092 [Example] [US4] 在 patra-registry-adapter 的 pom.xml 中引入 patra-common-test 依赖
- [ ] T093 [Example] [US4] 在 patra-registry-boot 的 pom.xml 中引入 patra-spring-boot-starter-test 依赖

#### 编写示例单元测试

- [ ] T094 [P] [Example] [US4] 创建 ProvenanceTestBuilder in patra-registry-domain/src/test/java/com/patra/registry/domain/test/ProvenanceTestBuilder.java
- [ ] T095 [P] [Example] [US4] 编写 Provenance 聚合根单元测试 in patra-registry-domain/src/test/java/com/patra/registry/domain/model/ProvenanceTest.java
- [ ] T096 [P] [Example] [US4] 编写 ProvenanceCoordinator 单元测试 in patra-registry-app/src/test/java/com/patra/registry/app/coordinator/ProvenanceCoordinatorTest.java

#### 编写示例集成测试

- [ ] T097 [Example] [US4] 编写 ProvenanceRepositoryImpl 集成测试 in patra-registry-boot/src/test/java/com/patra/registry/infra/repository/ProvenanceRepositoryIT.java(依赖 T093)
- [ ] T098 [Example] [US4] 编写 ProvenanceCoordinator 集成测试 in patra-registry-boot/src/test/java/com/patra/registry/app/coordinator/ProvenanceCoordinatorIT.java

#### 编写示例 E2E 测试

- [ ] T099 [Example] [US4] 编写 ProvenanceController E2E 测试 in patra-registry-boot/src/test/java/com/patra/registry/adapter/controller/ProvenanceControllerE2E.java
- [ ] T100 [Example] [US4] 编写 RegistryFlow E2E 测试 in patra-registry-boot/src/test/java/com/patra/registry/RegistryFlowE2E.java

#### 配置容器复用(本地开发)

- [ ] T101 [Example] [US4] 创建 ~/.testcontainers.properties 配置示例,启用容器复用
- [ ] T102 [Example] [US4] 创建 src/test/resources/application-test.yml 配置示例,设置日志级别

#### 验证测试通过

- [ ] T103 [US4] 运行 patra-registry 模块的所有测试(单元测试、集成测试、E2E 测试),验证测试基础设施可用性
- [ ] T104 [US4] 验证 TestContainers 启动时间 < 10 秒(首次),< 1 秒(复用)

**检查点**: 此时,patra-registry 模块的示例测试全部通过,验证测试基础设施可用性和便利性

---

## 阶段 7: 润色和跨领域关注点

**目的**: 影响多个用户故事的改进

### package-info.java 生成

> **说明**: 为每个新包自动生成 package-info.java,包含包描述、主要组件、设计原则和使用示例

- [ ] T105 [P] 生成 package-info.java for com.patra.common.test in patra-common-test/src/main/java/com/patra/common/test/package-info.java
  - **数据来源**: test-infrastructure-model.md 的"模块概述"
  - **包含内容**: 模块描述、主要组件列表(TestDataBuilder、MockDataFactory、DomainAssertions、BaseUnitTest)、设计原则(Builder 模式、静态工厂方法)、使用示例

- [ ] T106 [P] 生成 package-info.java for com.patra.common.test.builder in patra-common-test/src/main/java/com/patra/common/test/builder/package-info.java
  - **描述**: 测试数据构建器包
  - **主要组件**: TestDataBuilder 抽象基类
  - **设计原则**: Builder 模式、模板方法模式、流式 API

- [ ] T107 [P] 生成 package-info.java for com.patra.common.test.factory in patra-common-test/src/main/java/com/patra/common/test/factory/package-info.java
  - **描述**: Mock 数据工厂包
  - **主要组件**: MockDataFactory 静态工具类
  - **设计原则**: 静态工厂方法模式、ThreadLocalRandom

- [ ] T108 [P] 生成 package-info.java for com.patra.common.test.assertion in patra-common-test/src/main/java/com/patra/common/test/assertion/package-info.java
  - **描述**: 断言辅助工具包
  - **主要组件**: DomainAssertions、AssertionHelper
  - **设计原则**: AssertJ 流式断言、业务语义化断言

- [ ] T109 [P] 生成 package-info.java for com.patra.common.test.base in patra-common-test/src/main/java/com/patra/common/test/base/package-info.java
  - **描述**: 测试基类包
  - **主要组件**: BaseUnitTest
  - **设计原则**: Mockito @Mock/@InjectMocks、TestWatcher

- [ ] T110 [P] 生成 package-info.java for com.patra.common.test.constant in patra-common-test/src/main/java/com/patra/common/test/constant/package-info.java
  - **描述**: 测试常量包
  - **主要组件**: TestConstants
  - **设计原则**: 常量集中管理

- [ ] T111 [P] 生成 package-info.java for com.patra.spring.boot.starter.test in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/package-info.java
  - **数据来源**: test-infrastructure-model.md 的"模块概述"
  - **包含内容**: 模块描述、主要组件列表(BaseIntegrationTest、BaseE2ETest、TestcontainersConfiguration)、设计原则(Spring Boot Starter、自动配置)、使用示例

- [ ] T112 [P] 生成 package-info.java for com.patra.spring.boot.starter.test.autoconfigure in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/autoconfigure/package-info.java
  - **描述**: 自动配置包
  - **主要组件**: TestContainersAutoConfiguration、MockMvcAutoConfiguration、WireMockAutoConfiguration
  - **设计原则**: Spring Boot Auto-Configuration、@ConditionalOnClass

- [ ] T113 [P] 生成 package-info.java for com.patra.spring.boot.starter.test.base in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/base/package-info.java
  - **描述**: 测试基类包
  - **主要组件**: BaseIntegrationTest、BaseE2ETest
  - **设计原则**: @SpringBootTest、@Transactional、MockMvc

- [ ] T114 [P] 生成 package-info.java for com.patra.spring.boot.starter.test.container in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/container/package-info.java
  - **描述**: TestContainers 容器定义包
  - **主要组件**: MySQLTestContainer、RedisTestContainer、NacosTestContainer
  - **设计原则**: @ServiceConnection、Reusable Containers

- [ ] T115 [P] 生成 package-info.java for com.patra.spring.boot.starter.test.config in patra-spring-boot-starter-test/src/main/java/com/patra/spring/boot/starter/test/config/package-info.java
  - **描述**: 配置类包
  - **主要组件**: TestcontainersConfiguration、TestProperties
  - **设计原则**: @TestConfiguration、@ServiceConnection

### 模块文档生成

> **说明**: 生成和更新模块 README.md

- [ ] T116 生成 patra-common-test/README.md
  - **数据来源**: test-api.md、quickstart.md
  - **包含内容**: 模块概述、核心 API 列表(TestDataBuilder、MockDataFactory、DomainAssertions、BaseUnitTest)、依赖配置、使用示例、最佳实践

- [ ] T117 生成 patra-spring-boot-starter-test/README.md
  - **数据来源**: test-api.md、quickstart.md
  - **包含内容**: 模块概述、核心 API 列表(BaseIntegrationTest、BaseE2ETest、TestcontainersConfiguration)、依赖配置、使用示例、容器复用配置、最佳实践

### JavaDoc 补充(核心类)

> **说明**: 为核心工具类和测试基类添加详细的 JavaDoc 注释

- [ ] T118 [P] 为 TestDataBuilder 添加详细 JavaDoc in patra-common-test/.../builder/TestDataBuilder.java
  - **类级 JavaDoc**: 测试数据构建器基类描述、设计模式(Builder 模式)、使用场景、@author、@since
  - **方法级 JavaDoc**: build()、buildList()、buildAndSave() 的参数说明、返回值说明、使用示例

- [ ] T119 [P] 为 MockDataFactory 添加详细 JavaDoc in patra-common-test/.../factory/MockDataFactory.java
  - **类级 JavaDoc**: Mock 数据工厂描述、设计模式(静态工厂方法)、线程安全性说明
  - **方法级 JavaDoc**: 每个随机数据生成方法的参数说明、返回值说明、使用示例

- [ ] T120 [P] 为 DomainAssertions 添加详细 JavaDoc in patra-common-test/.../assertion/DomainAssertions.java
  - **类级 JavaDoc**: 领域断言工具描述、业务语义化断言的优势
  - **方法级 JavaDoc**: 每个断言方法的参数说明、断言逻辑、失败时的错误信息示例

- [ ] T121 [P] 为 BaseIntegrationTest 添加详细 JavaDoc in patra-spring-boot-starter-test/.../base/BaseIntegrationTest.java
  - **类级 JavaDoc**: 集成测试基类描述、自动配置的组件列表(MySQL、Redis、Nacos)、@Transactional 回滚机制
  - **方法级 JavaDoc**: cleanRedis()、executeSqlScript() 的使用说明

- [ ] T122 [P] 为 BaseE2ETest 添加详细 JavaDoc in patra-spring-boot-starter-test/.../base/BaseE2ETest.java
  - **类级 JavaDoc**: E2E 测试基类描述、MockMvc 用法、完整应用启动
  - **方法级 JavaDoc**: performGet()、performPost() 等便捷方法的使用说明

### 其他润色任务

- [ ] T123 代码清理和重构 - 移除未使用的导入、优化代码结构
- [ ] T124 [P] 为所有公共类添加 @since 标签(版本 1.0.0)
- [ ] T125 [P] 为所有公共类添加 @author 标签
- [ ] T126 运行 quickstart.md 中的示例验证
- [ ] T127 运行所有测试(单元测试、集成测试、E2E 测试),确保测试覆盖率 ≥ 80%
- [ ] T128 生成测试覆盖率报告(使用 JaCoCo)
- [ ] T129 在 patra-registry-boot 中运行性能测试,验证 TestContainers 启动时间 < 10 秒

---

## 依赖和执行顺序

### 阶段依赖

- **设置(阶段 1)**: 无依赖 - 可以立即开始
- **基础(阶段 2)**: 依赖于设置完成 - 阻塞所有用户故事
- **用户故事(阶段 3-6)**: 都依赖于基础阶段完成
  - **US1(P1)**: 可以在基础(阶段 2)之后开始 - 不依赖其他故事
  - **US2(P1)**: 可以在基础(阶段 2)之后开始 - 依赖 US1 完成(patra-common-test)
  - **US3(P2)**: 可以在基础(阶段 2)之后开始 - 依赖 US1、US2 完成
  - **US4(P3)**: 可以在 US1、US2、US3 完成后开始 - 需要所有测试基础设施就绪
- **润色(阶段 7)**: 依赖于所有期望的用户故事完成

### 用户故事依赖

- **用户故事 1(P1)**: 可以在基础(阶段 2)之后开始 - 不依赖其他故事
- **用户故事 2(P1)**: 可以在基础(阶段 2)之后开始 - 但依赖 US1 完成(因为需要 patra-common-test)
- **用户故事 3(P2)**: 可以在基础(阶段 2)之后开始 - 但依赖 US1、US2 完成(因为需要验证版本管理)
- **用户故事 4(P3)**: 必须在 US1、US2、US3 完成后开始 - 需要完整的测试基础设施

### 在每个用户故事内

- 测试(如果包含)必须在实施之前编写并失败
- MockDataFactory 在 TestDataBuilder 之前(因为 Builder 可能使用 Factory)
- TestDataBuilder 在 DomainAssertions 之前
- 所有核心工具类在测试基类之前
- 容器定义在自动配置之前
- 自动配置在测试基类之前
- 元测试在实施之后运行,验证功能正确性

### 并行机会

- **阶段 1(设置)**: T001、T002、T003 可以并行运行
- **阶段 2(基础)**: T004-T006(配置)可以并行,T007-T012(核心工具类骨架)可以并行,T013-T015(容器定义)可以并行,T017-T019(自动配置)可以并行
- **用户故事 1**: T022-T026(元测试)可以并行,T027-T036(MockDataFactory 方法)可以并行,T041-T046(DomainAssertions 方法)可以并行
- **用户故事 2**: T052-T054(元测试)可以并行,T055-T062(容器方法)可以并行,T067-T069(自动配置)可以并行
- **用户故事 3**: T080-T086(版本管理)可以并行
- **用户故事 4**: T089-T093(依赖引入)可以并行,T094-T096(单元测试)可以并行
- **润色阶段**: T105-T115(package-info.java)可以并行,T118-T122(JavaDoc)可以并行,T124-T125(标签)可以并行

---

## 并行示例: 用户故事 1

```bash
# 同时启动用户故事 1 的所有元测试(不同文件,无依赖):
Task: "为 TestDataBuilder 编写单元测试 in patra-common-test/src/test/java/.../TestDataBuilderTest.java"
Task: "为 MockDataFactory 编写单元测试 in patra-common-test/src/test/java/.../MockDataFactoryTest.java"
Task: "为 DomainAssertions 编写单元测试 in patra-common-test/src/test/java/.../DomainAssertionsTest.java"

# 同时启动用户故事 1 的所有 MockDataFactory 方法实现(不同方法,可并行):
Task: "实现 MockDataFactory.randomString() 方法"
Task: "实现 MockDataFactory.randomUuid() 方法"
Task: "实现 MockDataFactory.randomInt() 方法"
```

---

## 并行示例: 用户故事 2

```bash
# 同时启动用户故事 2 的所有容器方法实现(不同容器,可并行):
Task: "实现 MySQLTestContainer.start() 方法(MySQL 8.0.36 + tmpfs + Reusable)"
Task: "实现 RedisTestContainer.start() 方法(Redis 7-alpine + Reusable)"
Task: "实现 NacosTestContainer.start() 方法(Nacos 2.3.0 standalone + Reusable)"

# 同时启动用户故事 2 的所有自动配置实现(不同文件,可并行):
Task: "实现 TestContainersAutoConfiguration 条件化配置"
Task: "实现 MockMvcAutoConfiguration 自动配置"
Task: "实现 WireMockAutoConfiguration 自动配置"
```

---

## 实施策略

### MVP 优先(仅用户故事 1 + 2)

1. 完成阶段 1: 设置
2. 完成阶段 2: 基础(关键 - 阻塞所有故事)
3. 完成阶段 3: 用户故事 1(单元测试工具类复用)
4. 完成阶段 4: 用户故事 2(集成测试环境自动配置)
5. **停止并验证**: 独立测试用户故事 1 和 2
6. 如果准备好则部署/推广

### 增量交付

1. 完成设置 + 基础 → 基础就绪
2. 添加用户故事 1 → 独立测试 → 可用于单元测试(MVP!)
3. 添加用户故事 2 → 独立测试 → 可用于集成测试和 E2E 测试
4. 添加用户故事 3 → 独立测试 → 统一版本管理
5. 添加用户故事 4 → 独立测试 → 新模块快速搭建
6. 每个故事都增加价值而不破坏以前的故事

### 并行团队策略

对于多个开发者:

1. 团队一起完成设置 + 基础
2. 一旦基础完成:
   - 开发者 A: 用户故事 1(patra-common-test)
   - 开发者 B: 用户故事 2(patra-spring-boot-starter-test) - 等待 A 完成 US1
   - 开发者 C: 用户故事 3(版本管理) - 等待 A、B 完成 US1、US2
   - 开发者 D: 用户故事 4(示例测试) - 等待 A、B、C 完成 US1、US2、US3
3. 故事独立完成和集成

---

## 注意事项

- **[P] 任务**: 不同文件,无依赖,可并行执行
- **[Story] 标签**: 将任务映射到特定用户故事以实现可追溯性
- **每个用户故事**: 应该可以独立完成和测试
- **TDD 原则**: 在实施之前验证测试失败
- **提交频率**: 在每个任务或逻辑组之后提交
- **检查点**: 在任何检查点停止以独立验证故事
- **避免**: 模糊任务、同一文件冲突、破坏独立性的跨故事依赖
- **测试覆盖率**: patra-common-test ≥ 80%, patra-spring-boot-starter-test 集成测试全部通过
- **性能要求**: TestContainers 启动时间 < 10 秒(首次),< 1 秒(复用)
- **文档完整性**: 每个包有 package-info.java,每个模块有 README.md,核心类有详细 JavaDoc

---

## 任务总结

**任务总数**: 129
**用户故事任务分布**:
- 用户故事 1(P1): 30 个任务(T022-T051)
- 用户故事 2(P1): 28 个任务(T052-T079)
- 用户故事 3(P2): 9 个任务(T080-T088)
- 用户故事 4(P3): 16 个任务(T089-T104)
- 润色和跨领域: 25 个任务(T105-T129)

**并行机会识别**: 约 70% 的任务标记为 [P],可并行执行,显著提升开发效率

**独立测试标准**:
- **US1**: 在 patra-registry-domain 中使用 TestDataBuilder 创建测试数据,验证工具类可用性
- **US2**: 在 patra-registry-boot 中编写集成测试,验证 TestContainers 自动启动
- **US3**: 检查多个模块的 pom.xml,验证测试依赖无需指定 version
- **US4**: 在 patra-registry 中运行所有测试,验证测试基础设施完整可用

**建议的 MVP 范围**: 用户故事 1 + 2(单元测试工具类 + 集成测试环境)

**格式验证**: ✅ 所有任务遵循检查清单格式(复选框、ID、标签、文件路径)

---

**生成时间**: 2025-11-09
**生成工具**: /speckit.tasks 命令
**下一步**: 执行 `/speckit.implement` 开始实施,或手动选择任务开始编码
