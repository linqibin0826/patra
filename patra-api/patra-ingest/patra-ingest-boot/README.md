# patra-ingest-boot — 摄入服务启动模块

> **Spring Boot 启动入口**,组装所有模块并提供可执行 JAR。

---

## 概述

`patra-ingest-boot` 是 **patra-ingest 服务的启动模块(Boot Module)**,负责:

1. **Spring Boot 应用入口**: 提供 `main()` 方法和 `@SpringBootApplication` 注解
2. **依赖组装**: 聚合所有子模块依赖(adapter、infra、app、domain、api)
3. **配置管理**: 管理 Nacos 配置、应用配置文件
4. **可执行 JAR**: 打包为可执行的 Fat JAR
5. **架构验证**: 使用 ArchUnit 验证六边形架构规则

---

## 模块结构

```
patra-ingest-boot/
├─ src/main/java/.../
│  ├─ PatraIngestApplication.java          # Spring Boot 启动类
│  └─ config/
│     ├─ DomainFactoryConfiguration.java   # 领域工厂配置
│     └─ IngestErrorMappingContributor.java # 错误码映射贡献器
├─ src/main/resources/
│  ├─ application.yml                      # 主配置文件
│  ├─ application-dev.yml                  # 开发环境配置
│  ├─ application-prod.yml                 # 生产环境配置
│  ├─ ingest-error-config.yaml             # 错误码配置
│  └─ ingest-mq-config.yaml                # MQ 通道配置
└─ pom.xml                                 # 依赖聚合 + Fat JAR 打包
```

---

## 核心组件

### 1. PatraIngestApplication (启动类)

**职责**: Spring Boot 应用启动入口。

**核心代码**:
```java
@SpringBootApplication
public class PatraIngestApplication {

    public static void main(String[] args) {
        // 默认使用 'dev' 配置文件
        if (System.getProperty("spring.profiles.active") == null
            && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
            System.setProperty("spring.profiles.active", "dev");
        }
        SpringApplication.run(PatraIngestApplication.class, args);
    }
}
```

**特性**:
- **自动配置**: `@SpringBootApplication` 自动扫描 `com.patra.ingest` 包
- **默认配置文件**: 未指定时默认使用 `dev` 环境
- **Feign 客户端自动发现**: 通过 `patra-spring-cloud-starter-feign` 自动扫描 `com.patra.*.api.rpc.client` 包

**文件**: `PatraIngestApplication.java`

### 2. DomainFactoryConfiguration (领域工厂配置)

**职责**: 配置领域工厂 Bean,供应用层使用。

**文件**: `config/DomainFactoryConfiguration.java`

### 3. IngestErrorMappingContributor (错误码映射贡献器)

**职责**: 将 `IngestErrorCode` 注册到全局错误码映射器。

**文件**: `config/IngestErrorMappingContributor.java`

---

## 配置文件

### application.yml (主配置)

```yaml
server:
  port: 8082
  shutdown: graceful

spring:
  application:
    name: patra-ingest
  profiles:
    active: dev
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        group: ${NACOS_GROUP:PAPERTRACE}
      config:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        file-extension: yml
        shared-configs:
          - data-id: common-config.yml
            group: PAPERTRACE
            refresh: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

### application-dev.yml (开发环境)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/papertrace_ingest?useSSL=false
    username: root
    password: root

logging:
  level:
    root: INFO
    com.patra.ingest: DEBUG
```

### application-prod.yml (生产环境)

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

logging:
  level:
    root: INFO
    com.patra.ingest: INFO
```

---

## 依赖聚合

### pom.xml 依赖

```xml
<dependencies>
    <!-- Web Starter -->
    <dependency>
        <groupId>com.papertrace</groupId>
        <artifactId>patra-spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Nacos Discovery & Config -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>

    <!-- Actuator & Metrics -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>

    <!-- 子模块依赖 -->
    <dependency>
        <groupId>com.papertrace</groupId>
        <artifactId>patra-ingest-adapter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.papertrace</groupId>
        <artifactId>patra-ingest-infra</artifactId>
    </dependency>

    <!-- 对象存储 -->
    <dependency>
        <groupId>com.papertrace</groupId>
        <artifactId>patra-spring-boot-starter-object-storage</artifactId>
    </dependency>
    <dependency>
        <groupId>com.papertrace</groupId>
        <artifactId>patra-storage-api</artifactId>
    </dependency>

    <!-- ArchUnit (架构验证测试) -->
    <dependency>
        <groupId>com.tngtech.archunit</groupId>
        <artifactId>archunit-junit5</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 打包和部署

### 1. 构建 Fat JAR

```bash
cd patra-ingest/patra-ingest-boot
mvn clean package -DskipTests
```

**输出**: `target/patra-ingest-boot-0.1.0-SNAPSHOT.jar` (约 80MB)

### 2. 运行 JAR

```bash
java -jar target/patra-ingest-boot-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev
```

### 3. 指定 JVM 参数

```bash
java -Xms512m -Xmx2g \
  -Dspring.profiles.active=prod \
  -Dserver.port=8082 \
  -jar patra-ingest-boot-0.1.0-SNAPSHOT.jar
```

### 4. Docker 部署

```dockerfile
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY patra-ingest-boot-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8082

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms512m -Xmx2g"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**构建镜像**:
```bash
docker build -t papertrace/patra-ingest:0.1.0 .
```

**运行容器**:
```bash
docker run -d \
  --name patra-ingest \
  -p 8082:8082 \
  -e NACOS_SERVER_ADDR=nacos:8848 \
  -e DB_URL=jdbc:mysql://mysql:3306/papertrace_ingest \
  papertrace/patra-ingest:0.1.0
```

---

## 架构验证

### ArchUnit 测试

`patra-ingest-boot` 使用 ArchUnit 验证六边形架构规则:

```java
@AnalyzeClasses(packages = "com.patra.ingest")
public class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_infra =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infra..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_adapter =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule app_should_not_depend_on_adapter =
        noClasses()
            .that().resideInAPackage("..app..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");
}
```

**运行测试**:
```bash
mvn test -Dtest=HexagonalArchitectureTest
```

---

## 健康检查

### Actuator 端点

```bash
# 健康检查
curl http://localhost:8082/actuator/health

# 响应示例
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "nacos": {"status": "UP"},
    "rocketmq": {"status": "UP"}
  }
}
```

### Prometheus 指标

```bash
curl http://localhost:8082/actuator/prometheus
```

---

## 依赖关系

### 聚合的子模块
- `patra-ingest-adapter`: 适配器层
- `patra-ingest-infra`: 基础设施层
- `patra-ingest-app`: 应用层(通过 adapter 传递依赖)
- `patra-ingest-domain`: 领域层(通过 app 传递依赖)
- `patra-ingest-api`: API 契约(通过 adapter 传递依赖)

**依赖传递**: Boot → Adapter → App → Domain

---

## 启动顺序

1. **加载配置**: 从 Nacos 拉取远程配置,合并本地配置
2. **初始化 Spring Context**: 扫描所有 Bean,注入依赖
3. **连接数据库**: 初始化 MyBatis-Plus,创建连接池
4. **注册到 Nacos**: 将服务注册到 Nacos Discovery
5. **启动 XXL-Job 执行器**: 连接到 XXL-Job 调度中心
6. **启动 RocketMQ 消费者**: 订阅任务就绪 Topic
7. **暴露 Actuator 端点**: 提供健康检查和指标端点
8. **就绪**: 服务启动完成,开始接收请求

---

## 技术栈

- **Java**: 25
- **Spring Boot**: 3.5.7
- **Spring Cloud**: 2025.0.0
- **Nacos**: 服务发现 + 配置中心
- **Actuator**: 健康检查和指标
- **Prometheus**: 指标采集
- **ArchUnit**: 架构验证测试

---

## 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 激活的配置文件 | `dev` |
| `NACOS_SERVER_ADDR` | Nacos 服务地址 | `localhost:8848` |
| `NACOS_NAMESPACE` | Nacos 命名空间 | `dev` |
| `NACOS_GROUP` | Nacos 配置组 | `PAPERTRACE` |
| `DB_URL` | 数据库连接 URL | 无 |
| `DB_USERNAME` | 数据库用户名 | 无 |
| `DB_PASSWORD` | 数据库密码 | 无 |

---

**最后更新**: 2025-01-16
**Maven 坐标**: `com.papertrace:patra-ingest-boot:0.1.0-SNAPSHOT`
**作者**: linqibin
