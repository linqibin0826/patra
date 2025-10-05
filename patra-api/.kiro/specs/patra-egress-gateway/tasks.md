# patra-egress-gateway 实现任务列表

## 任务概述

本任务列表将 patra-egress-gateway 的设计转化为可执行的编码任务。遵循测试驱动开发（TDD）原则，优先实现核心功能，确保每个步骤都是增量的、可测试的。

## 任务列表

- [ ] 1. 创建项目骨架和模块结构
  - 创建 patra-egress-gateway 聚合模块及其子模块（api/domain/app/infra/adapter/boot）
  - 配置 Maven 依赖管理和编译插件
  - 创建基础包结构
  - _需求: 需求 8（多类型外部服务支持）_

- [ ] 2. 实现 Domain 层核心模型
  - 定义值对象（HttpRequest、HttpResponse、ResilienceConfig、ResponseEnvelope 等）
  - 实现 ResilienceConfigAggregate 聚合根
  - 定义领域端口接口（ConfigPort、HttpClientPort、RateLimiterPort、CircuitBreakerPort）
  - _需求: 需求 2（弹性配置管理）、需求 4（统一响应语义封装）_

- [ ] 2.1 实现 HttpRequest 和 HttpResponse 值对象
  - 创建 HttpRequest record（url、method、headers、body）
  - 创建 HttpResponse record（statusCode、headers、body）
  - 添加 isSuccess() 方法判断 2xx 状态码
  - _需求: 需求 1（外部服务调用透传）_

- [ ] 2.2 实现 ResilienceConfig 值对象
  - 创建 ResilienceConfig record（不包含 rateLimit）
  - 实现 validate() 校验方法
  - 实现 mergeWithMax() 合并方法
  - _需求: 需求 2（弹性配置管理）_

- [ ]* 2.3 编写 ResilienceConfig 单元测试
  - 测试配置校验逻辑（负值、零值等边界情况）
  - 测试配置合并逻辑（不超过最大值）
  - _需求: 需求 2（弹性配置管理）_

- [ ] 2.4 实现响应相关值对象
  - 创建 RateLimitStatus record（区分 Gateway 和外部服务限流）
  - 创建 ExternalRateLimitInfo record 并实现 fromHeaders() 方法
  - 创建 RetryAdvice record 并实现 fromResponse() 方法
  - 创建 ResponseEnvelope record
  - _需求: 需求 4（统一响应语义封装）、需求 5（弹性能力实现）_


- [ ]* 2.5 编写响应值对象单元测试
  - 测试 ExternalRateLimitInfo.fromHeaders() 解析逻辑
  - 测试 RetryAdvice.fromResponse() 生成逻辑
  - 测试 HttpResponse.isSuccess() 判断逻辑
  - _需求: 需求 4（统一响应语义封装）_

- [ ] 2.6 实现 ResilienceConfigAggregate 聚合根
  - 实现 loadSystemConfig() 静态工厂方法
  - 实现 mergeWithCallerConfig() 方法
  - 实现 validate() 方法
  - _需求: 需求 2（弹性配置管理）_

- [ ]* 2.7 编写 ResilienceConfigAggregate 单元测试
  - 测试配置加载逻辑
  - 测试配置合并逻辑（调用方配置超过最大值时使用最大值）
  - 测试配置校验逻辑
  - _需求: 需求 2（弹性配置管理）_

- [ ] 2.8 定义领域端口接口
  - 定义 ConfigPort 接口（loadSystemConfig）
  - 定义 HttpClientPort 接口（call）
  - 定义 RateLimiterPort 接口（tryAcquire、getStatus）
  - 定义 CircuitBreakerPort 接口（executeWithCircuitBreaker、getState）
  - _需求: 需求 3（配置源演进支持）、需求 5（弹性能力实现）_

- [ ] 3. 实现 API 层错误码和 DTOs
  - 定义 EgressErrors 错误码枚举
  - 定义领域异常类（EgressException、ConfigValidationException 等）
  - 定义外部 DTOs（ExternalCallRequest、ExternalCallResponse）
  - _需求: 需求 7（错误处理与标准化）_

- [ ] 3.1 定义错误码枚举
  - 创建 EgressErrors 枚举（HTTP 对齐错误 0xxx 段）
  - 创建业务错误码（1xxx+ 段）
  - _需求: 需求 7（错误处理与标准化）_

- [ ] 3.2 实现领域异常类
  - 创建 EgressException 基类（继承 ApplicationException）
  - 创建具体异常类（ConfigValidationException、RateLimitExceededException 等）
  - _需求: 需求 7（错误处理与标准化）_

- [ ] 3.3 定义外部 DTOs
  - 创建 ExternalCallRequest DTO（映射到 ExternalCallCommand）
  - 创建 ExternalCallResponse DTO（映射到 ExternalCallResult）
  - _需求: 需求 1（外部服务调用透传）_

- [ ] 4. 实现 Infrastructure 层配置加载
  - 实现 YamlConfigRepository（从 YAML 加载配置）
  - 创建配置属性类（EgressProperties）
  - 实现配置校验逻辑
  - _需求: 需求 2（弹性配置管理）、需求 3（配置源演进支持）_


- [ ] 4.1 创建配置属性类
  - 创建 EgressProperties 类（映射 patra.egress 配置）
  - 创建 GlobalProperties 类（映射 global.rateLimit）
  - 创建 ResilienceProperties 类（映射 resilience.max 和 resilience.default）
  - _需求: 需求 2（弹性配置管理）_

- [ ] 4.2 实现 YamlConfigRepository
  - 实现 ConfigPort 接口
  - 从 EgressProperties 加载系统配置
  - 提供 getMaxConfig() 方法
  - _需求: 需求 3（配置源演进支持）_

- [ ]* 4.3 编写配置加载集成测试
  - 测试从 YAML 加载配置
  - 测试配置校验失败场景
  - _需求: 需求 2（弹性配置管理）_

- [ ] 5. 实现 Infrastructure 层 HTTP 客户端
  - 配置 Spring RestClient
  - 实现 HttpClientAdapter（实现 HttpClientPort）
  - 实现超时配置
  - 实现敏感信息脱敏工具
  - _需求: 需求 1（外部服务调用透传）、需求 6（可观测性）_

- [ ] 5.1 配置 Spring RestClient
  - 创建 HttpClientConfig 配置类
  - 配置 RestClient Bean（设置超时、连接池等）
  - _需求: 需求 1（外部服务调用透传）_

- [ ] 5.2 实现 HttpClientAdapter
  - 实现 HttpClientPort.call() 方法
  - 支持各种 HTTP 方法（GET、POST、PUT、DELETE 等）
  - 支持自定义 Headers 和 Body
  - 应用超时配置
  - _需求: 需求 1（外部服务调用透传）_

- [ ] 5.3 实现敏感信息脱敏工具
  - 创建 SensitiveHeaderMasker 工具类
  - 实现 mask() 方法（脱敏 Authorization、API-Key 等）
  - _需求: 需求 6（可观测性）_

- [ ]* 5.4 编写 HTTP 客户端集成测试
  - 使用 WireMock 模拟外部服务
  - 测试各种 HTTP 方法调用
  - 测试超时场景
  - 测试敏感信息脱敏
  - _需求: 需求 1（外部服务调用透传）_

- [ ] 6. 实现 Infrastructure 层弹性能力
  - 配置 Resilience4j
  - 实现 RateLimiterAdapter（全局限流）
  - 实现 CircuitBreakerAdapter（熔断）
  - 实现 RetryHandler（重试）
  - _需求: 需求 5（弹性能力实现）_


- [ ] 6.1 配置 Resilience4j
  - 添加 Resilience4j 依赖
  - 创建 Resilience4jConfig 配置类
  - 配置 RateLimiter、CircuitBreaker、Retry、TimeLimiter
  - _需求: 需求 5（弹性能力实现）_

- [ ] 6.2 实现 RateLimiterAdapter
  - 实现 RateLimiterPort 接口
  - 使用 Resilience4j RateLimiter 实现全局限流
  - 实现 tryAcquire() 方法
  - 实现 getStatus() 方法（返回限流状态）
  - _需求: 需求 5（弹性能力实现）_

- [ ]* 6.3 编写 RateLimiterAdapter 单元测试
  - 测试限流通过场景
  - 测试限流拒绝场景
  - 测试限流状态查询
  - _需求: 需求 5（弹性能力实现）_

- [ ] 6.4 实现 CircuitBreakerAdapter
  - 实现 CircuitBreakerPort 接口
  - 使用 Resilience4j CircuitBreaker 实现熔断
  - 实现 executeWithCircuitBreaker() 方法
  - 实现 getState() 方法（返回熔断状态）
  - _需求: 需求 5（弹性能力实现）_

- [ ]* 6.5 编写 CircuitBreakerAdapter 单元测试
  - 测试熔断器关闭状态
  - 测试熔断器打开场景（失败率超过阈值）
  - 测试熔断器半开状态
  - _需求: 需求 5（弹性能力实现）_

- [ ] 6.6 实现 RetryHandler
  - 使用 Resilience4j Retry 实现重试逻辑
  - 支持指数退避策略
  - 支持可重试的状态码和异常
  - _需求: 需求 5（弹性能力实现）_

- [ ]* 6.7 编写 RetryHandler 单元测试
  - 测试重试成功场景
  - 测试重试达到最大次数场景
  - 测试指数退避延迟
  - _需求: 需求 5（弹性能力实现）_

- [ ] 7. 实现 Application 层 Use Case
  - 定义 ExternalCallUseCase 接口
  - 实现 ExternalCallOrchestrator
  - 实现配置合并与校验逻辑
  - 实现响应封装逻辑
  - _需求: 需求 1（外部服务调用透传）、需求 2（弹性配置管理）、需求 4（统一响应语义封装）_

- [ ] 7.1 定义 Command 和 Result 对象
  - 创建 ExternalCallCommand record
  - 创建 ExternalCallResult record
  - _需求: 需求 1（外部服务调用透传）_


- [ ] 7.2 定义 ExternalCallUseCase 接口
  - 定义 execute(ExternalCallCommand) 方法
  - _需求: 需求 1（外部服务调用透传）_

- [ ] 7.3 实现 ExternalCallOrchestrator
  - 实现 ExternalCallUseCase 接口
  - 注入依赖（ConfigPort、HttpClientPort、RateLimiterPort、CircuitBreakerPort）
  - 实现 execute() 方法框架
  - _需求: 需求 1（外部服务调用透传）_

- [ ] 7.4 实现配置合并与校验逻辑
  - 加载系统级配置
  - 合并调用方配置（不超过最大值）
  - 校验配置有效性
  - 记录配置覆盖警告日志
  - _需求: 需求 2（弹性配置管理）_

- [ ] 7.5 实现弹性能力编排
  - 应用全局限流（tryAcquire）
  - 应用熔断器（executeWithCircuitBreaker）
  - 应用重试逻辑
  - 应用超时控制
  - _需求: 需求 5（弹性能力实现）_

- [ ] 7.6 实现响应封装逻辑
  - 创建 ResponseEnvelopeBuilder 工具类
  - 实现 build() 方法（封装响应为 ResponseEnvelope）
  - 提取外部服务限流信息
  - 生成重试建议
  - 计算响应 Body 哈希
  - 过滤响应头白名单
  - _需求: 需求 4（统一响应语义封装）_

- [ ]* 7.7 编写 ExternalCallOrchestrator 单元测试
  - 测试完整调用流程（成功场景）
  - 测试配置合并逻辑
  - 测试限流拒绝场景
  - 测试熔断场景
  - 测试重试场景
  - 测试超时场景
  - _需求: 需求 1-5（核心功能）_

- [ ] 8. 实现 Adapter 层 REST Controller
  - 创建 ExternalCallController
  - 实现 POST /api/egress/call 接口
  - 实现请求参数校验
  - 实现异常映射为 ProblemDetail
  - _需求: 需求 1（外部服务调用透传）、需求 7（错误处理与标准化）_

- [ ] 8.1 创建 ExternalCallController
  - 定义 POST /api/egress/call 接口
  - 接收 ExternalCallRequest DTO
  - 转换为 ExternalCallCommand
  - 调用 ExternalCallUseCase
  - 返回 ExternalCallResponse DTO
  - _需求: 需求 1（外部服务调用透传）_


- [ ] 8.2 实现请求参数校验
  - 使用 @Valid 注解校验请求参数
  - 校验 URL 不为空
  - 校验 HTTP 方法不为空
  - 校验 ResilienceConfig 有效性（如果传递）
  - _需求: 需求 2（弹性配置管理）_

- [ ] 8.3 实现异常映射
  - 创建 ErrorMappingContributor（如果需要自定义映射）
  - 确保所有领域异常映射为 ProblemDetail
  - _需求: 需求 7（错误处理与标准化）_

- [ ]* 8.4 编写 Controller 集成测试
  - 测试成功调用场景
  - 测试参数校验失败场景
  - 测试限流场景（返回 429）
  - 测试熔断场景（返回 503）
  - 测试超时场景（返回 504）
  - 测试 ProblemDetail 输出格式
  - _需求: 需求 1、7（调用和错误处理）_

- [ ] 9. 实现可观测性
  - 实现日志记录（请求/响应/错误/弹性事件）
  - 实现指标收集（Micrometer）
  - 配置日志格式和级别
  - _需求: 需求 6（可观测性）_

- [ ] 9.1 实现日志记录
  - 在 ExternalCallOrchestrator 中记录请求日志
  - 记录响应日志（状态码、耗时、Body 哈希）
  - 记录错误日志（异常堆栈）
  - 记录弹性事件日志（限流、熔断、重试）
  - 使用统一日志前缀 [EGRESS][LAYER]
  - 确保敏感信息脱敏
  - _需求: 需求 6（可观测性）_

- [ ] 9.2 实现指标收集
  - 创建 ExternalCallMetrics 组件
  - 记录调用耗时（Timer）
  - 记录限流次数（Counter）
  - 记录熔断次数（Counter）
  - 记录重试次数（Counter）
  - _需求: 需求 6（可观测性）_

- [ ]* 9.3 编写可观测性测试
  - 测试日志输出格式
  - 测试敏感信息脱敏
  - 测试指标收集
  - _需求: 需求 6（可观测性）_

- [ ] 10. 配置 Boot 层
  - 创建 Spring Boot 主类
  - 配置 application.yaml
  - 配置 egress-error-config.yaml
  - 配置依赖注入
  - _需求: 需求 2（弹性配置管理）、需求 7（错误处理与标准化）_


- [ ] 10.1 创建 Spring Boot 主类
  - 创建 EgressGatewayApplication 类
  - 添加 @SpringBootApplication 注解
  - 配置组件扫描
  - _需求: 需求 8（多类型外部服务支持）_

- [ ] 10.2 配置 application.yaml
  - 配置服务端口（8083）
  - 配置 Nacos 注册和配置中心
  - 配置错误码前缀（EGR）
  - 配置全局限流（1000 次/秒）
  - 配置弹性能力最大值和默认值
  - 配置日志级别
  - _需求: 需求 2（弹性配置管理）、需求 5（弹性能力实现）_

- [ ] 10.3 配置 egress-error-config.yaml
  - 配置 HTTP 对齐错误（0xxx 段）
  - 配置业务错误（1xxx+ 段）
  - _需求: 需求 7（错误处理与标准化）_

- [ ] 10.4 配置依赖注入
  - 确保所有 Port 接口有对应的实现 Bean
  - 配置条件装配（如 @ConditionalOnProperty）
  - _需求: 需求 3（配置源演进支持）_

- [ ] 11. 端到端集成测试
  - 编写完整的端到端测试场景
  - 测试多种外部服务类型调用
  - 测试弹性能力的实际行为
  - 测试配置覆盖逻辑
  - _需求: 需求 1-8（所有需求）_

- [ ]* 11.1 测试 PubMed 调用场景
  - 使用 WireMock 模拟 PubMed API
  - 测试成功调用
  - 测试限流场景（429）
  - 测试重试场景
  - 测试响应封装（提取 X-RateLimit-* 响应头）
  - _需求: 需求 1、4、5（调用、响应封装、弹性能力）_

- [ ]* 11.2 测试 OSS 调用场景
  - 使用 WireMock 模拟 OSS API
  - 测试文件上传（PUT）
  - 测试超时场景
  - 测试响应封装（提取 ETag）
  - _需求: 需求 1、4、5（调用、响应封装、弹性能力）_

- [ ]* 11.3 测试配置覆盖场景
  - 测试调用方传递配置（不超过最大值）
  - 测试调用方传递配置（超过最大值，使用系统最大值）
  - 测试调用方不传递配置（使用系统默认值）
  - _需求: 需求 2（弹性配置管理）_

- [ ]* 11.4 测试弹性能力组合场景
  - 测试限流 + 重试
  - 测试熔断 + 重试
  - 测试超时 + 重试
  - _需求: 需求 5（弹性能力实现）_

- [ ] 12. 文档和部署
  - 编写模块 README
  - 编写 API 文档
  - 创建 Dockerfile
  - 更新根 pom.xml（添加 patra-egress-gateway 模块）
  - _需求: 需求 8（多类型外部服务支持）_


- [ ] 12.1 编写模块 README
  - 概述模块职责
  - 说明核心功能
  - 提供使用示例（PubMed、OSS 等）
  - 说明配置项
  - _需求: 需求 8（多类型外部服务支持）_

- [ ] 12.2 编写 API 文档
  - 文档化 POST /api/egress/call 接口
  - 说明请求参数结构
  - 说明响应结构（ResponseEnvelope）
  - 说明错误码
  - _需求: 需求 1、4、7（调用、响应封装、错误处理）_

- [ ] 12.3 创建 Dockerfile
  - 基于 eclipse-temurin:21-jre-alpine
  - 配置端口 8083
  - 配置启动命令
  - _需求: 需求 8（多类型外部服务支持）_

- [ ] 12.4 更新根 pom.xml
  - 在 modules 中添加 patra-egress-gateway
  - 确保依赖版本统一
  - _需求: 需求 8（多类型外部服务支持）_

- [ ] 12.5 更新 patra-parent 依赖管理
  - 在 dependencyManagement 中添加 patra-egress-gateway-api
  - 添加 Resilience4j 版本管理
  - _需求: 需求 5（弹性能力实现）_

## 任务执行说明

1. **按顺序执行**：任务按照依赖关系排列，建议按顺序执行
2. **增量开发**：每个任务都是独立的、可测试的增量
3. **测试优先**：标记为 `*` 的任务是可选的单元测试任务，可根据需要跳过
4. **小步提交**：完成每个任务后及时提交代码
5. **文档同步**：修改代码时同步更新相关文档

## 技术栈

- Java 21
- Spring Boot 3.2.4
- Spring Cloud 2023.0.1
- Resilience4j（限流、重试、熔断）
- Spring RestClient（HTTP 客户端）
- Micrometer（指标收集）
- WireMock（测试外部服务）
- Lombok、MapStruct、Hutool

## 预期交付物

- 完整的 patra-egress-gateway 微服务
- 单元测试和集成测试
- 模块 README 和 API 文档
- Dockerfile 和部署配置
- 可运行的示例（PubMed、OSS 调用）
