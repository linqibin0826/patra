/// 表达式编译器包 - 动态表达式编译和渲染框架。
/// 
/// 本包提供 Patra 动态表达式系统的核心编译器,将领域表达式编译为数据源特定的查询参数和请求。 编译器基于 patra-expr-kernel
/// 表达式内核,支持多数据源、多操作类型的动态查询生成。
/// 
/// ## 职责
/// 
/// - 将领域表达式({@link com.patra.expr.Expr})编译为数据源特定的查询参数
///   - 支持标准键到提供商参数的映射转换
///   - 提供表达式规范化、验证和能力检查
///   - 支持多数据源配置和操作类型切片
///   - 提供编译流水线(规范化 → 验证 → 渲染 → 转换)
///   - 集成 Spring Boot 自动配置
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.starter.expr.compiler.ExprCompiler} - 表达式编译器接口, 定义编译方法和便捷重载
///   - {@link com.patra.starter.expr.compiler.DefaultExprCompiler} - 默认编译器实现, 实现完整的编译流水线(规范化 → 验证
///       → 渲染 → 转换)
///   - `CompileRequest` - 编译请求对象,包含表达式、数据源、操作类型等信息
///   - `CompileResult` - 编译结果对象,包含查询参数、验证报告、渲染跟踪等
///   - `CompileRequestBuilder` - 编译请求构建器,提供流式 API
/// 
/// ## 编译流水线
/// 
/// 表达式编译分为以下阶段:
/// 
/// ## 使用场景
/// 
/// - **API 请求生成**: 根据领域表达式生成 HTTP 查询参数
///   - **动态查询构建**: 支持用户自定义查询条件
///   - **多数据源适配**: 同一表达式适配不同数据源的查询格式
///   - **表达式验证**: 验证表达式是否符合数据源能力约束
/// 
/// ## 使用示例
/// 
/// ```java
/// // 1. 注入编译器(Spring 自动配置)
/// @Autowired
/// private ExprCompiler compiler;
/// 
/// // 2. 创建领域表达式(使用 patra-expr-kernel DSL)
/// Expr expression = Expr.and(
///     Expr.eq("pubDate", "2024-01-01"),
///     Expr.contains("title", "covid")
/// );
/// 
/// // 3. 编译为 PubMed 查询参数
/// CompileResult result = compiler.compile(expression, ProvenanceCode.PUBMED);
/// 
/// // 4. 获取查询参数
/// Map<String, String> params = result.getQueryParams();
/// // params = {
/// //   "mindate": "2024/01/01",
/// //   "term": "covid[Title]"
/// //
/// 
/// // 5. 检查验证结果
/// ValidationReport report = result.getValidationReport();
/// if (!report.isValid()) {
///     List<Issue> errors = report.getErrors();
///     // 处理验证错误
/// 
/// // 6. 使用编译请求构建器
/// CompileRequest request = CompileRequestBuilder
///     .of(expression, ProvenanceCode.EPMC)
///     .forOperationType("HARVEST")
///     .forOperation("search")
///     .withStrictMode(true)
///     .build();
/// CompileResult result2 = compiler.compile(request);
/// ```
/// 
/// ## 标准键到提供商参数映射
/// 
/// 编译器使用标准键(std_key)抽象查询参数,通过数据源映射转换为提供商参数:
/// 
/// - **标准键**: 统一的参数名称(如 `minDate`、`maxDate`、`term`)
///   - **提供商参数**: 数据源特定的参数名称(如 PubMed 的 `mindate`、EPMC 的 `fromDate`)
///   - **映射规则**: 在表达式快照的 `apiParamMappings` 中定义
/// 
/// ## 配置示例
/// 
/// ```java
/// # application.yml
/// patra:
///   expr:
///     compiler:
///       strict-mode: false              # 严格模式,默认宽松
///       fail-on-unsupported: false      # 遇到不支持的特性是否失败
///       cache-snapshot: true            # 是否缓存表达式快照
///       cache-ttl: 3600                 # 快照缓存 TTL(秒)
///     mode:
///       fallback-enabled: true          # 启用降级模式
///       validation-level: WARN          # 验证级别: ERROR/WARN/INFO
/// ```
/// 
/// ## 与 patra-expr-kernel 的关系
/// 
/// - **patra-expr-kernel**: 提供表达式内核,定义 DSL 和表达式树结构
///   - **本包**: 提供编译器实现,将表达式编译为查询参数
///   - **协作**: 内核定义"是什么",编译器实现"怎么做"
/// 
/// ## Spring Boot 集成
/// 
/// 本包通过 patra-spring-boot-starter-expr 提供自动配置:
/// 
/// - **自动配置**: `ExprCompilerAutoConfiguration` 自动注册编译器 Bean
///   - **配置绑定**: 支持 `application.yml` 配置编译器行为
///   - **依赖注入**: 编译器可直接通过 `@Autowired` 注入
///   - **指标集成**: 自动暴露编译器性能指标(编译次数、耗时等)
/// 
/// ## 设计原则
/// 
/// - **流式 API**: 提供流式构建器,简化编译请求创建
///   - **可扩展**: 支持自定义渲染规则、转换器和验证器
///   - **性能优化**: 快照缓存、延迟加载、并行验证
///   - **错误友好**: 详细的验证报告和渲染跟踪,便于调试
///   - **降级支持**: 遇到不支持的特性时提供降级策略
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.expr.compiler;
