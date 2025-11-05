/**
 * 表达式编译器包 - 动态表达式编译和渲染框架。
 *
 * <p>本包提供 Patra 动态表达式系统的核心编译器,将领域表达式编译为数据源特定的查询参数和请求。
 * 编译器基于 patra-expr-kernel 表达式内核,支持多数据源、多操作类型的动态查询生成。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>将领域表达式({@link com.patra.expr.Expr})编译为数据源特定的查询参数
 *   <li>支持标准键到提供商参数的映射转换
 *   <li>提供表达式规范化、验证和能力检查
 *   <li>支持多数据源配置和操作类型切片
 *   <li>提供编译流水线(规范化 → 验证 → 渲染 → 转换)
 *   <li>集成 Spring Boot 自动配置
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.expr.compiler.ExprCompiler} - 表达式编译器接口,
 *       定义编译方法和便捷重载
 *   <li>{@link com.patra.starter.expr.compiler.DefaultExprCompiler} - 默认编译器实现,
 *       实现完整的编译流水线(规范化 → 验证 → 渲染 → 转换)
 *   <li>{@code CompileRequest} - 编译请求对象,包含表达式、数据源、操作类型等信息
 *   <li>{@code CompileResult} - 编译结果对象,包含查询参数、验证报告、渲染跟踪等
 *   <li>{@code CompileRequestBuilder} - 编译请求构建器,提供流式 API
 * </ul>
 *
 * <h2>编译流水线</h2>
 *
 * <p>表达式编译分为以下阶段:
 *
 * <ol>
 *   <li><strong>快照加载</strong>: 根据数据源、操作类型、端点名称加载表达式快照
 *   <li><strong>规范化</strong>: 规范化表达式结构,展开嵌套条件,合并重复项
 *   <li><strong>能力检查</strong>: 验证表达式是否符合数据源能力约束
 *   <li><strong>渲染</strong>: 根据渲染规则将表达式转换为查询片段
 *   <li><strong>参数映射</strong>: 将标准键转换为提供商特定参数名
 *   <li><strong>值转换</strong>: 应用值转换规则(日期格式化、大小写转换等)
 * </ol>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><strong>API 请求生成</strong>: 根据领域表达式生成 HTTP 查询参数
 *   <li><strong>动态查询构建</strong>: 支持用户自定义查询条件
 *   <li><strong>多数据源适配</strong>: 同一表达式适配不同数据源的查询格式
 *   <li><strong>表达式验证</strong>: 验证表达式是否符合数据源能力约束
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 1. 注入编译器(Spring 自动配置)
 * @Autowired
 * private ExprCompiler compiler;
 *
 * // 2. 创建领域表达式(使用 patra-expr-kernel DSL)
 * Expr expression = Expr.and(
 *     Expr.eq("pubDate", "2024-01-01"),
 *     Expr.contains("title", "covid")
 * );
 *
 * // 3. 编译为 PubMed 查询参数
 * CompileResult result = compiler.compile(expression, ProvenanceCode.PUBMED);
 *
 * // 4. 获取查询参数
 * Map<String, String> params = result.getQueryParams();
 * // params = {
 * //   "mindate": "2024/01/01",
 * //   "term": "covid[Title]"
 * // }
 *
 * // 5. 检查验证结果
 * ValidationReport report = result.getValidationReport();
 * if (!report.isValid()) {
 *     List<Issue> errors = report.getErrors();
 *     // 处理验证错误
 * }
 *
 * // 6. 使用编译请求构建器
 * CompileRequest request = CompileRequestBuilder
 *     .of(expression, ProvenanceCode.EPMC)
 *     .forOperationType("HARVEST")
 *     .forOperation("search")
 *     .withStrictMode(true)
 *     .build();
 * CompileResult result2 = compiler.compile(request);
 * }</pre>
 *
 * <h2>标准键到提供商参数映射</h2>
 *
 * <p>编译器使用标准键(std_key)抽象查询参数,通过数据源映射转换为提供商参数:
 *
 * <ul>
 *   <li><strong>标准键</strong>: 统一的参数名称(如 {@code minDate}、{@code maxDate}、{@code term})
 *   <li><strong>提供商参数</strong>: 数据源特定的参数名称(如 PubMed 的 {@code mindate}、EPMC 的 {@code fromDate})
 *   <li><strong>映射规则</strong>: 在表达式快照的 {@code apiParamMappings} 中定义
 * </ul>
 *
 * <h2>配置示例</h2>
 *
 * <pre>{@code
 * # application.yml
 * patra:
 *   expr:
 *     compiler:
 *       strict-mode: false              # 严格模式,默认宽松
 *       fail-on-unsupported: false      # 遇到不支持的特性是否失败
 *       cache-snapshot: true            # 是否缓存表达式快照
 *       cache-ttl: 3600                 # 快照缓存 TTL(秒)
 *     mode:
 *       fallback-enabled: true          # 启用降级模式
 *       validation-level: WARN          # 验证级别: ERROR/WARN/INFO
 * }</pre>
 *
 * <h2>与 patra-expr-kernel 的关系</h2>
 *
 * <ul>
 *   <li><strong>patra-expr-kernel</strong>: 提供表达式内核,定义 DSL 和表达式树结构
 *   <li><strong>本包</strong>: 提供编译器实现,将表达式编译为查询参数
 *   <li><strong>协作</strong>: 内核定义"是什么",编译器实现"怎么做"
 * </ul>
 *
 * <h2>Spring Boot 集成</h2>
 *
 * <p>本包通过 patra-spring-boot-starter-expr 提供自动配置:
 *
 * <ul>
 *   <li><strong>自动配置</strong>: {@code ExprCompilerAutoConfiguration} 自动注册编译器 Bean
 *   <li><strong>配置绑定</strong>: 支持 {@code application.yml} 配置编译器行为
 *   <li><strong>依赖注入</strong>: 编译器可直接通过 {@code @Autowired} 注入
 *   <li><strong>指标集成</strong>: 自动暴露编译器性能指标(编译次数、耗时等)
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>流式 API</strong>: 提供流式构建器,简化编译请求创建
 *   <li><strong>可扩展</strong>: 支持自定义渲染规则、转换器和验证器
 *   <li><strong>性能优化</strong>: 快照缓存、延迟加载、并行验证
 *   <li><strong>错误友好</strong>: 详细的验证报告和渲染跟踪,便于调试
 *   <li><strong>降级支持</strong>: 遇到不支持的特性时提供降级策略
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.expr.compiler;
