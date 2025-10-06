# patra-egress-gateway 当前进度与下一步

## ✅ 已完成（Phase 1: Domain & API Layers）

### Domain 层（任务 2）
- ✅ HttpRequest、HttpResponse 值对象
- ✅ ResilienceConfig 值对象（validate、mergeWithMax）+ 15个单元测试
- ✅ RateLimitStatus、RetryAdvice、ResponseEnvelope 值对象 + 34个单元测试
- ✅ ResilienceConfigAggregate 聚合根 + 10个单元测试
- ✅ 领域端口接口（ConfigPort、HttpClientPort、RateLimiterPort、CircuitBreakerPort）

### API 层（任务 3）
- ✅ EgressErrors 错误码枚举（10个错误码：HTTP 对齐 + 业务错误）
- ✅ EgressException 基类 + 4个具体异常类
- ✅ 7个 DTOs（全部使用 record，带紧凑构造器）
- ✅ EgressGatewayClient Feign 客户端 API

### Infrastructure 层（任务 4）
- ✅ EgressProperties、GlobalProperties、ResilienceProperties 配置属性类
- ✅ YamlConfigRepository 实现 ConfigPort
- ✅ 7个配置加载集成测试

## 🔄 下一步优先级（Phase 2: Infrastructure & App Layers）

### P0（必须完成才能运行）
1. **任务 5**：实现 Infrastructure 层 HTTP 客户端
   - 配置 Spring RestClient
   - 实现 HttpClientAdapter（实现 HttpClientPort）
   - 实现敏感信息脱敏工具
   - 编写集成测试（使用 WireMock）

2. **任务 7**：实现 Application 层 Use Case
   - 定义 Command 和 Result 对象
   - 实现 ExternalCallOrchestrator（简化版，暂不包含弹性能力）
   - 实现 ResponseEnvelopeBuilder
   - 实现工具类（ResponseHashCalculator、HeaderWhitelistFilter）
   - 实现 DTO/Command/Result 转换

3. **任务 8**：实现 Adapter 层 REST Controller
   - 创建 ExternalCallController
   - 实现请求参数校验
   - 实现异常映射
   - 编写集成测试

4. **任务 10**：配置 Boot 层
   - 创建 Spring Boot 主类
   - 配置 application.yaml
   - 配置 egress-error-config.yaml
   - 确保依赖注入正确

### P1（增强稳定性）
1. **任务 6**：实现 Infrastructure 层弹性能力
   - 配置 Resilience4j
   - 实现 RateLimiterAdapter（全局限流）
   - 实现 CircuitBreakerAdapter（熔断）
   - 实现 RetryHandler（重试）

2. **任务 9**：实现可观测性
   - 实现日志记录（请求/响应/错误/弹性事件）
   - 实现指标收集（Micrometer）

### P2（完善系统）
1. **任务 11**：端到端集成测试
   - 准备测试基础设施（WireMock、测试配置）
   - 测试 PubMed/OSS 调用场景
   - 测试配置覆盖场景
   - 测试弹性能力组合场景

2. **任务 12**：文档和部署
   - 编写模块 README 和 API 文档
   - 创建 Dockerfile
   - 更新根 pom.xml

## 关键设计决策

### 1. 限流设计
- **全局限流**：使用 Resilience4j RateLimiter 实现（1000 次/秒）
- **业务方限流**：可传递更严格配置，但不超过全局最大值
- **限流状态**：区分网关限流和外部服务限流

### 2. 配置合并策略
1. 加载系统默认配置（default）和最大配置（max）
2. 如果业务方传递配置，与 max 比较，取较小值
3. 如果业务方未传递配置，使用 default
4. 超过最大值时记录警告日志

### 3. 响应封装策略
- **Body 哈希**：使用 SHA-256 计算，用于去重和取证
- **响应头白名单**：默认包括 Content-Type、X-RateLimit-*、Retry-After
- **敏感信息脱敏**：日志中不记录 Authorization、API-Key 等

### 4. 重试策略
- **可重试状态码**：429、408、5xx
- **退避策略**：指数退避，每次翻倍
- **Retry-After 支持**：优先使用外部服务返回的 Retry-After 头

### 5. DTO 转换策略
- **推荐手动转换**：DTO -> Command/Result 的转换逻辑较简单
- 如果转换逻辑复杂，可考虑使用 MapStruct
- 在任务 7.8 中明确决定

## 关键注意事项

### ConfigPort 接口设计
- 定义了 `loadSystemDefaultConfig()` 和 `loadSystemMaxConfig()` 两个方法
- 需要区分系统默认值和最大值，支持业务方配置覆盖

### 超时控制
- **注意**：超时控制通过 RestClient 的超时配置实现
- **不使用** Resilience4j TimeLimiter

### 错误处理
- **通常不需要**自定义 ErrorMappingContributor
- 使用 patra-common 的标准错误处理即可

### Feign 客户端调用
业务方使用方式：
1. 添加依赖：`patra-egress-gateway-api`
2. 启用 Feign：`@EnableFeignClients(clients = EgressGatewayClient.class)`
3. 注入使用：`@Autowired EgressGatewayClient egressGatewayClient`
