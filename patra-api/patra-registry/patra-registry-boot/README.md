# patra-registry-boot

## 概述

`patra-registry-boot` 是 patra-registry 服务的**可执行入口模块**,负责组装所有子模块并提供 Spring Boot 应用启动能力。本模块包含主类、配置文件和应用上下文组装逻辑,是整个服务的运行时容器。

在六边形架构中,本模块作为组装层,将适配器层(`adapter`)、应用层(`app`)和基础设施层(`infra`)组装成可执行的 Spring Boot 应用,不包含业务逻辑。

## 核心职责

- **应用启动**: 提供 Spring Boot 主类和启动入口
- **模块组装**: 组装所有子模块,构建完整的应用上下文
- **配置管理**: 管理应用配置文件(`application.yml`、`bootstrap.yml`)
- **依赖声明**: 声明运行时依赖,包括 Web、Feign、Nacos 等
- **数据库迁移**: 集成 Flyway,自动执行数据库迁移

## 模块结构

```
patra-registry-boot/
├── src/main/java/com/patra/registry/
│   └── PatraRegistryApplication.java       # Spring Boot 主类
├── src/main/resources/
│   ├── application.yml                     # 应用配置
│   ├── bootstrap.yml                       # Bootstrap 配置(Nacos)
│   └── db/migration/                       # Flyway 迁移脚本
│       ├── V1__init_provenance.sql
│       ├── V2__init_expr.sql
│       └── ...
└── pom.xml                                 # Maven 配置
```

## 主要组件

### PatraRegistryApplication

Spring Boot 主类,应用启动入口。

**职责**:
- 启动 Spring Boot 应用
- 扫描所有子模块的组件
- 加载配置文件

**实现**:
```java
@SpringBootApplication
public class PatraRegistryApplication {

  public static void main(String[] args) {
    SpringApplication.run(PatraRegistryApplication.class, args);
  }
}
```

### application.yml

应用配置文件,定义服务名称、端口、日志等配置。

**核心配置**:
```yaml
spring:
  application:
    name: patra-registry

server:
  port: 8081

patra:
  logging:
    trace:
      enabled: true
```

### bootstrap.yml

Bootstrap 配置文件,定义 Nacos 配置中心和服务注册配置。

**核心配置**:
```yaml
spring:
  application:
    name: patra-registry
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        file-extension: yml
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
```

### Flyway 迁移脚本

数据库迁移脚本,自动创建表结构和初始化数据。

**脚本位置**: `src/main/resources/db/migration/`

**命名规范**: `V{version}__{description}.sql`

**示例**:
- `V1__init_provenance.sql`: 创建数据源相关表
- `V2__init_expr.sql`: 创建表达式相关表
- `V3__seed_data.sql`: 插入种子数据

## 依赖关系

### 子模块依赖

本模块依赖所有子模块:
- `patra-registry-adapter`: 适配器层
- `patra-registry-infra`: 基础设施层

**注意**: 不需要直接依赖 `app`、`domain`、`api` 模块,它们通过传递依赖自动引入。

### 外部依赖

**Web 相关**:
- `patra-spring-boot-starter-web`: Web 配置和异常处理
- `spring-boot-starter-validation`: 参数验证

**微服务相关**:
- `patra-spring-cloud-starter-feign`: Feign 客户端配置
- `spring-cloud-starter-alibaba-nacos-discovery`: Nacos 服务注册
- `spring-cloud-starter-alibaba-nacos-config`: Nacos 配置中心

**测试相关**:
- `spring-boot-starter-test`: Spring Boot 测试支持
- `testcontainers`: Docker 容器测试支持

## 技术栈

| 组件 | 版本/说明 |
|------|---------|
| **Java** | 25 |
| **Spring Boot** | 3.5.7 |
| **Spring Cloud** | 2025.0.0 |
| **Nacos** | 服务注册与配置中心 |
| **Flyway** | 数据库迁移工具 |
| **MyBatis-Plus** | 持久化框架(通过 infra 引入) |
| **MapStruct** | 对象映射(通过子模块引入) |
| **Maven** | 构建工具 |

## 本地开发

### 前置条件

- Java 25
- Maven 3.9+
- MySQL 8.0+
- Nacos 2.x (可选,本地开发可禁用)

### 启动服务

**方式 1: Maven 插件**
```bash
cd patra-registry-boot
mvn spring-boot:run
```

**方式 2: IDE 运行**
- 运行 `PatraRegistryApplication.main()` 方法

**方式 3: JAR 包运行**
```bash
mvn clean package -DskipTests
java -jar target/patra-registry-boot-0.1.0-SNAPSHOT.jar
```

### 默认配置

- **端口**: 8081
- **上下文路径**: `/`
- **健康检查**: `http://localhost:8081/actuator/health`
- **内部 API**: `http://localhost:8081/_internal/`

### 数据库初始化

Flyway 自动执行数据库迁移,首次启动时:
1. 创建 `flyway_schema_history` 表
2. 按版本顺序执行所有迁移脚本
3. 插入种子数据

**查看迁移历史**:
```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

## 配置管理

### 配置优先级

1. **命令行参数**: `--server.port=8082`
2. **环境变量**: `SERVER_PORT=8082`
3. **application.yml**: 本地配置文件
4. **Nacos 配置中心**: 远程配置(生产环境)

### Nacos 配置

**Data ID**: `patra-registry.yml`
**Group**: `DEFAULT_GROUP`

**配置内容**:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/patra_registry
    username: root
    password: ${MYSQL_PASSWORD}

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
```

## 构建与部署

### Maven 打包

```bash
# 打包(跳过测试)
mvn clean package -DskipTests

# 打包(执行测试)
mvn clean package

# 生成 Docker 镜像
mvn spring-boot:build-image
```

### Docker 部署

**Dockerfile**:
```dockerfile
FROM openjdk:25-slim
COPY target/patra-registry-boot-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

**构建镜像**:
```bash
docker build -t patra-registry:0.1.0 .
```

**运行容器**:
```bash
docker run -d \
  -p 8081:8081 \
  -e NACOS_SERVER_ADDR=nacos:8848 \
  -e MYSQL_HOST=mysql \
  patra-registry:0.1.0
```

## 测试策略

Patra Registry 遵循**六边形架构的分层测试策略**。

**详细测试规范** → 参见 [测试覆盖率检查清单](../../.specify/templates/test-coverage-checklist.md)

**TDD 实践指南** → 参见 [patra-tdd-development Skill](../../.claude/skills/patra-tdd-development/SKILL.md)

### 快速参考

| 测试类型 | 位置 | 框架 | 覆盖率 |
|---------|------|------|--------|
| **Domain 单元测试** | patra-registry-domain/src/test/ | JUnit 5 + AssertJ | ≥ 80% |
| **Application 单元测试** | patra-registry-app/src/test/ | JUnit 5 + Mockito | ≥ 70% |
| **Infrastructure 测试** | patra-registry-infra/src/test/ | @MybatisTest + TestContainers | 有 |
| **Adapter 测试** | patra-registry-adapter/src/test/ | @WebMvcTest + Mockito | 有 |
| **Boot E2E 测试** | patra-registry-boot/src/test/ | @SpringBootTest + TestContainers | 有 |

### E2E 测试示例

```java
@SpringBootTest
@Testcontainers
class ProvenanceEndpointE2ETest {

  @Container
  static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void shouldListProvenances() {
    ResponseEntity<List> response = restTemplate.getForEntity(
        "/_internal/provenances",
        List.class
    );

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
  }
}
```

### 架构测试

位于本模块的 `src/test/java/com/patra/registry/architecture/ArchitectureTest.java`,使用 ArchUnit 框架验证六边形架构规则。

**测试覆盖**:
- ✅ 六边形架构依赖方向 (Boot → Adapter → App → Domain ← Infra)
- ✅ Domain 层纯净性 (无框架依赖)
- ✅ Boot 层职责边界 (不包含业务逻辑)
- ✅ 循环依赖检查
- ✅ 命名规范 (DTO、Mapper、Repository、Orchestrator、Coordinator)
- ✅ 注解使用规范 (@Service、@Repository、@Component)
- ✅ DDD 模型约束 (值对象不可变性、聚合根位置)
- ✅ 端口接口隔离 (Port 接口不依赖实现类)
- ✅ API 契约隔离 (API 模块不依赖其他模块)

**运行方式**:
```bash
# 运行所有测试(包括架构测试)
mvn verify

# 只运行架构测试
mvn verify -DskipTests

# 跳过架构测试(不推荐)
mvn verify -Darchitecture.test.skip=true
```

**CI/CD 集成**:

架构测试已配置为强制执行的构建检查项:
- ✅ 在 `verify` 阶段自动运行
- ✅ 即使使用 `-DskipTests` 也会执行
- ✅ 测试失败会立即中断构建
- ✅ 独立的测试报告目录: `target/surefire-reports/architecture/`

**配置说明**:

本模块的 `pom.xml` 中已配置 Maven Surefire 插件强制执行架构测试:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <executions>
        <execution>
            <id>architecture-tests</id>
            <phase>verify</phase>
            <goals>
                <goal>test</goal>
            </goals>
            <configuration>
                <includes>
                    <include>**/ArchitectureTest.java</include>
                </includes>
                <testFailureIgnore>false</testFailureIgnore>
                <skipTests>false</skipTests>  <!-- 覆盖全局 -DskipTests -->
                <skip>${architecture.test.skip}</skip>  <!-- 允许显式跳过 -->
                <reportsDirectory>${project.build.directory}/surefire-reports/architecture</reportsDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**ArchUnit 配置**:

全局配置位于 `src/test/resources/archunit.properties`:
```properties
# 允许规则在没有匹配类时通过(适用于多模块项目)
archRule.failOnEmptyShould=false

# 类缓存模式: default(每次测试) | forever(缓存到进程结束)
classCache=default
classCache.useMd5=true
```

**架构测试失败处理**:

当架构测试失败时:
1. **查看报告**: 检查 `target/surefire-reports/architecture/` 目录中的测试报告
2. **理解违规**: 阅读失败消息,理解违反了哪条架构规则
3. **修复代码**: 调整代码以符合架构规范
4. **重新运行**: 执行 `mvn verify` 验证修复

**常见架构违规示例**:

| 违规类型 | 违规示例 | 正确做法 |
|---------|---------|---------|
| Domain 层依赖框架 | `@Entity` 在 Domain 实体上 | 使用纯 Java 类,在 Infra 层创建 DO |
| Boot 层包含业务逻辑 | `@Service` 类在 boot 包下 | 移动到 app 或 domain 包 |
| 循环依赖 | A → B → C → A | 重构依赖关系,引入中介 |
| 命名不规范 | `UserConverter` 实现 Repository | 重命名为 `UserRepository` |
| 端口泄漏 | Port 接口依赖实现类 | 移除具体类依赖,只依赖接口 |

## 相关文档

- [patra-registry 顶层文档](../README.md)
- [patra-registry-adapter 模块](../patra-registry-adapter/README.md) - REST 适配器
- [patra-registry-infra 模块](../patra-registry-infra/README.md) - 基础设施层

---

**最后更新**: 2025-01-12
