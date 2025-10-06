# patra-spring-boot-starter-provenance 需求文档

## 简介

patra-spring-boot-starter-provenance 是一个 Spring Boot Starter，用于封装文献数据源 API 的参数模型和响应模型，通过南向网关（patra-egress-gateway）调用外部数据源。它为业务方提供类型安全、易于使用的客户端接口，简化文献数据采集流程。

该 Starter 的核心职责是提供强类型的 API 封装，而不涉及参数转换（Expr → 数据源参数）、业务逻辑或过度抽象设计。所有外部服务调用都通过南向网关进行，确保统一的弹性能力和可观测性。

**包名**：`com.patra.starter.provenance`

## 需求

### 需求 1：数据源 API 封装

**用户故事：** 作为业务方开发者，我希望使用强类型的客户端接口调用文献数据源 API，以便减少参数错误和提高代码可读性。

#### 验证标准

1. WHEN 业务方需要调用 PubMed esearch API THEN Starter SHALL 提供 PubMedClient.esearch() 方法
2. WHEN 业务方需要调用 PubMed efetch API THEN Starter SHALL 提供 PubMedClient.efetch() 方法
3. WHEN 业务方需要调用 EPMC search API THEN Starter SHALL 提供 EPMCClient.search() 方法
4. WHEN 业务方调用客户端方法 THEN Starter SHALL 接收强类型的 Request 对象（不使用 Map）
5. WHEN 业务方调用客户端方法 THEN Starter SHALL 返回强类型的 Response 对象（不返回原始字符串）
6. WHEN 数据源返回 XML 格式响应 THEN Starter SHALL 自动转换为 JSON 对象
7. WHEN 业务方调用客户端方法 THEN Starter SHALL 保留数据源响应的所有字段和嵌套结构

### 需求 2：参数完整性

**用户故事：** 作为业务方开发者，我希望 Starter 提供所有 API 参数的定义，即使某些参数是可选的，以便我能够根据需要灵活配置请求。

#### 验证标准

1. WHEN Starter 封装数据源 API THEN Starter SHALL 定义所有 API 参数（包括必需和可选参数）
2. WHEN 业务方构建请求对象 THEN Starter SHALL 允许业务方不传递可选参数
3. WHEN 业务方构建请求对象 THEN Starter SHALL 使用强类型定义参数（record 或 class）
4. WHEN 业务方传递可选参数 THEN Starter SHALL 在构建 URL 时包含这些参数
5. IF 业务方未传递可选参数 THEN Starter SHALL 在构建 URL 时忽略这些参数

### 需求 3：响应完整性

**用户故事：** 作为业务方开发者，我希望 Starter 返回的响应包含数据源的所有字段，即使我暂时不使用某些字段，以便未来扩展功能时无需修改 Starter。

#### 验证标准

1. WHEN Starter 接收到数据源响应 THEN Starter SHALL 保留所有字段和嵌套结构
2. WHEN 数据源返回 XML 格式 THEN Starter SHALL 自动转换为 JSON 对象
3. WHEN Starter 封装响应 THEN Starter SHALL 使用强类型 POJO（不返回原始字符串）
4. WHEN 数据源响应包含嵌套结构 THEN Starter SHALL 正确映射为嵌套对象
5. WHEN 数据源响应包含数组 THEN Starter SHALL 正确映射为 List 或数组

### 需求 4：网关调用封装

**用户故事：** 作为业务方开发者，我希望 Starter 内部自动通过南向网关调用外部服务，以便我无需关心 HTTP 请求细节和弹性能力配置。

#### 验证标准

1. WHEN 业务方调用客户端方法 THEN Starter SHALL 内部使用 EgressGatewayClient 调用南向网关
2. WHEN Starter 构建网关请求 THEN Starter SHALL 根据请求参数构建完整的 URL
3. WHEN Starter 构建网关请求 THEN Starter SHALL 根据配置构建 HTTP Headers（如 User-Agent、API-Key）
4. WHEN Starter 调用网关 THEN Starter SHALL 传递完整的 HTTP 请求信息（URL、方法、Headers、Body）
5. WHEN 网关返回响应 THEN Starter SHALL 解析响应并返回结构化对象
6. WHEN 网关调用失败 THEN Starter SHALL 抛出明确的异常（如 ProvenanceClientException）

### 需求 5：配置管理

**用户故事：** 作为业务方开发者，我希望 Starter 支持灵活的配置管理，允许我在调用时传递配置、从数据库加载配置或使用本地配置文件，以便适应不同的使用场景。

#### 验证标准

1. WHEN 业务方调用客户端方法 AND 传递了配置对象 THEN Starter SHALL 使用业务方传递的配置（最高优先级）
2. WHEN 业务方调用客户端方法 AND 未传递配置对象 THEN Starter SHALL 尝试从数据库加载配置（通过 patra-registry）
3. IF 数据库中没有配置 THEN Starter SHALL 使用本地配置文件（Nacos 或 application.yml）
4. WHEN Starter 加载配置 THEN Starter SHALL 每次 API 调用时动态加载（不做缓存）
5. WHEN 配置来源为 Nacos THEN Starter SHALL 支持配置动态刷新
6. WHEN Starter 定义配置结构 THEN Starter SHALL 参考 patra-registry-api 中的 ProvenanceConfigResp 结构
7. WHEN Starter 加载配置 THEN Starter SHALL 包含以下配置项：
   - baseUrl（数据源基础 URL）
   - http 配置（超时、重试等）
   - 分页配置（pageSize、maxPages）
   - 窗口配置（时间窗口）
   - 批处理配置（batchSize）
   - 重试配置（maxRetries、retryBackoff）
   - 限流配置（rateLimit）

### 需求 6：客户端设计

**用户故事：** 作为业务方开发者，我希望每个数据源有独立的客户端接口，以便我能够清晰地区分不同数据源的 API，而不是使用统一的抽象接口。

#### 验证标准

1. WHEN Starter 提供客户端接口 THEN Starter SHALL 为每个数据源创建独立的 Client（不做统一抽象）
2. WHEN 业务方使用 PubMed 数据源 THEN Starter SHALL 提供 PubMedClient 接口
3. WHEN 业务方使用 EPMC 数据源 THEN Starter SHALL 提供 EPMCClient 接口
4. WHEN 客户端方法接收参数 THEN Starter SHALL 接收对应的 Request 对象和可选的 Config 对象
5. WHEN 需要新增数据源 THEN Starter SHALL 允许创建新的独立 Client（无需修改现有 Client）

### 需求 7：Starter 自动配置

**用户故事：** 作为业务方开发者，我希望 Starter 能够自动配置客户端 Bean，以便我只需添加依赖即可使用，无需手动配置。

#### 验证标准

1. WHEN 业务方添加 Starter 依赖 THEN Starter SHALL 自动配置 PubMedClient 和 EPMCClient Bean
2. WHEN Starter 自动配置 THEN Starter SHALL 使用 @AutoConfiguration 注解
3. WHEN Starter 自动配置 THEN Starter SHALL 使用 @EnableConfigurationProperties 加载配置属性
4. WHEN Starter 自动配置 THEN Starter SHALL 使用 @ConditionalOnMissingBean 提供默认实现
5. WHEN Starter 自动配置 THEN Starter SHALL 使用 @ConditionalOnBean(EgressGatewayClient.class) 检查网关依赖
6. WHEN Starter 自动配置 THEN Starter SHALL 使用 @ConditionalOnProperty 支持开关配置
7. WHEN Starter 自动配置 THEN Starter SHALL 在 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 中注册自动配置类

### 需求 8：可观测性

**用户故事：** 作为运维工程师，我希望 Starter 能够记录关键操作的日志，并支持分布式追踪，以便进行问题排查和性能分析。

#### 验证标准

1. WHEN Starter 调用 API THEN Starter SHALL 记录 API 调用开始日志（INFO 级别）
2. WHEN Starter 调用 API THEN Starter SHALL 记录 API 调用结束日志（INFO 级别，包含耗时）
3. WHEN Starter 调用 API 失败 THEN Starter SHALL 记录错误日志（ERROR 级别，包含异常堆栈）
4. WHEN Starter 记录日志 THEN Starter SHALL 包含 traceId（依赖 SkyWalking Agent 自动注入）
5. WHEN Starter 记录日志 THEN Starter SHALL 使用统一的日志前缀格式 `[PROVENANCE][LAYER]`
6. WHEN Starter 记录详细参数 THEN Starter SHALL 使用 DEBUG 级别

### 需求 9：分页与批量处理

**用户故事：** 作为业务方开发者，我希望 Starter 只提供基础的 API 方法，而不自动处理分页和批量逻辑，以便我能够根据具体场景灵活控制分页和批处理。

#### 验证标准

1. WHEN Starter 提供 API 方法 THEN Starter SHALL 只提供单次调用的基础方法（不自动分页）
2. WHEN 业务方需要分页 THEN 业务方 SHALL 自己处理分页逻辑（调用多次 API）
3. WHEN 配置中包含分页配置 THEN Starter SHALL 将配置传递给网关（由业务方根据配置处理）
4. WHEN 配置中包含批处理配置 THEN Starter SHALL 将配置传递给网关（由业务方根据配置处理）
5. WHEN Starter 提供 API 方法 THEN Starter SHALL NOT 提供自动分批功能

### 需求 10：职责边界约束

**用户故事：** 作为系统架构师，我希望 Starter 严格遵守职责边界，只负责 API 封装和网关调用，不涉及参数转换、业务逻辑或过度抽象，以便保持系统的清晰分层。

#### 验证标准

1. WHEN Starter 实现功能 THEN Starter SHALL NOT 进行参数转换（如 Expr → 数据源参数）
2. WHEN Starter 实现功能 THEN Starter SHALL NOT 包含业务逻辑（如文献去重、数据校验）
3. WHEN Starter 实现功能 THEN Starter SHALL NOT 进行过度抽象设计（如统一的数据源接口）
4. WHEN Starter 实现功能 THEN Starter SHALL 只负责 API 封装和网关调用
5. WHEN 业务方需要参数转换 THEN 业务方 SHALL 自己处理转换逻辑

### 需求 11：首期范围约束

**用户故事：** 作为项目经理，我希望首期只实现核心功能，将错误处理、认证机制、性能优化等功能留到后续迭代，以便快速交付可用版本。

#### 验证标准

1. WHEN 首期实现 THEN Starter SHALL 支持 PubMed esearch 和 efetch API
2. WHEN 首期实现 THEN Starter SHALL 支持 EPMC search API
3. WHEN 首期实现 THEN Starter SHALL 提供基础的异常处理（抛出 ProvenanceClientException）
4. WHEN 首期实现 THEN Starter SHALL NOT 实现自定义错误处理与异常体系设计
5. WHEN 首期实现 THEN Starter SHALL NOT 实现 API Key 管理与认证机制
6. WHEN 首期实现 THEN Starter SHALL NOT 实现性能优化（并发控制、连接池）
7. WHEN 首期实现 THEN Starter SHALL NOT 实现响应缓存机制
8. WHEN 首期实现 THEN Starter SHALL NOT 实现自动重试与降级策略（依赖网关提供）
9. WHEN 首期实现 THEN Starter SHALL NOT 包含单元测试与集成测试（后续补充）

### 需求 12：性能指标记录

**用户故事：** 作为运维工程师，我希望 Starter 能够记录 API 调用的性能指标（如耗时、成功率、异常统计），以便监控系统性能和快速定位问题。

#### 验证标准

1. WHEN Starter 调用 API THEN Starter SHALL 记录 API 调用耗时（毫秒）
2. WHEN Starter 调用 API THEN Starter SHALL 记录 API 调用成功次数
3. WHEN Starter 调用 API 失败 THEN Starter SHALL 记录 API 调用失败次数
4. WHEN Starter 记录指标 THEN Starter SHALL 使用 Micrometer 指标库
5. WHEN Starter 记录指标 THEN Starter SHALL 使用统一的指标命名规范：
   - 指标前缀：`provenance.client`
   - 指标维度：provenanceCode（数据源代码，如 PUBMED/EPMC）、apiName（API 名称，如 esearch/efetch）
6. WHEN Starter 记录指标 THEN Starter SHALL 提供以下指标：
   - `provenance.client.api.duration`：API 调用耗时（Timer）
   - `provenance.client.api.success`：API 调用成功次数（Counter）
   - `provenance.client.api.failure`：API 调用失败次数（Counter）
7. WHEN 指标记录失败 THEN Starter SHALL NOT 影响正常的 API 调用流程
