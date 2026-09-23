# 配置管理指南

## 环境配置

Patra 使用两个 profile：**dev（本地 IDE 开发）** 和 **container（容器化部署）**（`prod` 预留给未来真正的云生产）

```
patra-{service}-boot/src/main/resources/
├── application.yaml           # 基础配置
├── application-dev.yaml       # 本地 IDE 开发
└── application-container.yaml      # 容器化部署(环境变量注入)
```

## 配置优先级（由高到低）

1. 命令行参数 `--spring.datasource.url=...`
2. 环境变量 `SPRING_DATASOURCE_URL`
3. `application-{profile}.yaml`
4. `application.yaml`

## Nacos 服务发现配置

```yaml
spring:
  application:
    name: patra-catalog
  cloud:
    nacos:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_HOST:${PATRA_INFRA_HOST:127.0.0.1}}:${NACOS_PORT:8848}
        service: ${spring.application.name}
        fail-fast: true
```

Nacos 仅用于服务发现，不用于配置管理。

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
