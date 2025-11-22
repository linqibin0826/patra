/// Plan 表达式构建器包。
/// 
/// 本包负责构建 Plan 的表达式描述符（未编译的原始表达式）， 表达式在 Task 执行阶段会被编译为可执行的查询和参数。
/// 
/// ## 职责
/// 
/// - 根据 Provenance 配置构建原始表达式
///   - 生成表达式的哈希值（用于去重和缓存）
///   - 保存表达式快照（避免配置变更影响执行）
///   - 提供表达式的序列化和反序列化
/// 
/// ## 核心组件
/// 
/// - `PlanExpressionBuilder` - 表达式构建器
///   - `PlanExpressionDescriptor` - 表达式描述符
///       
/// - `exprProtoJson`: 原始表达式 JSON（ExprProto）
///         - `exprHash`: 表达式哈希值（用于去重）
///         - `normalizedExpression`: 规范化表达式（用于显示）
/// 
/// ## 表达式生命周期
/// 
/// ```
/// 
/// 1. Plan 摄入阶段（本包）
///    └─ PlanExpressionBuilder.build() → PlanExpressionDescriptor（未编译）
/// 
/// 2. Plan 持久化
///    └─ 保存 exprProtoJson 到 plan.expr_proto_snapshot_json
/// 
/// 3. Task 执行准备阶段
///    └─ ExecutionContextLoader 加载表达式快照
/// 
/// 4. Task 执行阶段
///    └─ ExpressionCompilerPort.compile(exprProtoJson) → 编译为可执行查询
/// 
/// ```
/// 
/// ## 表达式示例
/// 
/// ### PubMed HARVEST 表达式
/// 
/// ```
/// 
/// {
///   "query": "entrez_date:[${from} TO ${to}]",
///   "params": {
///     "from": "${window.from}",
///     "to": "${window.to}",
///     "retmax": "10000",
///     "sort": "pub_date"
///   }
/// }
/// 
/// ```
/// 
/// ### EPMC SEARCH 表达式
/// 
/// ```
/// 
/// {
///   "query": "(FIRST_PDATE:[${from} TO ${to}])",
///   "params": {
///     "from": "${window.from}",
///     "to": "${window.to}",
///     "pageSize": "1000",
///     "format": "json"
///   }
/// }
/// 
/// ```
/// 
/// ## 表达式哈希
/// 
/// - 使用 SHA-256 计算表达式的哈希值
///   - 用于判断两个 Plan 是否使用相同的表达式
///   - 支持表达式级别的去重和缓存
/// 
/// ## 使用示例
/// 
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class PlanExpressionBuilder {
///     private final PatraRegistryPort registryPort;
///     private final ObjectMapper objectMapper;
/// 
///     public PlanExpressionDescriptor build(
///         ProvenanceCode provenanceCode,
///         OperationCode operationCode
///     ) {
///         // 1. 加载配置
///         var config = registryPort.loadConfig(provenanceCode, operationCode);
/// 
///         // 2. 构建 ExprProto
///         var exprProto = ExprProto.builder()
///             .query(config.getQueryTemplate())
///             .params(config.getDefaultParams())
///             .build();
/// 
///         // 3. 序列化为 JSON
///         var exprProtoJson = objectMapper.writeValueAsString(exprProto);
/// 
///         // 4. 计算哈希
///         var exprHash = DigestUtils.sha256Hex(exprProtoJson);
/// 
///         // 5. 生成规范化表达式（用于显示）
///         var normalized = exprProto.getQuery();
/// 
///         return PlanExpressionDescriptor.builder()
///             .exprProtoJson(exprProtoJson)
///             .exprHash(exprHash)
///             .normalizedExpression(normalized)
///             .build();
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
/// @see com.patra.ingest.app.usecase.execution.session.ExecutionContextLoader 表达式编译
package com.patra.ingest.app.usecase.plan.expression;
