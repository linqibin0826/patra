# 配置管理指南

## Spring Boot 配置加载机制

Spring Boot 配置优先级（由高到低）：

1. **命令行参数** - `--spring.datasource.url=...`
2. **环境变量** - `SPRING_DATASOURCE_URL`
3. **application-{profile}.yaml** - 环境特定配置
4. **application.yaml** - 基础配置

## Consul 服务发现

### 基础配置

```yaml
# application.yaml
spring:
  application:
    name: patra-catalog
  cloud:
    consul:
      host: ${CONSUL_HOST:localhost}
      port: ${CONSUL_PORT:8500}
      discovery:
        service-name: ${spring.application.name}
        health-check-interval: 10s
        health-check-path: /actuator/health
        instance-id: ${spring.application.name}:${random.value}
```

**说明**：
- Consul 仅用于服务发现，不用于配置管理
- 健康检查通过 Spring Boot Actuator 的 `/actuator/health` 端点
- 实例 ID 使用随机值确保唯一性

## 环境配置

Patra 项目使用两个环境：**dev（开发）** 和 **prod（生产）**

### application.yaml（基础配置）

```yaml
spring:
  application:
    name: patra-catalog
  cloud:
    consul:
      host: ${CONSUL_HOST:localhost}
      port: ${CONSUL_PORT:8500}
      discovery:
        service-name: ${spring.application.name}
        health-check-interval: 10s
        health-check-path: /actuator/health
```

### application-dev.yaml（开发环境）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:13306/patra_catalog_dev
    username: root
    password: 123456

logging:
  level:
    com.patra: DEBUG
```

### application-prod.yaml（生产环境）

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

logging:
  level:
    com.patra: INFO
```

**环境激活**：
```bash
# 开发环境（默认）
java -jar app.jar

# 生产环境
java -jar app.jar --spring.profiles.active=prod
```

## 配置类模式

### 使用 @ConfigurationProperties

```java
@Component
@ConfigurationProperties(prefix = "app")
@Validated
@Data
public class AppConfig {
    @NotBlank
    private String name;

    @Min(1) @Max(60)
    private int timeout;
}
```

### 环境隔离

```java
@Configuration
public class EnvironmentConfig {
    @Bean
    @Profile("dev")
    public FeatureToggle devFeatureToggle() {
        return new DevFeatureToggle();
    }

    @Bean
    @Profile("prod")
    public FeatureToggle prodFeatureToggle() {
        return new ProdFeatureToggle();
    }
}
```

## 配置最佳实践

### 1. 配置命名规范

```yaml
# ✅ 正确：层级结构 + 语义化命名
app:
  ingest:
    pubmed:
      batch-size: 1000
      api-timeout: 30s

# ❌ 错误：扁平化命名
pubmedBatchSize: 1000
timeout1: 30
```

### 2. 默认值处理

```java
@Component
public class AppConfig {
    @Value("${app.cache.ttl:3600}")  // 总是提供默认值
    private int cacheTtl;
}
```

### 3. 敏感信息管理

```yaml
# ❌ 错误：硬编码
spring:
  datasource:
    password: MySecretPassword123

# ✅ 正确：环境变量
spring:
  datasource:
    password: ${DB_PASSWORD}
```

## Patra 项目配置规范

### 配置文件结构

```
patra-{service}-boot/
└── src/main/resources/
    ├── application.yaml           # 基础配置
    ├── application-dev.yaml       # 开发环境
    └── application-prod.yaml      # 生产环境
```

### 配置注入方式

| 场景 | 推荐方式 | 示例 |
|------|----------|------|
| 简单配置值 | `@Value` | `@Value("${app.timeout:30}")` |
| 复杂配置对象 | `@ConfigurationProperties` | `@ConfigurationProperties("app")` |
| 环境特定 Bean | `@Profile` | `@Bean @Profile("prod")` |

### 强制规范

✅ **必须遵守**：
1. 仅使用 dev 和 prod 两个环境
2. 生产环境敏感信息通过环境变量注入
3. 所有配置属性必须提供默认值
4. 服务发现使用 Consul

❌ **禁止行为**：
1. 硬编码敏感信息（密码、密钥、Token）
2. 使用 `@Profile("test")`（项目不存在 test 环境）
3. 在日志中输出敏感配置
4. 使用扁平化配置命名（如 `dbUrl`）

### 配置变更流程

**开发环境**：
- 直接修改 `application-dev.yaml`
- 提交到 Git 仓库

**生产环境**：
- 修改环境变量或配置文件
- 重启应用生效

## 故障排查

### 配置不生效检查清单

```
1. [ ] 检查环境变量是否正确设置
2. [ ] 检查 profile 是否正确激活
3. [ ] 检查配置文件名称是否正确（application-{profile}.yaml）
4. [ ] 查看应用日志中的配置加载信息
5. [ ] 确认 Consul 服务发现配置是否正确
```
