patra-spring-boot-starter-provenance-client 需求清单

1. 模块定位与职责

- 模块名称：patra-spring-boot-starter-provenance-client（遵循
  starter 命名规范）
- 模块形式：Spring Boot Starter
- 核心职责：封装文献数据源 API
  的参数模型和响应模型，通过南向网关调用外部数据源
- 明确不做的事情：
    - 不做参数转换（Expr → 数据源参数由调用方自己处理）
    - 不做业务逻辑
    - 不做过度抽象设计

2. 数据源与 API 范围

- 首期支持数据源与 API：
    - PubMed：
        - esearch：搜索文献，返回 ID 列表
        - efetch：根据 ID 获取文献详细信息
    - EPMC：
        - search：搜索文献
- 说明：每个数据源的 API 定义都不相同，不做统一抽象
- API 规格基准：暂以现有知识为准（参考 PubMed/EPMC 官方 API
  文档）

3. 参数封装需求

- 封装粒度：所有 API 参数都需要封装（完整覆盖数据源 API
  的参数列表）
- 参数可选性：除必需参数外，其他参数设计为可选
- 参数类型：使用强类型定义（record 或
  class），不使用弱类型（Map）
- 设计原则：调用方可以不传某些可选参数，但模块必须提供所有参数的
  定义

4. 响应封装需求

- 保留完整性：必须保留数据源响应的所有字段和嵌套结构
- 格式转换：若数据源返回 XML，模块内部需自动转换为 JSON 对象
- 类型映射：响应需映射为强类型 POJO，不返回原始字符串
- 字段完整性原则：调用方可以不用某些字段，但模块不能缺失任何字段

5. 网关调用需求

- 调用方式：内部封装 EgressGatewayClient 调用
- 封装职责：
    - 根据请求参数构建完整 URL
    - 根据配置构建 HTTP Headers
    - 通过南向网关（patra-egress-gateway）发送请求
    - 解析响应并返回结构化对象

6. 配置管理需求

- 配置结构：参考 patra-registry-api 中的 ProvenanceConfigResp
    - 包含：baseUrl、http
      配置、分页配置、窗口配置、批处理配置、重试配置、限流配置等
- 配置来源优先级（从高到低）：
  a. 调用时传入的配置对象（最高优先级）
  b. 从数据库动态加载（patra-registry）
  c. 从本地配置文件加载（Nacos 或 application.yml）
- 配置加载时机：每次 API 调用时动态加载，不做缓存
- 配置刷新：支持 Nacos 动态配置刷新

7. Client 设计需求

- 设计原则：每个数据源一个独立的 Client（不做统一抽象接口）
- PubMedClient：包含 esearch()、efetch() 方法
- EPMCClient：包含 search() 方法
- 方法签名：每个方法接收对应的 Request 对象和可选的 Config 对象
- 扩展性：未来新增数据源时，创建新的独立 Client 即可

8. Starter 设计需求（参考 patra-spring-boot-starter-expr）

- 自动配置类（ProvenanceClientAutoConfiguration）：
    - 使用 @AutoConfiguration
    - 使用
      @EnableConfigurationProperties(ProvenanceClientProperties.class)
    - 使用 @Bean 定义 PubMedClient、EPMCClient
    - 使用 @ConditionalOnMissingBean 提供默认实现
    - 使用 @ConditionalOnBean(EgressGatewayClient.class)
      检查网关依赖
    - 使用 @ConditionalOnProperty 支持开关配置
- 配置属性类（ProvenanceClientProperties）：
    - @ConfigurationProperties(prefix = "patra.provenance.client")
    - 包含启用开关、默认配置等
- 自动装配文件：
    - 路径：META-INF/spring/org.springframework.boot.autoconfigure.
      AutoConfiguration.imports
    - 内容：com.patra.starter.provenance.client.boot.ProvenanceClie
      ntAutoConfiguration

9. 可观测性需求

- 链路追踪：支持 SkyWalking traceId 传递（依赖 SkyWalking Agent
  自动注入）
- 日志要求：在关键节点输出日志（API
  调用开始、结束、耗时等），日志中包含 traceId
- 日志级别：INFO 记录关键操作，DEBUG 记录详细参数

10. 分页与批量处理需求

- 分页支持：只提供基础 API 方法，分页逻辑由调用方自己处理
- 批量处理：不提供自动分批功能，批次大小等配置在
  ProvenanceConfigResp 中已定义，由调用方根据配置处理

11. 明确不在首期范围的需求（后续优化）

- 错误处理与异常体系设计
- API Key 管理与认证机制
- 性能优化（并发控制、连接池）
- 响应缓存机制
- 自动重试与降级策略
- 单元测试与集成测试

  ---
