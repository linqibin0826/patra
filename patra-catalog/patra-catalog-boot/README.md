# patra-catalog-boot — 目录管理启动模块

## 📋 概述

`patra-catalog-boot` 是 patra-catalog 服务的**启动模块（Boot Layer）**，负责 Spring Boot 应用的启动、配置和运行。

本模块在六边形架构中扮演**应用入口**角色，遵循以下原则：
- **最小代码**：只包含启动类和配置类，不包含业务逻辑
- **依赖聚合**：聚合所有其他模块的依赖
- **环境隔离**：提供多环境配置（dev、test、prod）
- **可观测性**：集成日志、监控、健康检查

---

## 🏗️ 模块结构

```
patra-catalog-boot/
├─ src/main/java/.../
│  └─ PatraCatalogApplication.java          # Spring Boot 启动类
├─ src/main/resources/
│  ├─ application.yml                       # 主配置文件
│  ├─ application-dev.yml                   # 开发环境配置
│  ├─ application-test.yml                  # 测试环境配置
│  ├─ application-prod.yml                  # 生产环境配置
│  ├─ logback-spring.xml                    # Logback 日志配置
│  └─ db/migration/                         # Flyway 数据库迁移脚本
│     ├─ V1__init_mesh_import_tables.sql    # 初始化 MeSH 导入管理表
│     └─ V2__init_mesh_data_tables.sql      # 初始化 MeSH 数据表
└─ src/test/java/.../
   └─ architecture/                         # ArchUnit 架构测试
      ├─ CatalogArchitectureTest.java       # 架构规则测试（总入口）
      └─ rules/                             # 架构规则定义
         ├─ HexagonalArchitectureRules.java # 六边形架构规则
         ├─ NamingRules.java                # 命名约定规则
         ├─ DependencyRules.java            # 依赖方向规则
         └─ TestingRules.java               # 测试规范规则
```

---

## 🔑 核心职责

### 1. 应用启动

**职责**：启动 Spring Boot 应用，初始化所有组件。

**启动类**：
```java
@SpringBootApplication
public class PatraCatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatraCatalogApplication.class, args);
    }
}
```

**文件**：`PatraCatalogApplication.java`

### 2. 配置管理

**职责**：管理多环境配置，提供配置文件模板。

**配置文件结构**：
```
application.yml          # 主配置（通用配置）
application-dev.yml      # 开发环境（本地数据库、DEBUG 日志）
application-test.yml     # 测试环境（H2 内存数据库、INFO 日志）
application-prod.yml     # 生产环境（生产数据库、WARN 日志）
```

### 3. 数据库迁移

**职责**：使用 Flyway 管理数据库 Schema 版本。

**迁移脚本位置**：`src/main/resources/db/migration/`

**命名规范**：
- `V{version}__{description}.sql`（版本迁移）
- `R__{description}.sql`（可重复迁移）

**示例**：
- `V1__init_mesh_import_tables.sql`
- `V2__init_mesh_data_tables.sql`

### 4. 架构测试

**职责**：使用 ArchUnit 自动化验证六边形架构和 DDD 的核心约束。

**测试类别**：
| 测试类别 | 规则数 | 说明 |
|---------|-------|------|
| **层依赖方向** | 5 | 验证 Adapter → App → Domain ← Infra 依赖方向 |
| **Domain 纯净性** | 3 | 验证 Domain 层零 Spring 依赖、允许 Jackson |
| **命名约定** | 5 | 验证 Port/DO/Aggregate/Orchestrator 命名和位置 |
| **封装规则** | 3 | 验证 DO 不泄露、Port 可见性、Event 位置 |
| **事务边界** | 2 | 验证 @Transactional 仅在 App 层 |
| **测试规范** | 6 | 验证测试命名规范、测试独立性、分层测试策略 |

### 5. 可观测性

**职责**：集成日志、监控、健康检查。

**可观测性组件**：
- **日志**：Logback + SLF4J
- **监控**：Micrometer + Prometheus（规划中）
- **健康检查**：Spring Boot Actuator
- **分布式追踪**：SkyWalking（规划中）

---

## 🎯 核心组件

### 1. PatraCatalogApplication (启动类)

**职责**：Spring Boot 应用入口。

**实现**：
```java
@SpringBootApplication
public class PatraCatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatraCatalogApplication.class, args);
    }
}
```

**扫描路径**：
- `com.patra.catalog.*`（自动扫描所有 `@Component`、`@Service`、`@Repository`、`@Controller`）

**文件**：`PatraCatalogApplication.java`

### 2. application.yml (主配置文件)

**职责**：定义通用配置（所有环境共享）。

**示例**：
```yaml
spring:
  application:
    name: patra-catalog
  profiles:
    active: dev

server:
  port: 8083

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

mesh:
  import:
    batch-size: 1000
    enable-data-validation: true
    deviation-threshold: 0.05

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

**文件**：`src/main/resources/application.yml`

### 3. application-dev.yml (开发环境配置)

**职责**：开发环境专用配置。

**示例**：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/patra_catalog_dev?useUnicode=true&characterEncoding=utf8
    username: root
    password: root
    hikari:
      maximum-pool-size: 5

  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl

logging:
  level:
    root: INFO
    com.patra.catalog: DEBUG
    com.patra.catalog.infra.persistence.mapper: DEBUG

mesh:
  import:
    source-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml
```

**文件**：`src/main/resources/application-dev.yml`

### 4. CatalogArchitectureTest (架构测试)

**职责**：验证六边形架构和 DDD 的核心约束。

**实现**：
```java
@AnalyzeClasses(packages = "com.patra.catalog", importOptions = {
    ImportOption.DoNotIncludeTests.class,
    ImportOption.DoNotIncludeJars.class
})
public class CatalogArchitectureTest {

    @ArchTest
    public static final ArchRule hexagonal_architecture_layer_dependencies =
        HexagonalArchitectureRules.layerDependencies();

    @ArchTest
    public static final ArchRule domain_layer_should_be_framework_free =
        HexagonalArchitectureRules.domainLayerShouldBePure();

    @ArchTest
    public static final ArchRule repository_implementations_should_be_in_infra =
        NamingRules.repositoryImplementationsShouldBeInInfra();

    @ArchTest
    public static final ArchRule do_objects_should_not_leak_from_infra =
        DependencyRules.doObjectsShouldNotLeakFromInfra();

    @ArchTest
    public static final ArchRule transactional_annotation_only_in_app_layer =
        DependencyRules.transactionalOnlyInAppLayer();

    @ArchTest
    public static final ArchRule test_classes_should_follow_naming_convention =
        TestingRules.testClassesShouldFollowNamingConvention();
}
```

**运行测试**：
```bash
mvn test -Dtest=CatalogArchitectureTest
```

**文件**：`src/test/java/com/patra/catalog/architecture/CatalogArchitectureTest.java`

---

## 📦 依赖关系

### 上游依赖

本模块聚合所有其他模块的依赖：
- `patra-catalog-adapter`：适配器层
- `patra-catalog-app`：应用层
- `patra-catalog-domain`：领域层
- `patra-catalog-infra`：基础设施层
- `patra-catalog-api`：对外契约层

### 外部依赖

- `spring-boot-starter-web`：Web 容器（Tomcat）
- `spring-boot-starter-actuator`：健康检查
- `redisson-spring-boot-starter`：分布式锁
- `xxl-job-core`：分布式任务调度
- `flyway-core`：数据库迁移
- `flyway-mysql`：MySQL 支持
- `archunit-junit5`：架构测试

---

## 🚀 快速开始

### 本地运行

#### 前置条件

- Java 25
- Maven 3.9+
- MySQL 8.0+
- Redis 7.0+（用于 Redisson 分布式锁）

#### 启动步骤

1. **启动 MySQL 和 Redis**（使用 Docker Compose）：
```bash
cd docker
docker-compose up -d mysql redis
```

2. **创建数据库**：
```sql
CREATE DATABASE patra_catalog_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. **运行应用**：
```bash
cd patra-catalog/patra-catalog-boot
../../mvnw spring-boot:run
```

4. **验证启动**：
```bash
# 健康检查
curl http://localhost:8083/actuator/health

# 查看指标
curl http://localhost:8083/actuator/metrics
```

### 配置说明

#### 环境切换

通过 `spring.profiles.active` 切换环境：
```bash
# 开发环境
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 测试环境
mvn spring-boot:run -Dspring-boot.run.profiles=test

# 生产环境
java -jar patra-catalog-boot.jar --spring.profiles.active=prod
```

#### 配置优先级

1. 命令行参数（`--key=value`）
2. 环境变量（`SPRING_APPLICATION_NAME`）
3. `application-{profile}.yml`
4. `application.yml`

---

## 🧪 测试覆盖

| 测试类型 | 覆盖率目标 | 当前覆盖率 |
|---------|-----------|-----------|
| 架构测试 | 100%（架构规则） | [待测试运行后更新] |
| E2E 测试 | 核心流程 100% | [待测试运行后更新] |

**关键测试类**：
- `CatalogArchitectureTest` - 架构规则测试
- `MeshImportE2E` - MeSH 导入端到端测试（规划中）

---

## 🎨 设计模式

### 1. 启动类模式

**核心思想**：启动类只负责启动应用，不包含任何业务逻辑。

**特点**：
- 使用 `@SpringBootApplication` 注解
- `main` 方法调用 `SpringApplication.run()`
- 不包含任何 `@Bean` 定义（配置类单独定义）

### 2. 配置文件分离模式

**核心思想**：使用多个配置文件管理不同环境的配置。

**特点**：
- 通用配置在 `application.yml`
- 环境专用配置在 `application-{profile}.yml`
- 通过 `spring.profiles.active` 切换环境

### 3. 架构测试模式

**核心思想**：使用 ArchUnit 自动化验证架构约束。

**特点**：
- 编译时检查（在测试阶段运行）
- 失败时阻止构建
- 支持自定义规则

---

## 🛠️ 技术栈

- **Spring Boot**：3.5.7
- **Spring Cloud**：2025.0.0
- **Redisson**：分布式锁
- **XXL-Job**：分布式任务调度
- **Flyway**：数据库迁移
- **ArchUnit**：架构测试

---

## 📚 相关文档

- [patra-catalog 模块总览](../README.md)
- [MeSH 导入快速开始](../../specs/001-mesh-data-import/quickstart.md)
- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Flyway 官方文档](https://flywaydb.org/)
- [ArchUnit 官方文档](https://www.archunit.org/)

---

**最后更新**：2025-11-22
**Maven 坐标**：`com.patra:patra-catalog-boot:0.2.0-SNAPSHOT`
**作者**：Patra Team
