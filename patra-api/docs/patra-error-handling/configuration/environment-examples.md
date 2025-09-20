# Patra 错误处理系统 - 环境配置示例

不同环境下的完整配置示例，包括开发、测试、预发布和生产环境的最佳实践配置。

## 目录

1. [开发环境配置](#开发环境配置)
2. [测试环境配置](#测试环境配置)
3. [预发布环境配置](#预发布环境配置)
4. [生产环境配置](#生产环境配置)
5. [Docker容器配置](#docker容器配置)
6. [Kubernetes配置](#kubernetes配置)
7. [配置管理最佳实践](#配置管理最佳实践)

## 开发环境配置

### application-dev.yml

```yaml
# 开发环境 - 详细日志和调试功能
spring:
  profiles:
    active: dev
  
  # 数据库配置
  datasource:
    url: jdbc:mysql://localhost:13306/patra_registry?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  # Jackson配置
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false
      indent-output: true  # 开发环境美化JSON输出
    deserialization:
      fail-on-unknown-properties: false

# 错误处理配置 - 开发环境设置
patra:
  error:
    enabled: true
    context-prefix: REG
    map-status:
      strategy: suffix-heuristic
  web:
    problem:
      enabled: true
      type-base-url: "https://dev-errors.patra.com/"
      include-stack: true  # 开发环境启用堆栈跟踪
  feign:
    problem:
      enabled: true
      tolerant: true
  tracing:
    header-names:
      - traceId
      - X-B3-TraceId
      - traceparent
      - X-Request-ID
      - X-Correlation-ID

# 详细日志配置 - 开发环境
logging:
  level:
    # 应用日志
    com.patra.registry: DEBUG
    com.patra.starter: DEBUG
    
    # Spring框架日志
    org.springframework.web: DEBUG
    org.springframework.transaction: DEBUG
    org.springframework.security: DEBUG
    
    # 数据库日志
    org.springframework.jdbc: DEBUG
    com.baomidou.mybatisplus: DEBUG
    
    # HTTP客户端日志
    feign: DEBUG
    org.apache.http: DEBUG
    
    # 根日志级别
    root: INFO
  
  # 日志格式
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId:-}] %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId:-}] %logger{36} - %msg%n"
  
  # 日志文件
  file:
    name: logs/patra-registry-dev.log
    max-size: 100MB
    max-history: 7

# 开发工具配置
spring:
  devtools:
    restart:
      enabled: true
    livereload:
      enabled: true

# 管理端点配置
management:
  endpoints:
    web:
      exposure:
        include: "*"  # 开发环境暴露所有端点
  endpoint:
    health:
      show-details: always
    env:
      show-values: always
```

### 开发环境特定配置

```yaml
# application-dev-local.yml - 本地开发覆盖
patra:
  web:
    problem:
      type-base-url: "http://localhost:8080/errors/"  # 本地错误文档
      include-stack: true

logging:
  level:
    com.patra: TRACE  # 本地开发最详细日志
```

## 测试环境配置

### application-test.yml

```yaml
# 测试环境 - 稳定性和可重复性
spring:
  profiles:
    active: test
  
  # 内存数据库用于测试
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password: 
    driver-class-name: org.h2.Driver
  
  # H2控制台（仅测试环境）
  h2:
    console:
      enabled: true
      path: /h2-console
  
  # JPA配置
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    properties:
      hibernate:
        format_sql: false

# 错误处理配置 - 测试环境设置
patra:
  error:
    enabled: true
    context-prefix: TEST
  web:
    problem:
      enabled: true
      type-base-url: "https://test-errors.patra.com/"
      include-stack: false  # 测试环境不包含堆栈跟踪
  feign:
    problem:
      enabled: true
      tolerant: true
  tracing:
    header-names:
      - traceId
      - X-Test-Trace-Id

# 测试环境日志配置
logging:
  level:
    # 应用日志
    com.patra.registry: INFO
    com.patra.starter: WARN
    
    # 框架日志
    org.springframework: WARN
    org.hibernate: WARN
    
    # 根日志级别
    root: ERROR
  
  # 简化的日志格式
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

# 测试特定配置
spring:
  test:
    database:
      replace: none  # 不替换数据源配置

# 禁用不必要的功能
management:
  endpoints:
    web:
      exposure:
        include: health,info  # 仅暴露必要端点
```

### 集成测试配置

```yaml
# application-integration-test.yml
patra:
  error:
    context-prefix: ITEST
  web:
    problem:
      type-base-url: "https://test.example.com/errors/"

# 测试数据库
spring:
  datasource:
    url: jdbc:mysql://test-db:3306/patra_test
    username: ${TEST_DB_USER:test_user}
    password: ${TEST_DB_PASSWORD:test_pass}

# 外部服务Mock配置
wiremock:
  server:
    port: 8089
    
# 测试超时配置
feign:
  client:
    config:
      default:
        connect-timeout: 5000
        read-timeout: 10000
```

## 预发布环境配置

### application-staging.yml

```yaml
# 预发布环境 - 生产环境的镜像
spring:
  profiles:
    active: staging
  
  # 数据库配置
  datasource:
    url: ${STAGING_DB_URL}
    username: ${STAGING_DB_USER}
    password: ${STAGING_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
    
    # 连接池配置
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000
      validation-timeout: 5000

# 错误处理配置 - 预发布环境
patra:
  error:
    enabled: true
    context-prefix: REG
  web:
    problem:
      enabled: true
      type-base-url: "https://staging-errors.patra.com/"
      include-stack: false  # 预发布环境不包含堆栈跟踪
  feign:
    problem:
      enabled: true
      tolerant: true
  tracing:
    header-names:
      - traceId
      - X-B3-TraceId
      - traceparent

# 预发布环境日志配置
logging:
  level:
    # 应用日志
    com.patra.registry: INFO
    com.patra.starter: INFO
    
    # 框架日志
    org.springframework: WARN
    
    # 根日志级别
    root: WARN
  
  # 结构化日志格式
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId:-}] %logger{36} - %msg%n"
  
  # 日志文件配置
  file:
    name: /var/log/patra/patra-registry-staging.log
    max-size: 500MB
    max-history: 30

# 监控配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true

# 安全配置
server:
  port: 8080
  servlet:
    context-path: /api
  tomcat:
    max-threads: 200
    min-spare-threads: 10
```

## 生产环境配置

### application-prod.yml

```yaml
# 生产环境 - 高性能和安全性
spring:
  profiles:
    active: prod
  
  # 数据库配置
  datasource:
    url: ${PROD_DB_URL}
    username: ${PROD_DB_USER}
    password: ${PROD_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
    
    # 生产环境连接池配置
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      idle-timeout: 600000
      connection-timeout: 30000
      validation-timeout: 5000
      leak-detection-threshold: 60000

# 错误处理配置 - 生产环境
patra:
  error:
    enabled: true
    context-prefix: REG
  web:
    problem:
      enabled: true
      type-base-url: "https://errors.patra.com/"
      include-stack: false  # 生产环境绝不包含堆栈跟踪
  feign:
    problem:
      enabled: true
      tolerant: true
  tracing:
    header-names:
      - traceId
      - X-B3-TraceId
      - traceparent

# 生产环境日志配置
logging:
  level:
    # 应用日志
    com.patra.registry: INFO
    com.patra.starter: WARN
    
    # 框架日志
    org.springframework: WARN
    
    # 根日志级别
    root: WARN
  
  # 结构化JSON日志格式（用于日志收集）
  pattern:
    console: '{"timestamp":"%d{yyyy-MM-dd HH:mm:ss.SSS}","level":"%-5level","thread":"%thread","traceId":"%X{traceId:-}","logger":"%logger{36}","message":"%msg"}%n'
  
  # 日志文件配置
  file:
    name: /var/log/patra/patra-registry.log
    max-size: 1GB
    max-history: 90
    total-size-cap: 10GB

# 生产环境性能配置
server:
  port: 8080
  servlet:
    context-path: /api
  tomcat:
    max-threads: 500
    min-spare-threads: 25
    accept-count: 100
    max-connections: 8192
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/xml,text/plain

# 监控配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: never  # 生产环境不暴露详细信息
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: patra-registry
      environment: production

# JVM配置
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 生产环境仅验证
    show-sql: false
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
```

### 生产环境安全配置

```yaml
# application-prod-security.yml
server:
  ssl:
    enabled: true
    key-store: ${SSL_KEYSTORE_PATH}
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12

# 安全头配置
spring:
  security:
    headers:
      frame-options: DENY
      content-type-options: nosniff
      xss-protection: 1; mode=block
      referrer-policy: strict-origin-when-cross-origin

# 敏感信息掩码
logging:
  pattern:
    console: '{"timestamp":"%d{yyyy-MM-dd HH:mm:ss.SSS}","level":"%-5level","thread":"%thread","traceId":"%X{traceId:-}","logger":"%logger{36}","message":"%replace(%msg){"password":"[^"]*","password":"***"}"}%n'
```

## Docker容器配置

### Dockerfile

```dockerfile
FROM openjdk:21-jre-slim

# 创建应用用户
RUN groupadd -r patra && useradd -r -g patra patra

# 创建应用目录
WORKDIR /app

# 复制应用JAR
COPY target/patra-registry-boot-*.jar app.jar

# 创建日志目录
RUN mkdir -p /var/log/patra && chown -R patra:patra /var/log/patra

# 切换到应用用户
USER patra

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml

```yaml
version: '3.8'

services:
  patra-registry:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - PATRA_ERROR_CONTEXT_PREFIX=REG
      - PATRA_WEB_PROBLEM_TYPE_BASE_URL=https://errors.patra.com/
      - PATRA_WEB_PROBLEM_INCLUDE_STACK=false
      - DB_URL=jdbc:mysql://mysql:3306/patra_registry
      - DB_USER=patra_user
      - DB_PASSWORD=patra_pass
    depends_on:
      - mysql
    volumes:
      - ./logs:/var/log/patra
    networks:
      - patra-network

  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=root_password
      - MYSQL_DATABASE=patra_registry
      - MYSQL_USER=patra_user
      - MYSQL_PASSWORD=patra_pass
    volumes:
      - mysql_data:/var/lib/mysql
    networks:
      - patra-network

volumes:
  mysql_data:

networks:
  patra-network:
    driver: bridge
```

### application-docker.yml

```yaml
# Docker环境配置
spring:
  profiles:
    active: docker
  
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}

patra:
  error:
    enabled: true
    context-prefix: ${PATRA_ERROR_CONTEXT_PREFIX:REG}
  web:
    problem:
      enabled: true
      type-base-url: ${PATRA_WEB_PROBLEM_TYPE_BASE_URL:https://errors.example.com/}
      include-stack: ${PATRA_WEB_PROBLEM_INCLUDE_STACK:false}

logging:
  level:
    com.patra: INFO
    root: WARN
  file:
    name: /var/log/patra/patra-registry.log
```

## Kubernetes配置

### ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: patra-registry-config
  namespace: patra
data:
  application.yml: |
    spring:
      profiles:
        active: k8s
    
    patra:
      error:
        enabled: true
        context-prefix: REG
      web:
        problem:
          enabled: true
          type-base-url: "https://errors.patra.com/"
          include-stack: false
      feign:
        problem:
          enabled: true
          tolerant: true
    
    logging:
      level:
        com.patra.registry: INFO
        com.patra.starter: WARN
        root: WARN
      pattern:
        console: '{"timestamp":"%d{yyyy-MM-dd HH:mm:ss.SSS}","level":"%-5level","thread":"%thread","traceId":"%X{traceId:-}","logger":"%logger{36}","message":"%msg"}%n'
    
    management:
      endpoints:
        web:
          exposure:
            include: health,info,metrics,prometheus
      endpoint:
        health:
          show-details: never
```

### Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: patra-registry-secret
  namespace: patra
type: Opaque
data:
  db-url: <base64-encoded-db-url>
  db-user: <base64-encoded-db-user>
  db-password: <base64-encoded-db-password>
```

### Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: patra-registry
  namespace: patra
spec:
  replicas: 3
  selector:
    matchLabels:
      app: patra-registry
  template:
    metadata:
      labels:
        app: patra-registry
    spec:
      containers:
      - name: patra-registry
        image: patra/registry:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: DB_URL
          valueFrom:
            secretKeyRef:
              name: patra-registry-secret
              key: db-url
        - name: DB_USER
          valueFrom:
            secretKeyRef:
              name: patra-registry-secret
              key: db-user
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: patra-registry-secret
              key: db-password
        volumeMounts:
        - name: config
          mountPath: /app/config
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
      volumes:
      - name: config
        configMap:
          name: patra-registry-config
```

## 配置管理最佳实践

### 1. 环境变量优先级

```yaml
# 配置优先级（从高到低）：
# 1. 环境变量
# 2. application-{profile}.yml
# 3. application.yml
# 4. 默认值

patra:
  error:
    context-prefix: ${PATRA_ERROR_CONTEXT_PREFIX:REG}  # 环境变量优先
  web:
    problem:
      type-base-url: ${PATRA_TYPE_BASE_URL:https://errors.example.com/}
      include-stack: ${PATRA_INCLUDE_STACK:false}
```

### 2. 敏感信息管理

```yaml
# 使用外部配置管理敏感信息
spring:
  datasource:
    url: ${DB_URL}  # 从环境变量或密钥管理系统获取
    username: ${DB_USER}
    password: ${DB_PASSWORD}

# 避免在配置文件中硬编码敏感信息
# ❌ 错误做法
# spring:
#   datasource:
#     password: "hardcoded_password"

# ✅ 正确做法
spring:
  datasource:
    password: ${DB_PASSWORD}
```

### 3. 配置验证

```java
@Component
@ConfigurationProperties(prefix = "patra.error")
@Validated
public class ErrorProperties {
    
    @NotBlank(message = "Context prefix is required")
    private String contextPrefix;
    
    @Valid
    private WebProblemProperties web = new WebProblemProperties();
    
    // getters and setters
}
```

### 4. 配置文档化

```yaml
# 为每个配置项添加注释
patra:
  error:
    # 服务错误代码前缀，必须唯一（例如：REG, ORD, INV）
    context-prefix: REG
    
  web:
    problem:
      # 是否启用Web错误处理
      enabled: true
      
      # ProblemDetail type字段的基础URL
      type-base-url: "https://errors.patra.com/"
      
      # 是否在响应中包含堆栈跟踪（仅开发环境）
      include-stack: false
```

### 5. 配置测试

```java
@SpringBootTest
@TestPropertySource(properties = {
    "patra.error.context-prefix=TEST",
    "patra.web.problem.enabled=true"
})
class ConfigurationValidationTest {
    
    @Autowired
    private ErrorProperties errorProperties;
    
    @Test
    void shouldLoadValidConfiguration() {
        assertThat(errorProperties.getContextPrefix()).isEqualTo("TEST");
        assertThat(errorProperties.isEnabled()).isTrue();
    }
    
    @Test
    void shouldFailWithInvalidConfiguration() {
        // 测试无效配置的处理
    }
}
```

这些环境配置示例提供了从开发到生产的完整配置参考，确保在不同环境中都能获得最佳的错误处理体验。