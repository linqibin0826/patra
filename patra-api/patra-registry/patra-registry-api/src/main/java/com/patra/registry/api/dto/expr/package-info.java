/// 表达式 API 数据传输对象包 - REST API 契约层。
///
/// 本包包含表达式服务相关的 REST API 响应 DTOs,定义了表达式快照、字段定义、渲染规则和参数映射的数据传输对象。 表达式系统用于动态生成 API
/// 请求参数、字段映射和渲染逻辑,支持 patra-expr-kernel 的编译和执行。
///
/// ## 职责
///
/// - 定义表达式快照的 API 响应格式
///   - 定义表达式字段的 API 响应格式
///   - 定义渲染规则的 API 响应格式
///   - 定义参数映射的 API 响应格式
///   - 定义能力声明的 API 响应格式
///   - 支持 JSON 序列化和 Feign 客户端集成
///
/// ## 核心 DTOs
///
/// - {@link com.patra.registry.api.dto.expr.ExprSnapshotResp} - 表达式快照响应,
///       聚合字段定义、渲染规则、参数映射和能力的完整视图
///   - {@link com.patra.registry.api.dto.expr.ExprFieldResp} - 表达式字段响应, 描述字段名称、类型、路径和提取规则
///   - {@link com.patra.registry.api.dto.expr.ExprRenderRuleResp} - 渲染规则响应, 指定字段的输出格式、日期格式化、空值处理等
///   - {@link com.patra.registry.api.dto.expr.ApiParamMappingResp} - 参数映射响应, 描述 API 参数名到表达式变量的映射关系
///   - {@link com.patra.registry.api.dto.expr.ExprCapabilityResp} - 能力声明响应,
///       定义表达式支持的功能特性(如分页、排序、过滤)
///
/// ## ExprSnapshotResp 聚合响应
///
/// 表达式快照响应 DTO 包含以下组成部分:
///
/// - **fields**: 字段定义列表,描述表达式可访问的所有字段
///   - **renderRules**: 渲染规则列表,定义字段的输出格式
///   - **paramMappings**: 参数映射列表,描述 API 参数到表达式变量的映射
///   - **capabilities**: 能力声明列表,定义表达式支持的功能特性
///
/// ## 与领域模型的映射
///
/// DTOs 由适配器层从领域模型转换:
///
/// - {@link com.patra.registry.domain.model.vo.expr.ExprSnapshot} → {@link
///       com.patra.registry.api.dto.expr.ExprSnapshotResp}
///   - {@link com.patra.registry.domain.model.vo.expr.ExprField} → {@link
///       com.patra.registry.api.dto.expr.ExprFieldResp}
///   - {@link com.patra.registry.domain.model.vo.expr.ExprRenderRule} → {@link
///       com.patra.registry.api.dto.expr.ExprRenderRuleResp}
///   - {@link com.patra.registry.domain.model.vo.expr.ApiParamMapping} → {@link
///       com.patra.registry.api.dto.expr.ApiParamMappingResp}
///   - {@link com.patra.registry.domain.model.vo.expr.ExprCapability} → {@link
///       com.patra.registry.api.dto.expr.ExprCapabilityResp}
///
/// ## 使用场景
///
/// - **表达式编译**: patra-expr-kernel 根据快照编译动态表达式
///   - **API 请求生成**: patra-ingest 根据参数映射和渲染规则生成 HTTP 请求
///   - **字段提取**: 根据字段定义从响应中提取数据
///   - **能力检测**: 根据能力声明判断数据源支持的功能
///
/// ## DTO 设计原则
///
/// - **不可变性**: 所有 DTOs 使用 `record` 实现
///   - **聚合结构**: {@link com.patra.registry.api.dto.expr.ExprSnapshotResp} 作为聚合根
///   - **扁平化**: 简化嵌套结构,便于 JSON 序列化
///   - **无业务逻辑**: 纯数据传输对象
///
/// ## 使用示例
///
/// ```java
/// // Feign 客户端调用
/// @Autowired
/// private ExprClient exprClient;
///
/// // 查询表达式快照
/// ExprSnapshotResp snapshot = exprClient.getSnapshot(
///     "PUBMED",
///     "HARVEST",
///     "search",
///     Instant.now()
/// );
///
/// // 访问字段定义
/// List<ExprFieldResp> fields = snapshot.fields();
///
/// // 访问渲染规则
/// List<ExprRenderRuleResp> rules = snapshot.renderRules();
///
/// // 访问参数映射
/// List<ApiParamMappingResp> mappings = snapshot.paramMappings();
/// ```
///
/// ## JSON 响应示例
///
/// ```java
/// {
///   "fields": [
///     {
///       "name": "pubDate",
///       "type": "DATE",
///       "path": "$.PubDate"
///   ],
///   "renderRules": [
///     {
///       "fieldName": "pubDate",
///       "format": "yyyy/MM/dd"
///   ],
///   "paramMappings": [
///     {
///       "standardKey": "minDate",
///       "providerParam": "mindate"
///   ],
///   "capabilities": [
///     {
///       "name": "PAGINATION",
///       "supported": true
///   ]
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.api.dto.expr;
