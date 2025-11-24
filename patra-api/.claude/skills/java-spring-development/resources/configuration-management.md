# 配置管理指南

## Spring Boot 3 配置加载机制

Spring Boot 3 移除了 bootstrap context，配置优先级（由高到低）：

1. **命令行参数** - `--spring.datasource.url=...`
2. **环境变量** - `SPRING_DATASOURCE_URL`
3. **Nacos 配置中心** - 通过 `spring.config.import` 导入
4. **application-{profile}.yaml** - 环境特定配置
5. **application.yaml** - 基础配置

## Nacos 配置中心

### 基础配置

```yaml
# application.yaml
spring:
  application:
    name: patra-catalog
  cloud:
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      config:
        namespace: ${NACOS_NAMESPACE:}
        group: ${NACOS_GROUP:DEFAULT_GROUP}
        file-extension: yaml
        refresh-enabled: true
  config:
    # Spring Boot 3 使用 spring.config.import 导入 Nacos 配置
    import:
      - optional:nacos:${spring.application.name}.yaml
      - optional:nacos:${spring.application.name}-database.yaml?group=DATABASE_GROUP
```

**说明**：
- `optional:` 前缀表示配置不存在时不会导致启动失败
- 使用 `?group=XXX` 指定配置所属的 Group
- 后加载的配置会覆盖先加载的配置

### 动态配置刷新

```java
@RestController
@RefreshScope  // 支持配置动态刷新
public class ConfigController {
    @Value("${app.feature.enabled:false}")
    private boolean featureEnabled;
}
```

### 配置类模式

```java
@Component
@ConfigurationProperties(prefix = "app")
@RefreshScope
@Validated
@Data
public class AppConfig {
    @NotBlank
    private String name;

    @Min(1) @Max(60)
    private int timeout;
}
```

## 环境配置

Patra 项目使用两个环境：**dev（开发）** 和 **prod（生产）**

### application.yaml（基础配置）

```yaml
spring:
  application:
    name: patra-catalog
  cloud:
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
      config:
        namespace: ${NACOS_NAMESPACE:}
  config:
    import:
      - optional:nacos:${spring.application.name}.yaml
```

### application-dev.yaml（开发环境）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/patra_dev
    username: root
    password: dev_password
  cloud:
    nacos:
      server-addr: localhost:8848
      config:
        namespace: dev

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
  cloud:
    nacos:
      server-addr: ${NACOS_SERVER_ADDR}
      config:
        namespace: prod

logging:
  level:
    com.patra: INFO
```

**环境激活**：
```bash
# 开发环境
java -jar app.jar --spring.profiles.active=dev

# 生产环境
java -jar app.jar --spring.profiles.active=prod
```

## 环境隔离

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

### 4. Spring Boot 3 配置导入

```yaml
spring:
  config:
    import:
      # ✅ 使用 optional: 避免启动失败
      - optional:nacos:${spring.application.name}.yaml
      # ✅ 使用 Group 分组管理
      - optional:nacos:database.yaml?group=DATABASE_GROUP
```

## Spring Boot 3 迁移指南

### 从 Spring Boot 2.x 迁移

**移除的功能**：
- ❌ `bootstrap.yaml` / `bootstrap.properties` - 不再支持
- ❌ Spring Cloud Bootstrap Context - 已移除

**新的配置方式**：
- ✅ 使用 `spring.config.import` 导入外部配置
- ✅ 在 `application.yaml` 中配置 Nacos 连接信息

**迁移示例**：

```yaml
# Spring Boot 2.x（bootstrap.yaml）❌
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848

# Spring Boot 3（application.yaml）✅
spring:
  cloud:
    nacos:
      server-addr: localhost:8848
  config:
    import:
      - optional:nacos:${spring.application.name}.yaml
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
| 动态配置 | `@RefreshScope` + `@Value` | `@RefreshScope @Value("${app.feature}")` |
| 环境特定 Bean | `@Profile` | `@Bean @Profile("prod")` |

### 强制规范

✅ **必须遵守**：
1. Spring Boot 3 项目禁止使用 `bootstrap.yaml`
2. 使用 `spring.config.import` 导入 Nacos 配置
3. 仅使用 dev 和 prod 两个环境
4. 生产环境敏感信息通过环境变量注入
5. 所有配置属性必须提供默认值

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
- 在 Nacos 控制台修改配置
- 应用自动刷新（`@RefreshScope` 生效）

## 故障排查

### 配置不生效检查清单

```
1. [ ] 确认没有使用 bootstrap.yaml（Spring Boot 3 不支持）
2. [ ] 检查 spring.config.import 是否正确配置
3. [ ] 检查 @RefreshScope 注解是否添加
4. [ ] 检查 Nacos namespace 和 group 是否正确
5. [ ] 检查配置的 Data ID 是否匹配
6. [ ] 查看应用日志中的配置加载信息
```
