# patra-egress-gateway 实现任务列表

## 任务概述

本任务列表将 patra-egress-gateway 的设计转化为可执行的编码任务。遵循测试驱动开发（TDD）原则，优先实现核心功能，确保每个步骤都是增量的、可测试的。

### 📝 最近更新（2025-10-06）

**新增任务**：
- **任务 3.4**：定义 Feign 客户端 API（⚠️ 重要 - 业务方需要通过此接口调用网关）
- **任务 7.7**：实现工具类（ResponseHashCalculator、HeaderWhitelistFilter）
- **任务 7.8**：实现 DTO/Command/Result 转换（明确转换策略）
- **任务 11.0**：准备测试基础设施（WireMock、测试配置、Mock 数据）

**澄清说明**：
- **任务 6.1**：明确超时控制通过 RestClient 配置实现，不使用 Resilience4j TimeLimiter
- **任务 8.3**：明确通常不需要自定义 ErrorMappingContributor（使用 patra-common 标准错误处理）

**快速检查清单更新**：
- API 层：新增 Feign 客户端 API 检查项
- Application 层：新增工具类和 DTO 转换检查项
- 测试：新增测试基础设施检查项

**常见问题新增**：
- Q7：业务方如何通过 Feign 调用网关？
- Q8：DTO 转换使用 MapStruct 还是手动转换？

## 任务列表

- [x] 1. 创建项目骨架和模块结构
  - 创建 patra-egress-gateway 聚合模块及其子模块（api/domain/app/infra/adapter/boot）
  - 配置 Maven 依赖管理和编译插件
  - 创建基础包结构
  - _需求: 需求 8（多类型外部服务支持）_

- [x] 2. 实现 Domain 层核心模型
  - 定义值对象（HttpRequest、HttpResponse、ResilienceConfig、ResponseEnvelope 等）
  - 实现 ResilienceConfigAggregate 聚合根
  - 定义领域端口接口（ConfigPort、HttpClientPort、RateLimiterPort、CircuitBreakerPort）
  - _需求: 需求 2（弹性配置管理）、需求 4（统一响应语义封装）_

- [x] 2.1 实现 HttpRequest 和 HttpResponse 值对象
  - 创建 HttpRequest record（url、method、headers、body）
  - 创建 HttpResponse record（statusCode、headers、body）
  - 添加 isSuccess() 方法判断 2xx 状态码
  - _需求: 需求 1（外部服务调用透传）_

- [x] 2.2 实现 ResilienceConfig 值对象
  - 创建 ResilienceConfig record（不包含 rateLimit）
  - 实现 validate() 校验方法
  - 实现 mergeWithMax() 合并方法
  - _需求: 需求 2（弹性配置管理）_

- [x]* 2.3 编写 ResilienceConfig 单元测试
  - 测试配置校验逻辑（负值、零值等边界情况）
  - 测试配置合并逻辑（不超过最大值）
  - _需求: 需求 2（弹性配置管理）_
  - **已完成**: 15个测试用例全部通过，覆盖校验、合并、不可变性等场景

- [x] 2.4 实现响应相关值对象
  - 创建 RateLimitStatus record（区分 Gateway 和外部服务限流）
  - 创建 ExternalRateLimitInfo record 并实现 fromHeaders() 方法
  - 创建 RetryAdvice record 并实现 fromResponse() 方法
  - 创建 ResponseEnvelope record
  - _需求: 需求 4（统一响应语义封装）、需求 5（弹性能力实现）_
  - **修复**: ExternalRateLimitInfo 和 RetryAdvice 的响应头大小写敏感问题
  - **增强**: RateLimitStatus 添加构造函数参数校验


- [x]* 2.5 编写响应值对象单元测试
  - 测试 ExternalRateLimitInfo.fromHeaders() 解析逻辑（包括大小写不敏感测试）
  - 测试 RetryAdvice.fromResponse() 生成逻辑（包括Retry-After头处理）
  - 测试 HttpResponse.isSuccess() 判断逻辑
  - 测试 RateLimitStatus 参数校验和 isLimited() 逻辑
  - _需求: 需求 4（统一响应语义封装）_
  - **已完成**: 34个测试用例全部通过，覆盖边界情况和异常场景

- [x] 2.6 实现 ResilienceConfigAggregate 聚合根
  - 实现 loadSystemConfig() 静态工厂方法
  - 实现 mergeWithCallerConfig() 方法
  - 实现 validate() 方法
  - _需求: 需求 2（弹性配置管理）_

- [x]* 2.7 编写 ResilienceConfigAggregate 单元测试
  - 测试配置加载逻辑
  - 测试配置合并逻辑（调用方配置超过最大值时使用最大值）
  - 测试配置校验逻辑
  - _需求: 需求 2（弹性配置管理）_
  - **已完成**: 10个测试用例全部通过，使用Mock ConfigPort实现

- [x] 2.8 定义领域端口接口
  - 定义 ConfigPort 接口（loadSystemConfig）
  - 定义 HttpClientPort 接口（call）
  - 定义 RateLimiterPort 接口（tryAcquire、getStatus）
  - 定义 CircuitBreakerPort 接口（executeWithCircuitBreaker、getState）
  - _需求: 需求 3（配置源演进支持）、需求 5（弹性能力实现）_

- [x] 3. 实现 API 层错误码和 DTOs
  - 定义 EgressErrors 错误码枚举
  - 定义领域异常类（EgressException、ConfigValidationException 等）
  - 定义外部 DTOs（ExternalCallRequest、ExternalCallResponse）
  - _需求: 需求 7（错误处理与标准化）_

- [x] 3.1 定义错误码枚举
  - 创建 EgressErrors 枚举（HTTP 对齐错误 0xxx 段）
  - 创建业务错误码（1xxx+ 段）
  - _需求: 需求 7（错误处理与标准化）_
  - **已完成**: 使用 HttpStdErrors.of("EGR") 模式，定义10个错误码

- [x] 3.2 实现领域异常类
  - 创建 EgressException 基类（继承 ApplicationException）
  - 创建具体异常类（ConfigValidationException、RateLimitExceededException 等）
  - _需求: 需求 7（错误处理与标准化）_
  - **已完成**: EgressException基类 + 4个具体异常类

- [x] 3.3 定义外部 DTOs
  - 创建 ExternalCallRequest DTO（映射到 ExternalCallCommand）
  - 创建 ExternalCallResponse DTO（映射到 ExternalCallResult）
  - 创建 ResilienceConfigDTO（映射到 ResilienceConfig）
  - _需求: 需求 1（外部服务调用透传）_
  - **已完成**: 7个DTO类（全部使用 record，带紧凑构造器保证不可变性）

- [x] 3.4 定义 Feign 客户端 API
  - 创建 EgressGatewayClient 接口（使用 @FeignClient 注解）
  - 定义 call() 方法（POST /api/egress/call）
  - 添加 Feign 相关依赖和配置
  - _需求: 需求 1（外部服务调用透传）_
  - **重要**: 业务方需要通过此接口调用网关
  - **已完成**: EgressGatewayClient接口 + pom.xml依赖配置

- [x] 4. 实现 Infrastructure 层配置加载
  - 实现 YamlConfigRepository（从 YAML 加载配置）
  - 创建配置属性类（EgressProperties）
  - 实现配置校验逻辑
  - _需求: 需求 2（弹性配置管理）、需求 3（配置源演进支持）_
  - **已完成**: YamlConfigRepository 实现 ConfigPort，从 YAML 加载系统默认和最大配置


- [x] 4.1 创建配置属性类
  - 创建 EgressProperties 类（映射 patra.egress 配置）
  - 创建 GlobalProperties 类（映射 global.rateLimit）
  - 创建 ResilienceProperties 类（映射 resilience.max 和 resilience.default）
  - _需求: 需求 2（弹性配置管理）_
  - **已完成**: 4个配置属性类（EgressProperties、GlobalProperties、ResilienceProperties、ResilienceConfigProperties）

- [x] 4.2 实现 YamlConfigRepository
  - 实现 ConfigPort 接口
  - 从 EgressProperties 加载系统配置
  - 提供 getMaxConfig() 方法
  - _需求: 需求 3（配置源演进支持）_
  - **已完成**: loadSystemDefaultConfig() 和 loadSystemMaxConfig() 方法，自动转换为 ResilienceConfig 值对象

- [x]* 4.3 编写配置加载集成测试
  - 测试从 YAML 加载配置
  - 测试配置校验失败场景
  - _需求: 需求 2（弹性配置管理）_
  - **已完成**: 7个集成测试用例全部通过，配置校验逻辑已在 Domain 层完整测试

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
  - 配置 RateLimiter、CircuitBreaker、Retry
  - **注**: 超时控制通过 RestClient 的超时配置实现（任务 5.1），不使用 Resilience4j TimeLimiter
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

- [ ] 7.7 实现工具类
  - 创建 ResponseHashCalculator（使用 SHA-256 计算哈希）
  - 创建 HeaderWhitelistFilter（根据白名单过滤响应头）
  - 实现单元测试
  - _需求: 需求 4（统一响应语义封装）、需求 6（可观测性）_

- [ ] 7.8 实现 DTO/Command/Result 转换
  - 决定转换策略（MapStruct 或手动转换）
  - 实现 DTO -> Command 转换（ExternalCallRequestDTO -> ExternalCallCommand）
  - 实现 Result -> DTO 转换（ExternalCallResult -> ExternalCallResponseDTO）
  - 如使用 MapStruct，配置 Mapper 接口和依赖
  - _需求: 需求 1（外部服务调用透传）_

- [ ]* 7.9 编写 ExternalCallOrchestrator 单元测试
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
  - 验证 egress-error-config.yaml 配置是否完整（任务 10.3）
  - 确保所有领域异常映射为 ProblemDetail
  - **注**: 如果使用 patra-common 的标准错误处理，通常不需要自定义 ErrorMappingContributor
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

- [x] 10. 配置 Boot 层
  - 创建 Spring Boot 主类
  - 配置 application.yaml
  - 配置 egress-error-config.yaml
  - 配置依赖注入
  - _需求: 需求 2（弹性配置管理）、需求 7（错误处理与标准化）_


- [x] 10.1 创建 Spring Boot 主类
  - 创建 EgressGatewayApplication 类
  - 添加 @SpringBootApplication 注解
  - 配置组件扫描
  - _需求: 需求 8（多类型外部服务支持）_
  - **已完成**: EgressGatewayApplication 主类，@SpringBootApplication + @ConfigurationPropertiesScan

- [x] 10.2 配置 application.yaml
  - 配置服务端口（8083）
  - 配置 Nacos 注册和配置中心
  - 配置错误码前缀（EGR）
  - 配置全局限流（1000 次/秒）
  - 配置弹性能力最大值和默认值
  - 配置日志级别
  - _需求: 需求 2（弹性配置管理）、需求 5（弹性能力实现）_
  - **已完成**: application.yaml 完整配置，包含 Nacos、弹性配置、Actuator、日志格式

- [x] 10.3 配置 egress-error-config.yaml
  - 配置 HTTP 对齐错误（0xxx 段）
  - 配置业务错误（1xxx+ 段）
  - _需求: 需求 7（错误处理与标准化）_
  - **已完成**: egress-error-config.yaml 包含错误处理配置和错误目录

- [x] 10.4 配置依赖注入
  - 确保所有 Port 接口有对应的实现 Bean
  - 配置条件装配（如 @ConditionalOnProperty）
  - _需求: 需求 3（配置源演进支持）_
  - **已完成**: 所有组件已有 Spring 注解（@Repository, @Component, @Service, @RestController），依赖注入自动完成

- [ ] 11. 端到端集成测试
  - 编写完整的端到端测试场景
  - 测试多种外部服务类型调用
  - 测试弹性能力的实际行为
  - 测试配置覆盖逻辑
  - _需求: 需求 1-8（所有需求）_

- [ ] 11.0 准备测试基础设施
  - 添加 WireMock 依赖和配置
  - 创建测试专用的 application-test.yaml
  - 准备 Mock 数据和响应模板
  - 配置测试基类和工具类
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

## 当前进度总结

### ✅ 已完成（Phase 1: Domain & API Layers）
- 项目骨架和模块结构已建立
- Domain 层核心模型完成（值对象、聚合根、端口接口）
- 配置管理逻辑已实现（ResilienceConfig、ResilienceConfigAggregate）
- 响应封装模型已定义（ResponseEnvelope、RetryAdvice、RateLimitStatus）
- API 层错误码和 DTOs 已定义（10个错误码，7个 DTOs，Feign 客户端 API）
- Infrastructure 层配置加载已实现（YamlConfigRepository，4个配置属性类，7个集成测试）

### 🔄 下一步建议（Phase 2: Infrastructure & App Layers）

**优先级 P0（必须完成才能运行）：**
1. ~~实现 API 层错误码和 DTOs（任务 3，包括 Feign 客户端 API）~~ ✅ 已完成
2. ~~实现 Infrastructure 层配置加载（任务 4）~~ ✅ 已完成
3. 实现 Infrastructure 层 HTTP 客户端（任务 5）
4. 实现 Application 层 Use Case（任务 7，包括工具类和 DTO 转换）
5. 实现 Adapter 层 REST Controller（任务 8）
6. 配置 Boot 层（任务 10）

**优先级 P1（增强稳定性）：**
1. 实现 Infrastructure 层弹性能力（任务 6）
2. 实现可观测性（任务 9）
3. ~~编写关键单元测试（2.3, 2.5, 2.7, 7.7）~~ - Domain 层测试已完成（59个测试用例）

**优先级 P2（完善系统）：**
1. 端到端集成测试（任务 11）
2. 文档和部署（任务 12）

### 📋 关键实现注意事项

#### 1. ConfigPort 接口设计
- **当前实现**: ConfigPort 定义了 `loadSystemDefaultConfig()` 和 `loadSystemMaxConfig()` 两个方法
- **原因**: 需要区分系统默认值和最大值，支持业务方配置覆盖
- **影响**: YamlConfigRepository 实现时需要从两个不同的配置节加载

#### 2. 限流设计考虑
- **全局限流**: 使用 Resilience4j RateLimiter 实现网关级别的全局限流（1000 次/秒）
- **业务方限流**: 业务方可以传递更严格的限流配置，但不能超过全局最大值
- **限流状态**: RateLimitStatus 区分网关限流状态和外部服务返回的限流信息

#### 3. 响应封装策略
- **Body 哈希**: 使用 SHA-256 计算响应 Body 哈希，用于去重和取证
- **响应头白名单**: 默认白名单包括 Content-Type、X-RateLimit-*、Retry-After 等
- **敏感信息脱敏**: 日志中不记录 Authorization、API-Key 等敏感请求头

#### 4. 重试策略
- **可重试状态码**: 429、408、5xx
- **退避策略**: 指数退避，从 retryBackoff 开始，每次翻倍
- **Retry-After 支持**: 优先使用外部服务返回的 Retry-After 头

#### 5. 测试策略
- **单元测试**: 优先测试 Domain 层的业务逻辑（配置合并、重试建议生成等）
- **集成测试**: 使用 WireMock 模拟外部服务，测试完整调用流程
- **性能测试**: 验证限流、熔断等弹性能力的实际效果

### ⚠️ 技术风险与难点

1. **Resilience4j 配置复杂性**
   - Resilience4j 的 RateLimiter、CircuitBreaker、Retry 需要协调工作
   - 建议先实现基础功能，再逐步增强弹性能力

2. **配置源演进**
   - 当前使用 YAML 配置，后期需支持数据库动态配置
   - 确保 ConfigPort 接口设计足够灵活，便于切换实现

3. **响应体大小限制**
   - 需要考虑大型响应体（如 PDF 文件）的内存占用
   - 建议在 RestClient 配置中设置合理的 maxInMemorySize

4. **并发限流精度**
   - Resilience4j RateLimiter 在高并发下的精度可能不如 Redis
   - 如果需要分布式限流，后期可以考虑引入 Redis

5. **熔断器状态管理**
   - 熔断器的状态需要持久化（或至少在内存中维护）
   - 考虑使用 Resilience4j 的事件监听机制记录熔断事件

### 📚 参考文档

- [Resilience4j 官方文档](https://resilience4j.readme.io/)
- [Spring RestClient 文档](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
- [RFC 7807 Problem Details](https://datatracker.ietf.org/doc/html/rfc7807)
- [HTTP 状态码规范](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status)

## 任务执行说明

1. **按顺序执行**：任务按照依赖关系排列，建议按顺序执行
2. **增量开发**：每个任务都是独立的、可测试的增量
3. **测试优先**：标记为 `*` 的任务是可选的单元测试任务，可根据需要跳过
4. **小步提交**：完成每个任务后及时提交代码
5. **文档同步**：修改代码时同步更新相关文档
6. **优先级驱动**：优先完成 P0 任务，确保最小可运行版本（MVP）

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

---

## 实施路线图（详细版）

### Phase 1: 最小可运行版本（MVP）

**目标**: 完成一个可以正常调用外部服务的基础版本，不包含弹性能力

#### 步骤 1: API 层基础（任务 3）
**时间估算**: 2-3 小时

1. 定义错误码枚举 `EgressErrors`
   - HTTP 对齐错误（0400、0422、0429、0500、0503、0504）
   - 业务错误（1001-1004）

2. 创建领域异常类
   - `EgressException` 基类
   - `ConfigValidationException`
   - `RateLimitExceededException`
   - `CircuitBreakerOpenException`
   - `ExternalCallTimeoutException`

3. 定义外部 DTOs
   - `ExternalCallRequestDTO`（包含 url、method、headers、body、resilienceConfig）
   - `ExternalCallResponseDTO`（包含 ResponseEnvelope）
   - `ResilienceConfigDTO`（映射到 ResilienceConfig）

4. 定义 Feign 客户端 API
   - 创建 `EgressGatewayClient` 接口
   - 使用 `@FeignClient` 注解
   - 定义 `call()` 方法（POST /api/egress/call）

**验收标准**:
- ✅ 所有错误码定义完整
- ✅ 异常类继承关系正确
- ✅ DTOs 可以正确序列化/反序列化（JSON）
- ✅ Feign 客户端 API 定义完整，业务方可以通过 Feign 调用网关

#### 步骤 2: Infrastructure 层配置加载（任务 4）
**时间估算**: 3-4 小时

1. 创建配置属性类
   ```java
   @ConfigurationProperties(prefix = "patra.egress")
   public class EgressProperties {
       private GlobalProperties global;
       private ResilienceProperties resilience;
   }
   ```

2. 实现 `YamlConfigRepository`
   - 从 `EgressProperties` 加载系统默认配置
   - 从 `EgressProperties` 加载系统最大配置
   - 实现配置校验逻辑

3. 配置 `application.yaml`
   ```yaml
   patra:
     egress:
       global:
         rateLimit: 1000
       resilience:
         max:
           timeout: 60s
           maxRetries: 5
           # ...
         default:
           timeout: 30s
           maxRetries: 3
           # ...
   ```

**验收标准**:
- ✅ 配置可以正确加载
- ✅ 配置校验失败时抛出异常
- ✅ 集成测试通过（加载配置并验证）

#### 步骤 3: Infrastructure 层 HTTP 客户端（任务 5）
**时间估算**: 4-5 小时

1. 配置 Spring RestClient
   ```java
   @Bean
   public RestClient restClient(RestClient.Builder builder) {
       return builder
           .requestFactory(new JdkClientHttpRequestFactory())
           .build();
   }
   ```

2. 实现 `HttpClientAdapter`
   - 支持 GET、POST、PUT、DELETE 等方法
   - 应用超时配置
   - 正确处理请求头和请求体
   - 返回完整的响应（状态码、头、体）

3. 实现敏感信息脱敏工具
   ```java
   public class SensitiveHeaderMasker {
       private static final Set<String> SENSITIVE_HEADERS = Set.of(
           "Authorization", "API-Key", "X-API-Key", "Cookie", "Set-Cookie"
       );

       public static Map<String, String> mask(Map<String, String> headers) {
           // ...
       }
   }
   ```

4. 编写集成测试（使用 WireMock）
   - 测试 GET 请求
   - 测试 POST 请求（带 Body）
   - 测试超时场景
   - 测试敏感信息脱敏

**验收标准**:
- ✅ HTTP 客户端可以成功调用 WireMock 模拟的服务
- ✅ 超时配置生效
- ✅ 敏感信息正确脱敏
- ✅ 集成测试通过

#### 步骤 4: Application 层 Use Case（任务 7 - 简化版）
**时间估算**: 4-5 小时

1. 定义 Command 和 Result
   ```java
   public record ExternalCallCommand(
       HttpRequest request,
       ResilienceConfig callerConfig
   ) {}

   public record ExternalCallResult(
       ResponseEnvelope envelope,
       Duration duration,
       int retryCount,
       String traceId
   ) {}
   ```

2. 实现 `ExternalCallOrchestrator`（简化版，暂不包含弹性能力）
   ```java
   public ExternalCallResult execute(ExternalCallCommand command) {
       // 1. 加载系统配置
       // 2. 合并配置
       // 3. 调用 HTTP 客户端（暂不应用限流、重试、熔断）
       // 4. 封装响应
       // 5. 记录日志
       // 6. 返回结果
   }
   ```

3. 实现 `ResponseEnvelopeBuilder`
   - 判断成功/失败（2xx = 成功）
   - 提取外部服务限流信息（X-RateLimit-*）
   - 生成重试建议（RetryAdvice）
   - 计算响应 Body 哈希（SHA-256）
   - 过滤响应头白名单

4. 实现工具类
   - `ResponseHashCalculator`（使用 SHA-256 计算哈希）
   - `HeaderWhitelistFilter`（根据白名单过滤响应头）

5. 实现 DTO 转换
   - 决定转换策略（MapStruct 或手动转换）
   - 实现 DTO -> Command 转换
   - 实现 Result -> DTO 转换

**验收标准**:
- ✅ 配置合并逻辑正确
- ✅ 响应封装完整
- ✅ 工具类实现完整并通过单元测试
- ✅ DTO 转换逻辑正确
- ✅ 单元测试通过（模拟 ConfigPort 和 HttpClientPort）

#### 步骤 5: Adapter 层 REST Controller（任务 8）
**时间估算**: 3-4 小时

1. 创建 `ExternalCallController`
   ```java
   @RestController
   @RequestMapping("/api/egress")
   public class ExternalCallController {

       @PostMapping("/call")
       public ResponseEntity<ExternalCallResponseDTO> call(
           @Valid @RequestBody ExternalCallRequestDTO request
       ) {
           // 转换 DTO -> Command
           // 调用 Use Case
           // 转换 Result -> DTO
           // 返回响应
       }
   }
   ```

2. 实现请求参数校验
   - URL 不为空
   - HTTP 方法不为空
   - ResilienceConfig 有效性

3. 实现异常映射（使用 patra-common 的 ProblemDetail）

**验收标准**:
- ✅ REST API 可以正常调用
- ✅ 参数校验失败返回 422
- ✅ 异常正确映射为 ProblemDetail
- ✅ 集成测试通过

#### 步骤 6: Boot 层配置（任务 10）
**时间估算**: 2-3 小时

1. 创建 Spring Boot 主类
   ```java
   @SpringBootApplication
   public class EgressGatewayApplication {
       public static void main(String[] args) {
           SpringApplication.run(EgressGatewayApplication.class, args);
       }
   }
   ```

2. 配置 `application.yaml`
3. 配置 `egress-error-config.yaml`
4. 确保依赖注入正确

**验收标准**:
- ✅ 应用可以正常启动
- ✅ 可以通过 REST API 调用外部服务
- ✅ 错误码配置生效

**MVP 完成标志**:
- ✅ 可以通过 REST API 调用外部服务（如 PubMed）
- ✅ 响应封装为统一格式（ResponseEnvelope）
- ✅ 配置管理正常工作

---

### Phase 2: 弹性能力增强

**目标**: 添加限流、重试、熔断、超时等弹性能力

#### 步骤 7: 实现限流（任务 6.1-6.3）
**时间估算**: 4-5 小时

1. 配置 Resilience4j RateLimiter
2. 实现 `RateLimiterAdapter`
3. 在 `ExternalCallOrchestrator` 中应用限流
4. 编写单元测试

**验收标准**:
- ✅ 限流生效（达到阈值时返回 429）
- ✅ 限流状态正确记录
- ✅ 单元测试和集成测试通过

#### 步骤 8: 实现重试（任务 6.6-6.7）
**时间估算**: 4-5 小时

1. 配置 Resilience4j Retry
2. 实现 `RetryHandler`
3. 在 `ExternalCallOrchestrator` 中应用重试
4. 编写单元测试

**验收标准**:
- ✅ 可重试状态码触发重试（429、5xx）
- ✅ 指数退避策略生效
- ✅ Retry-After 头优先使用
- ✅ 单元测试和集成测试通过

#### 步骤 9: 实现熔断（任务 6.4-6.5）
**时间估算**: 4-5 小时

1. 配置 Resilience4j CircuitBreaker
2. 实现 `CircuitBreakerAdapter`
3. 在 `ExternalCallOrchestrator` 中应用熔断
4. 编写单元测试

**验收标准**:
- ✅ 失败率超过阈值时触发熔断（返回 503）
- ✅ 熔断器状态转换正确（CLOSED -> OPEN -> HALF_OPEN）
- ✅ 单元测试和集成测试通过

---

### Phase 3: 可观测性与测试完善

#### 步骤 10: 实现可观测性（任务 9）
**时间估算**: 3-4 小时

1. 在各层添加日志记录（使用 `@Slf4j`）
2. 实现 `ExternalCallMetrics` 指标收集
3. 确保敏感信息脱敏

**验收标准**:
- ✅ 日志格式统一（`[EGRESS][LAYER]`）
- ✅ 关键操作有日志记录（请求、响应、错误、弹性事件）
- ✅ 指标正确收集（调用耗时、限流次数、熔断次数）
- ✅ 敏感信息正确脱敏

#### 步骤 11: 端到端测试（任务 11）
**时间估算**: 6-8 小时

1. 编写 PubMed 调用场景测试
2. 编写 OSS 调用场景测试
3. 编写配置覆盖场景测试
4. 编写弹性能力组合场景测试

**验收标准**:
- ✅ 所有端到端测试通过
- ✅ 覆盖主要使用场景

#### 步骤 12: 文档与部署（任务 12）
**时间估算**: 4-5 小时

1. 编写模块 README
2. 编写 API 文档
3. 创建 Dockerfile
4. 更新根 pom.xml 和 patra-parent

**验收标准**:
- ✅ 文档完整清晰
- ✅ Docker 镜像可以正常构建和运行

---

## 快速检查清单

### Domain 层检查
- [x] HttpRequest 和 HttpResponse 值对象定义完整
- [x] ResilienceConfig 值对象实现 validate() 和 mergeWithMax()
- [x] RateLimitStatus、RetryAdvice、ResponseEnvelope 定义完整
- [x] ResilienceConfigAggregate 实现配置加载和合并
- [x] 所有端口接口定义清晰

### API 层检查
- [x] 错误码枚举定义完整（10个错误码：HTTP 对齐 + 业务错误）
- [x] 领域异常类继承关系正确（EgressException 基类 + 4个具体异常）
- [x] DTOs 可以正确序列化/反序列化（7个 DTOs，全部使用 record）
- [x] Feign 客户端 API 定义完整（供业务方调用）

### Infrastructure 层检查
- [x] 配置属性类正确映射 YAML（EgressProperties、GlobalProperties、ResilienceProperties、ResilienceConfigProperties）
- [x] YamlConfigRepository 实现 ConfigPort（loadSystemDefaultConfig、loadSystemMaxConfig）
- [ ] HttpClientAdapter 支持各种 HTTP 方法
- [ ] Resilience4j 组件正确配置和实现

### Application 层检查
- [ ] ExternalCallCommand 和 ExternalCallResult 定义完整
- [ ] ExternalCallOrchestrator 流程编排正确
- [ ] ResponseEnvelopeBuilder 响应封装完整
- [ ] 工具类实现完整（ResponseHashCalculator、HeaderWhitelistFilter）
- [ ] DTO/Command/Result 转换逻辑正确

### Adapter 层检查
- [ ] ExternalCallController REST API 定义正确
- [ ] 请求参数校验完整
- [ ] 异常映射为 ProblemDetail

### Boot 层检查
- [ ] Spring Boot 主类配置正确
- [ ] application.yaml 配置完整
- [ ] egress-error-config.yaml 错误码配置完整
- [ ] 依赖注入正确

### 测试检查
- [ ] 测试基础设施准备完成（WireMock、测试配置、Mock 数据）
- [x] Domain 层单元测试覆盖核心逻辑（59个测试用例全部通过）
- [x] Infrastructure 层配置加载集成测试通过（7个测试用例）
- [ ] Infrastructure 层 HTTP 客户端集成测试通过（WireMock）
- [ ] Application 层单元测试通过（Mock 端口）
- [ ] Adapter 层集成测试通过
- [ ] 端到端测试覆盖主要场景

---

## 常见问题与解决方案

### Q1: ResilienceConfig 中的 rateLimit 是全局限流还是单次请求限流？
**A**: 全局限流。网关级别的限流（如 1000 次/秒）适用于所有请求。业务方可以传递更严格的限流配置（如 10 次/秒），但不能超过全局最大值。

### Q2: 如何处理大型响应体（如 PDF 文件）？
**A**:
1. 在 RestClient 配置中设置 `maxInMemorySize`
2. 对于特别大的响应体，考虑只计算哈希而不存储完整内容
3. 后期可以考虑流式处理

### Q3: 限流是基于什么维度的？
**A**: 当前设计是全局限流（所有请求共享一个限流器）。如果需要按 URL 或服务类型限流，可以在 RateLimiterAdapter 中使用不同的限流器实例。

### Q4: 重试逻辑如何与熔断器协调？
**A**:
1. 先应用熔断器：如果熔断器打开，直接返回 503，不进入重试逻辑
2. 在熔断器关闭或半开状态下，才应用重试逻辑
3. 重试次数计入熔断器的失败次数

### Q5: 如何确保配置热更新（数据库模式）？
**A**:
1. 当前阶段使用 YAML 静态配置
2. 后期切换到数据库时，可以使用 Spring Cloud Config 或 Nacos 实现热更新
3. ConfigPort 接口设计已经考虑了配置源的可切换性

### Q6: 响应头白名单如何配置？
**A**:
1. 系统默认白名单在 `application.yaml` 中配置
2. 业务方可以在请求中传递自定义白名单（通过 ResilienceConfig）
3. 如果业务方未传递，使用系统默认白名单

### Q7: 业务方如何通过 Feign 调用网关？
**A**:
1. 网关在 `patra-egress-gateway-api` 模块中提供 `EgressGatewayClient` Feign 接口
2. 业务方在 `pom.xml` 中添加依赖：
   ```xml
   <dependency>
       <groupId>com.papertrace</groupId>
       <artifactId>patra-egress-gateway-api</artifactId>
   </dependency>
   ```
3. 启用 Feign 客户端：`@EnableFeignClients(clients = EgressGatewayClient.class)`
4. 注入并使用：
   ```java
   @Autowired
   private EgressGatewayClient egressGatewayClient;

   ExternalCallResponseDTO response = egressGatewayClient.call(request);
   ```

### Q8: DTO 转换使用 MapStruct 还是手动转换？
**A**:
1. **推荐使用手动转换**：DTO -> Command/Result 的转换逻辑较简单，手动转换更直观
2. 如果转换逻辑复杂（多层嵌套、复杂映射），可以考虑使用 MapStruct
3. 在任务 7.8 中明确决定转换策略

---

## 下一步行动建议

基于当前进度（已完成 Phase 1: Domain Layer），建议按以下顺序进行：

1. **立即开始**: 实施 Phase 1 的 MVP（步骤 1-6）
   - 预计时间：18-24 小时
   - 目标：完成一个可以正常调用外部服务的基础版本

2. **短期目标**: 实施 Phase 2 的弹性能力（步骤 7-9）
   - 预计时间：12-15 小时
   - 目标：添加限流、重试、熔断等弹性能力

3. **中期目标**: 实施 Phase 3 的可观测性与测试（步骤 10-12）
   - 预计时间：13-17 小时
   - 目标：完善日志、指标、测试和文档

**总预计时间**: 43-56 小时（约 1-1.5 周全职工作）
