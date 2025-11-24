# 配置管理详细指南

## Nacos 配置中心

### 基础配置

```yaml
# bootstrap.yml
spring:
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        group: ${NACOS_GROUP:DEFAULT_GROUP}
        file-extension: yaml
        refresh-enabled: true
```

### 动态配置刷新

```java
@RestController
@RefreshScope  // 支持配置动态刷新
@RequiredArgsConstructor
public class ConfigController {

    @Value("${app.feature.enabled:false}")
    private boolean featureEnabled;

    @Value("${app.max-retry:3}")
    private int maxRetry;

    @GetMapping("/config/status")
    public Map<String, Object> getConfig() {
        return Map.of(
            "featureEnabled", featureEnabled,
            "maxRetry", maxRetry
        );
    }
}
```

### 配置类模式

```java
@Component
@ConfigurationProperties(prefix = "app.datasource")
@RefreshScope
@Data
public class DataSourceConfig {
    private String url;
    private String username;
    private String password;
    private int maxPoolSize = 10;
    private int minIdle = 5;
    private long connectionTimeout = 30000;
}
```

## 配置文件组织

### 环境配置分离

```yaml
# application.yml - 基础配置
spring:
  application:
    name: patra-catalog

# application-dev.yml - 开发环境
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/patra_dev

# application-test.yml - 测试环境
spring:
  datasource:
    url: jdbc:mysql://test.db.com:3306/patra_test

# application-prod.yml - 生产环境
spring:
  datasource:
    url: jdbc:mysql://prod.db.com:3306/patra_prod
```

### Nacos 配置分组

```yaml
# Nacos 配置中心的配置组织
# Data ID: patra-catalog.yaml
# Group: DEFAULT_GROUP
# 公共配置
common:
  timeout: 5000
  retry-times: 3

# Data ID: patra-catalog-database.yaml
# Group: DATABASE_GROUP
# 数据库配置
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

# Data ID: patra-catalog-redis.yaml
# Group: CACHE_GROUP
# 缓存配置
spring:
  redis:
    host: redis.server.com
    port: 6379
```

## 配置监听与处理

### 配置变更监听器

```java
@Component
@Slf4j
public class ConfigChangeListener implements ApplicationListener<RefreshScopeRefreshedEvent> {

    @Autowired
    private ApplicationContext context;

    @Override
    public void onApplicationEvent(RefreshScopeRefreshedEvent event) {
        log.info("配置已刷新，执行相关处理");

        // 重新初始化某些组件
        reinitializeComponents();

        // 清理缓存
        clearCache();
    }

    private void reinitializeComponents() {
        // 获取需要重新初始化的 Bean
        var dataSource = context.getBean(DataSourceConfig.class);
        log.info("数据源配置已更新: maxPoolSize={}", dataSource.getMaxPoolSize());
    }

    private void clearCache() {
        // 清理本地缓存
        log.info("清理本地缓存");
    }
}
```

### 自定义配置监听

```java
@Component
@Slf4j
public class CustomConfigListener {

    @NacosConfigListener(dataId = "patra-catalog-feature.yaml", autoRefreshed = true)
    public void onFeatureConfigChange(String config) {
        log.info("功能配置发生变化: {}", config);

        // 解析配置
        var features = parseFeatures(config);

        // 更新功能开关
        updateFeatureFlags(features);
    }

    @NacosValue(value = "${rate.limit:100}", autoRefreshed = true)
    private int rateLimit;

    @PostConstruct
    public void init() {
        log.info("初始限流配置: {}", rateLimit);
    }
}
```

## 多环境配置管理

### 配置优先级

```
1. 命令行参数
2. 环境变量
3. Nacos 配置中心
4. application-{profile}.yml
5. application.yml
```

### 环境隔离策略

```java
@Configuration
public class EnvironmentConfig {

    @Value("${spring.profiles.active}")
    private String activeProfile;

    @Bean
    @Profile("dev")
    public DataSource devDataSource() {
        // 开发环境数据源
        return DataSourceBuilder.create()
            .url("jdbc:mysql://localhost:3306/dev")
            .build();
    }

    @Bean
    @Profile("prod")
    public DataSource prodDataSource() {
        // 生产环境数据源
        return DataSourceBuilder.create()
            .url("jdbc:mysql://prod.db.com:3306/prod")
            .build();
    }

    @Bean
    public FeatureToggle featureToggle() {
        return switch (activeProfile) {
            case "dev" -> new DevFeatureToggle();
            case "test" -> new TestFeatureToggle();
            case "prod" -> new ProdFeatureToggle();
            default -> new DefaultFeatureToggle();
        };
    }
}
```

## 敏感配置管理

### 配置加密

```yaml
# Nacos 中存储加密后的配置
database:
  password: ENC(ASFASDFASDFA234ASDFASDF)

# 配置解密器
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD}
    algorithm: PBEWithMD5AndDES
```

### 敏感信息处理

```java
@Component
@Slf4j
public class SensitiveConfigProcessor {

    @Value("${database.password}")
    private String encryptedPassword;

    @Autowired
    private StringEncryptor encryptor;

    @PostConstruct
    public void init() {
        // 解密密码
        String decrypted = encryptor.decrypt(encryptedPassword);

        // 不要记录敏感信息
        log.info("数据库连接已初始化");
        // ❌ 错误：log.info("数据库密码: {}", decrypted);
    }

    public String getDecryptedPassword() {
        return encryptor.decrypt(encryptedPassword);
    }
}
```

## 配置版本管理

### Nacos 配置版本控制

```java
@Component
public class ConfigVersionManager {

    @Autowired
    private NacosConfigManager configManager;

    public void saveConfigWithVersion(String dataId, String content, String comment) {
        // Nacos 自动保存配置历史版本
        configManager.getConfigService().publishConfig(
            dataId,
            "DEFAULT_GROUP",
            content,
            ConfigType.YAML.getType()
        );

        log.info("配置已发布: dataId={}, comment={}", dataId, comment);
    }

    public String rollbackConfig(String dataId, int version) {
        // 回滚到指定版本
        var historyConfig = configManager.getConfigService()
            .getConfigHistory(dataId, "DEFAULT_GROUP", version);

        configManager.getConfigService().publishConfig(
            dataId,
            "DEFAULT_GROUP",
            historyConfig,
            ConfigType.YAML.getType()
        );

        return historyConfig;
    }
}
```

## 配置最佳实践

### 1. 配置分层管理
```yaml
# 基础设施配置
infrastructure:
  database:
    pool-size: 20
  redis:
    timeout: 5000

# 业务配置
business:
  order:
    timeout: 30000
    max-retry: 3
  payment:
    gateway: alipay
```

### 2. 配置命名规范
```yaml
# ✅ 正确：使用清晰的层级结构
app:
  feature:
    payment:
      enabled: true
      timeout: 5000

# ❌ 错误：扁平化配置
paymentEnabled: true
paymentTimeout: 5000
```

### 3. 默认值处理
```java
@Component
public class AppConfig {
    // 总是提供合理的默认值
    @Value("${app.cache.ttl:3600}")
    private int cacheTtl;

    @Value("${app.retry.max:3}")
    private int maxRetry;

    @Value("${app.feature.new:false}")
    private boolean newFeature;
}
```

### 4. 配置验证
```java
@Component
@ConfigurationProperties(prefix = "app")
@Validated
public class ApplicationConfig {

    @NotNull(message = "服务名称不能为空")
    private String name;

    @Min(value = 1, message = "超时时间至少 1 秒")
    @Max(value = 60, message = "超时时间最多 60 秒")
    private int timeout;

    @Valid
    @NotNull
    private DatabaseConfig database;

    @Data
    public static class DatabaseConfig {
        @NotBlank
        private String url;

        @Min(1)
        @Max(100)
        private int poolSize;
    }
}
```

## 故障排查

### 配置不生效检查清单

```
1. [ ] 检查 @RefreshScope 注解是否添加
2. [ ] 检查 Nacos namespace 和 group 是否正确
3. [ ] 检查配置文件优先级
4. [ ] 检查配置的 Data ID 是否匹配
5. [ ] 检查网络连接和 Nacos 服务状态
6. [ ] 查看应用日志中的配置加载信息
```

### 调试配置

```java
@RestController
@RequestMapping("/debug")
public class ConfigDebugController {

    @Autowired
    private Environment env;

    @Autowired
    private ConfigurableEnvironment configurableEnv;

    @GetMapping("/config/{key}")
    public String getConfig(@PathVariable String key) {
        return env.getProperty(key, "未找到配置");
    }

    @GetMapping("/sources")
    public List<String> getPropertySources() {
        return configurableEnv.getPropertySources().stream()
            .map(PropertySource::getName)
            .toList();
    }
}
```