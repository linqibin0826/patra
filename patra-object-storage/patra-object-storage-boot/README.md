# patra-object-storage-boot — 启动模块

## 概述

**patra-object-storage-boot** 是 patra-object-storage 服务的 Spring Boot 启动模块,负责应用的引导和配置。本模块聚合所有其他模块(adapter、app、infra),提供可执行的 JAR 包,是整个服务的入口点。

在六边形架构中,启动模块作为**基础设施层的一部分**,负责组装所有组件,配置 Spring 容器,并启动应用。

**核心原则**:
- **依赖聚合**: 聚合所有功能模块,提供完整的运行时环境
- **配置管理**: 管理应用配置,支持多环境配置
- **服务注册**: 将服务注册到 Nacos,支持服务发现
- **可执行构建**: 通过 Spring Boot Maven Plugin 构建可执行 JAR

## 核心职责

- **应用引导**: 提供 Spring Boot 应用入口(`main` 方法)
- **组件扫描**: 自动扫描并注册所有 Spring Bean
- **配置加载**: 加载 application.yml 和 Nacos 配置
- **服务注册**: 将服务注册到 Nacos 注册中心
- **健康检查**: 提供 Actuator 端点用于健康检查和监控
- **可执行打包**: 构建包含所有依赖的可执行 JAR

## 模块结构

```
patra-object-storage-boot/
├── src/main/
│   ├── java/com/patra/storage/
│   │   └── PatraObjectStorageApplication.java    # Spring Boot 启动类
│   └── resources/
│       ├── application.yml                  # 本地配置
│       ├── application-dev.yml              # 开发环境配置
│       ├── application-prod.yml             # 生产环境配置
│       └── logback-spring.xml               # 日志配置
└── pom.xml                                  # Maven 构建配置
```

## 主要组件

### PatraObjectStorageApplication (启动类)

Spring Boot 应用入口,负责启动应用并设置默认 Profile。

```java
@SpringBootApplication
public class PatraObjectStorageApplication {

    public static void main(String[] args) {
        // 1. 设置默认 Profile(如未指定)
        if (System.getProperty("spring.profiles.active") == null
            && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
            System.setProperty("spring.profiles.active", "dev");
        }

        // 2. 启动 Spring Boot 应用
        SpringApplication.run(PatraObjectStorageApplication.class, args);
    }
}
```

**设计要点**:
- **@SpringBootApplication**: 组合注解,包含 `@Configuration`、`@EnableAutoConfiguration`、`@ComponentScan`
- **默认 Profile**: 未指定环境时默认使用 `dev` 环境
- **组件扫描**: 自动扫描 `com.patra.objectstorage` 包下的所有 Spring Bean

**启动过程**:
1. 设置默认 Profile(dev)
2. 创建 Spring 应用上下文
3. 自动配置 Spring Boot 组件
4. 扫描并注册 Bean
5. 执行 Flyway 数据库迁移
6. 启动内嵌 Tomcat 服务器
7. 注册服务到 Nacos

## 依赖关系

### 功能模块依赖

```xml
<dependencies>
    <!-- 适配器层(REST 端点) -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-object-storage-adapter</artifactId>
    </dependency>

    <!-- 基础设施层(数据库访问) -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-object-storage-infra</artifactId>
    </dependency>
</dependencies>
```

**依赖传递**:
- `patra-object-storage-adapter` → `patra-object-storage-app` → `patra-object-storage-domain`
- `patra-object-storage-infra` → `patra-object-storage-domain`

**结果**: boot 模块传递依赖所有其他模块,无需显式声明。

### Spring Boot Starters

```xml
<dependencies>
    <!-- Web 支持(REST API) -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Nacos 服务发现 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>

    <!-- Nacos 配置管理 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>

    <!-- Actuator 监控端点 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

## 配置管理

### 本地配置 (application.yml)

基础配置,所有环境通用:

```yaml
spring:
  application:
    name: patra-object-storage

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/patra_storage?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=UTC
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:password}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

server:
  port: ${SERVER_PORT:8080}
  shutdown: graceful

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```

### 开发环境配置 (application-dev.yml)

```yaml
spring:
  cloud:
    nacos:
      discovery:
        enabled: true
        server-addr: localhost:8848
        namespace: dev
      config:
        enabled: true
        server-addr: localhost:8848
        namespace: dev
        file-extension: yaml
        refresh-enabled: true

logging:
  level:
    com.patra.objectstorage: DEBUG
    org.springframework.web: DEBUG
    com.baomidou.mybatisplus: DEBUG
```

### 生产环境配置 (application-prod.yml)

```yaml
spring:
  cloud:
    nacos:
      discovery:
        enabled: true
        server-addr: ${NACOS_SERVER:nacos.patra.com:8848}
        namespace: prod
      config:
        enabled: true
        server-addr: ${NACOS_SERVER:nacos.patra.com:8848}
        namespace: prod
        file-extension: yaml
        refresh-enabled: true

logging:
  level:
    com.patra.objectstorage: INFO
    org.springframework.web: WARN
```

### 环境变量

支持通过环境变量覆盖配置:

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 激活的 Profile | `dev` |
| `SERVER_PORT` | HTTP 端口 | `8080` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | `password` |
| `NACOS_SERVER` | Nacos 服务器地址 | `localhost:8848` |
| `NACOS_NAMESPACE` | Nacos 命名空间 | 根据 Profile |

## 服务注册与发现

### Nacos 服务注册

启动时自动注册到 Nacos:

```yaml
spring:
  cloud:
    nacos:
      discovery:
        enabled: true                      # 启用服务注册
        server-addr: localhost:8848        # Nacos 服务器地址
        namespace: dev                     # 命名空间(环境隔离)
        group: DEFAULT_GROUP               # 服务分组
        service: ${spring.application.name}# 服务名称
```

**注册信息**:
- **服务名**: `patra-object-storage`
- **实例 IP**: 自动检测本机 IP
- **端口**: `${server.port}`
- **元数据**: Spring Boot 版本、启动时间等

### 健康检查

Nacos 通过 Actuator 端点检查服务健康状态:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

**健康检查端点**:
- `GET /actuator/health`: 健康状态
- `GET /actuator/info`: 应用信息
- `GET /actuator/metrics`: 指标数据

## 构建与部署

### Maven 构建

通过 Spring Boot Maven Plugin 构建可执行 JAR:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <version>${spring-boot.version}</version>
            <configuration>
                <mainClass>com.patra.objectstorage.PatraObjectStorageApplication</mainClass>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>repackage</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**构建命令**:
```bash
# 清理并构建(跳过测试)
mvn clean package -DskipTests

# 构建并运行测试
mvn clean package

# 构建镜像(如果配置了 Docker Maven Plugin)
mvn clean package spring-boot:build-image
```

**产物**:
- `target/patra-object-storage-boot-0.1.0-SNAPSHOT.jar`: 可执行 JAR(包含所有依赖)

### 本地运行

**方式一:Maven 插件**
```bash
cd patra-object-storage-boot
mvn spring-boot:run
```

**方式二:可执行 JAR**
```bash
java -jar target/patra-object-storage-boot-0.1.0-SNAPSHOT.jar
```

**方式三:指定 Profile**
```bash
java -jar target/patra-object-storage-boot-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

**方式四:环境变量**
```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_PASSWORD=secret
java -jar target/patra-object-storage-boot-0.1.0-SNAPSHOT.jar
```

### Docker 部署

**Dockerfile**:
```dockerfile
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/patra-object-storage-boot-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**构建镜像**:
```bash
docker build -t patra/patra-object-storage:0.1.0 .
```

**运行容器**:
```bash
docker run -d \
  --name patra-object-storage \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_USERNAME=root \
  -e DB_PASSWORD=secret \
  -e NACOS_SERVER=nacos.patra.com:8848 \
  patra/patra-object-storage:0.1.0
```

## 监控与运维

### Actuator 端点

启用的 Actuator 端点:

| 端点 | 路径 | 说明 |
|------|------|------|
| Health | `/actuator/health` | 健康检查 |
| Info | `/actuator/info` | 应用信息 |
| Metrics | `/actuator/metrics` | 指标数据 |
| Prometheus | `/actuator/prometheus` | Prometheus 格式指标 |

### 日志配置

通过 `logback-spring.xml` 配置日志:

```xml
<configuration>
    <springProfile name="dev">
        <logger name="com.patra.objectstorage" level="DEBUG"/>
        <logger name="org.springframework.web" level="DEBUG"/>
    </springProfile>

    <springProfile name="prod">
        <logger name="com.patra.objectstorage" level="INFO"/>
        <logger name="org.springframework.web" level="WARN"/>
    </springProfile>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/patra-object-storage.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/patra-object-storage.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
    </appender>
</configuration>
```

### 优雅停机

配置优雅停机,确保请求处理完成后再关闭:

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

**停机流程**:
1. 接收 `SIGTERM` 信号
2. 停止接受新请求
3. 等待现有请求处理完成(最多 30 秒)
4. 关闭数据库连接池
5. 关闭 Spring 容器
6. 退出 JVM

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 25 | 运行时环境 |
| Spring Boot | 3.5.7 | 应用框架 |
| Spring Cloud | 2025.0.0 | 微服务框架 |
| Nacos | 2025.0.0.0 | 服务注册与配置中心 |
| Tomcat | 11.0.5+ | 内嵌 Web 服务器 |
| HikariCP | 6.2.1+ | 数据库连接池 |
| Flyway | 10.30.0+ | 数据库迁移工具 |

## 最佳实践

### 配置管理

1. **配置分层**: 本地配置 + 环境配置 + Nacos 配置
2. **环境隔离**: 使用不同的 Nacos 命名空间隔离环境
3. **敏感信息**: 通过环境变量或 Nacos 加密配置管理密码
4. **配置刷新**: 启用 Nacos 配置自动刷新(`refresh-enabled: true`)

### 性能优化

1. **连接池调优**: 根据负载调整 HikariCP 配置
2. **JVM 参数**: 配置合适的堆内存和 GC 策略
3. **日志级别**: 生产环境使用 INFO 或 WARN 级别
4. **Actuator 端点**: 仅暴露必要的端点,避免信息泄露

### 安全防护

1. **端口限制**: 仅内网暴露服务端口
2. **Actuator 认证**: 启用 Spring Security 保护 Actuator 端点
3. **敏感信息**: 不在日志中打印密码、Token 等敏感信息
4. **IP 白名单**: 限制 Actuator 端点访问来源

## 故障排查

### 常见问题

**Q: 启动时报错 "Failed to configure a DataSource"**
- **原因**: 数据库连接配置错误或数据库未启动
- **解决**:
  - 检查数据库连接配置(`url`、`username`、`password`)
  - 确保 MySQL 服务已启动
  - 检查数据库是否存在(`patra_storage`)

**Q: 服务无法注册到 Nacos**
- **原因**: Nacos 服务器未启动或配置错误
- **解决**:
  - 检查 Nacos 服务器是否可访问
  - 检查 `spring.cloud.nacos.discovery.server-addr` 配置
  - 检查命名空间是否存在

**Q: Flyway 迁移失败**
- **原因**: 数据库版本不兼容或迁移脚本错误
- **解决**:
  - 确保 MySQL 版本 >= 8.0
  - 检查迁移脚本语法
  - 必要时清理 `flyway_schema_history` 表重新迁移

### 日志查看

**本地日志文件**:
```bash
tail -f logs/patra-object-storage.log
```

**Docker 容器日志**:
```bash
docker logs -f patra-object-storage
```

**实时日志(Nacos)**:
- 访问 Nacos 控制台查看实时日志

## 相关文档

- **顶层 README**: 参见 `patra-object-storage/README.md` 了解整体架构
- **适配器层**: 参见 `patra-object-storage-adapter/README.md` 了解 REST 端点
- **基础设施层**: 参见 `patra-object-storage-infra/README.md` 了解数据库配置
