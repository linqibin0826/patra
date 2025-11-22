/// 表达式值对象包 - Registry Domain 层。
/// 
/// 本包包含表达式元数据的核心值对象,定义了动态表达式系统的配置和渲染规则。 表达式系统用于动态生成 API 请求参数、字段映射和渲染逻辑,支持 patra-expr-kernel
/// 的编译和执行。
/// 
/// ## 职责
/// 
/// - 定义表达式快照值对象,聚合字段定义、渲染规则、参数映射和能力
///   - 定义表达式字段值对象,描述字段的类型、路径和提取规则
///   - 定义渲染规则值对象,指定字段的输出格式和转换逻辑
///   - 定义参数映射值对象,描述 API 参数到表达式变量的映射关系
///   - 定义能力值对象,声明表达式支持的功能特性
/// 
/// ## 核心值对象
/// 
/// - {@link com.patra.registry.domain.model.vo.expr.ExprSnapshot} - 表达式快照聚合,
///       包含字段定义、渲染规则、参数映射和能力的完整视图
///   - {@link com.patra.registry.domain.model.vo.expr.ExprField} - 表达式字段定义, 描述字段名称、类型、路径和提取规则
///   - {@link com.patra.registry.domain.model.vo.expr.ExprRenderRule} - 表达式渲染规则,
///       指定字段的输出格式、日期格式化、空值处理等
///   - {@link com.patra.registry.domain.model.vo.expr.ApiParamMapping} - API 参数映射, 描述 API
///       参数名到表达式变量的映射关系
///   - {@link com.patra.registry.domain.model.vo.expr.ExprCapability} - 表达式能力声明,
///       定义表达式支持的功能特性(如分页、排序、过滤)
/// 
/// ## 设计原则
/// 
/// - **不可变性**: 所有值对象使用 `record` 实现,一旦创建不可修改
///   - **聚合结构**: {@link com.patra.registry.domain.model.vo.expr.ExprSnapshot}
///       作为聚合根,组合其他值对象提供完整视图
///   - **时态支持**: 表达式快照支持时态查询,可获取特定时刻的表达式配置
///   - **框架无关**: 纯 Java 对象,不依赖 Spring、JPA 等框架
/// 
/// ## ExprSnapshot 聚合
/// 
/// 表达式快照是表达式系统的核心聚合,包含以下组成部分:
/// 
/// - **fields**: 字段定义列表,描述表达式可访问的所有字段
///   - **renderRules**: 渲染规则列表,定义字段的输出格式
///   - **paramMappings**: 参数映射列表,描述 API 参数到表达式变量的映射
///   - **capabilities**: 能力声明列表,定义表达式支持的功能特性
/// 
/// ## 使用场景
/// 
/// - **表达式编译**: patra-expr-kernel 根据快照编译动态表达式
///   - **API 请求生成**: 根据参数映射和渲染规则生成 HTTP 请求
///   - **字段提取**: 根据字段定义从响应中提取数据
///   - **能力检测**: 根据能力声明判断数据源支持的功能
/// 
/// ## 使用示例
/// 
/// ```java
/// // 加载表达式快照
/// Optional<ExprSnapshot> snapshot = exprRepository.loadSnapshot(
///     ProvenanceCode.PUBMED,
///     "HARVEST",
///     "search",
///     Instant.now()
/// );
/// 
/// // 访问字段定义
/// List<ExprField> fields = snapshot.get().fields();
/// 
/// // 访问渲染规则
/// List<ExprRenderRule> rules = snapshot.get().renderRules();
/// 
/// // 访问参数映射
/// List<ApiParamMapping> mappings = snapshot.get().paramMappings();
/// 
/// // 访问能力声明
/// List<ExprCapability> capabilities = snapshot.get().capabilities();
/// ```
/// 
/// ## 与表达式系统集成
/// 
/// 本包定义的值对象由以下组件使用:
/// 
/// - **patra-expr-kernel**: 表达式编译和执行引擎
///   - **patra-spring-boot-starter-expr**: Spring Boot 集成层
///   - **patra-ingest**: 数据采集服务,使用表达式生成 API 请求
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.domain.model.vo.expr;
