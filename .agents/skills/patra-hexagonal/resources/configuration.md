# 配置管理指南

## 环境配置

Patra 使用两个环境：**dev（开发）** 和 **prod（生产）**

```
patra-{service}-boot/src/main/resources/
├── application.yaml           # 基础配置
├── application-dev.yaml       # 开发环境
└── application-prod.yaml      # 生产环境
```

## 配置优先级（由高到低）

1. 命令行参数 `--spring.datasource.url=...`
2. 环境变量 `SPRING_DATASOURCE_URL`
3. `application-{profile}.yaml`
4. `application.yaml`

## Consul 服务发现配置

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
        instance-id: ${spring.application.name}:${random.value}
```

Consul 仅用于服务发现，不用于配置管理。

## 配置类模式

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

## 配置命名规范

```yaml
# 层级结构 + 语义化命名
app:
  ingest:
    pubmed:
      batch-size: 1000
      api-timeout: 30s
```

## 规范

- 生产环境敏感信息通过环境变量注入（`${DB_PASSWORD}`）
- 所有配置属性必须提供默认值
- 禁止硬编码敏感信息
- 禁止使用 `@Profile("test")`
- 禁止在日志中输出敏感配置
