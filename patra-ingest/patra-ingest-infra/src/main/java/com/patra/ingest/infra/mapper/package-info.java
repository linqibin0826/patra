/// 数据源参数映射器包。
///
/// ## 职责
///
/// 本包实现了六边形架构中 Infrastructure 层的参数映射功能,将通用的领域批次模型({@link
/// com.patra.ingest.domain.model.vo.batch.Batch})转换为数据源特定的请求参数。
///
/// ## 架构定位
///
/// - **层级**: Infrastructure 层（外围层）
///   - **职责边界**: 封装数据源技术细节（参数名、分页机制、会话令牌）
///   - **依赖方向**: 依赖 Domain 层的 Batch 值对象,实现技术适配
///   - **设计原则**: 单一职责原则（SRP）+ 开闭原则（OCP）
///
/// ## 核心组件
///
/// - {@link com.patra.ingest.infra.mapper.ProviderParameterMapper} - 参数映射器接口（策略模式）
///   - {@link com.patra.ingest.infra.mapper.ProviderParameterMapperRegistry} - 映射器注册表（自动发现和路由）
///   - {@link com.patra.ingest.infra.mapper.impl.PubmedParameterMapper} - PubMed
// 参数映射实现（retstart/retmax
///       + History Server）
///   - {@link com.patra.ingest.infra.mapper.impl.EpmcParameterMapper} - EPMC 参数映射实现（pageSize/page +
///       cursorMark）
///   - {@link com.patra.ingest.infra.mapper.impl.DoajParameterMapper} - DOAJ 参数映射实现（pageSize/page +
///       Scroll API）
///
/// ## 设计模式
///
/// ### 1. 策略模式（Strategy Pattern）
///
/// 每个数据源对应一个 `ProviderParameterMapper` 实现:
///
/// ```java
/// public interface ProviderParameterMapper {
///     ProvenanceCode getSupportedProvenance();
///     JsonNode mapParameters(Batch batch, JsonNode baseParams, QuerySession metadata);
/// ```
///
/// ### 2. 注册表模式（Registry Pattern）
///
/// `ProviderParameterMapperRegistry` 自动发现所有 Mapper 实现并注册:
///
/// ```java
/// @Component
/// public class ProviderParameterMapperRegistry {
///     public ProviderParameterMapperRegistry(List<ProviderParameterMapper> mappers) {
///         this.mapperMap = buildMapperMap(mappers);  // 自动注册
///
///     public JsonNode mapBatchParameters(Batch batch, ProvenanceCode code, ...) {
///         ProviderParameterMapper mapper = mapperMap.get(code);  // 路由
///         return mapper.mapParameters(batch, baseParams, metadata);
/// ```
///
/// ## 映射规则示例
///
/// ### PubMed
///
/// ```java
/// // Domain 模型（通用）
/// Batch batch = new Batch(1, "cancer", 0, 500);
///
/// // PubMed 特定参数（Infrastructure 层转换）
/// {
///   "retstart": 0,      // batch.offset()
///   "retmax": 500,      // batch.limit()
///   "WebEnv": "...",    // metadata.stateToken("webEnv")
///   "query_key": "..."  // metadata.stateToken("queryKey")
/// ```
///
/// ### EPMC
///
/// ```java
/// // Domain 模型（通用）
/// Batch batch = new Batch(1, "cancer", 0, 500);
///
/// // EPMC 特定参数（Infrastructure 层转换）
/// {
///   "pageSize": 500,            // batch.limit()
///   "page": 1,                  // batch.offset() / batch.limit() + 1
///   "cursorMark": "*",          // metadata.stateToken("cursorMark")
///   "cursorMarkEnabled": true
/// ```
///
/// ### DOAJ
///
/// ```java
/// // Domain 模型（通用）
/// Batch batch = new Batch(1, "cancer", 0, 500);
///
/// // DOAJ 特定参数（Infrastructure 层转换）
/// {
///   "pageSize": 500,     // batch.limit()
///   "page": 1,           // batch.offset() / batch.limit() + 1
///   "scroll": "5m",      // Elasticsearch Scroll API
///   "scroll_id": "..."   // metadata.stateToken("scrollId")
/// ```
///
/// ## 扩展指南
///
/// 新增数据源参数映射（以 Crossref 为例）:
///
/// ```java
/// @Component
/// public class CrossrefParameterMapper implements ProviderParameterMapper {
///
///     @Override
///     public ProvenanceCode getSupportedProvenance() {
///         return ProvenanceCode.CROSSREF;  // 声明支持的数据源
///
///     @Override
///     public JsonNode mapParameters(Batch batch, JsonNode baseParams, QuerySession metadata) {
///         ObjectMapper mapper = JsonMapperHolder.getObjectMapper();
///         ObjectNode params = baseParams != null && baseParams.isObject()
///             ? ((ObjectNode) baseParams).deepCopy()
///             : mapper.createObjectNode();
///
///         // Crossref 使用 offset + rows 参数
///         params.put("offset", batch.offset());
///         params.put("rows", batch.limit());
///
///         // 添加游标令牌（如有）
///         metadata.stateToken().ifPresent(token -> {
///             if (token.containsKey("cursor")) {
///                 params.put("cursor", token.get("cursor")););
///
///         return params;
/// ```
///
/// **零配置自动注册**:
///
/// ## 使用示例
///
/// 在 `ProvenanceDataAdapter` 中使用:
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class ProvenanceDataAdapter implements ProvenanceDataPort {
///     private final ProviderParameterMapperRegistry parameterMapperRegistry;
///     private final ProvenanceDataProvider provider;
///
///     @Override
///     public <T> DataFetchResult<T> fetchData(
///             ExecutionContext context,
///             DataType dataType,
///             TypeReference<T> typeRef,
///             Batch batch) {
///
///         // 1. 映射批次参数（自动选择对应的 Mapper）
///         JsonNode batchParams = parameterMapperRegistry.mapBatchParameters(
///             batch,
///             context.provenanceCode(),
///             context.compiledParams(),
///             metadata
///         );
///
///         // 2. 构建提供者请求
///         ProviderRequest request = ProviderRequest.builder()
///             .query(context.compiledQuery())
///             .params(batchParams)  // 使用映射后的参数
///             .configSnapshot(context.configSnapshot())
///             .build();
///
///         // 3. 调用数据源
///         return provider.fetchData(request, dataType, typeRef);
/// ```
///
/// ## 架构原则
///
/// ### 1. 依赖倒置原则（DIP）
///
/// - Domain 层定义通用的 `Batch` 值对象（不知道具体数据源）
///   - Infrastructure 层实现数据源特定的参数映射（依赖 Domain）
///   - 通过接口解耦,符合六边形架构的端口-适配器模式
///
/// ### 2. 单一职责原则（SRP）
///
/// - **Domain 层 Batch**: 只关注业务概念（批次编号、查询、分页范围）
///   - **Infrastructure 层 ParameterMapper**: 只关注技术细节（参数名、分页机制、会话令牌）
///   - **Application 层 Strategy**: 只关注批次生成逻辑（如何计算 offset/limit）
///
/// ### 3. 开闭原则（OCP）
///
/// - 新增数据源无需修改 `ProviderParameterMapperRegistry`
///   - 通过 Spring 自动扫描和构造器注入实现零配置扩展
///
/// ## 关键设计决策
///
/// ### 为什么参数映射在 Infrastructure 层而非 Application 层？
///
/// - **技术细节隔离**: 参数名（retstart vs offset）是数据源的技术实现细节
///   - **领域模型纯净**: Batch 作为纯领域模型,不应包含技术协议
///   - **职责分离**:
///
/// - Application 层 Strategy: 决定"什么时候"创建批次、"如何"计算 offset/limit
///         - Infrastructure 层 Mapper: 决定"如何"将通用参数转换为数据源特定参数
///
/// ### 为什么使用 JsonNode 而非强类型 DTO？
///
/// - **灵活性**: 每个数据源的参数结构不同,JsonNode 提供统一的表示
///   - **动态性**: 支持运行时添加参数（如会话令牌）
///   - **框架对齐**: `ProvenanceDataProvider` 使用 JsonNode 作为参数类型
///
/// ## 相关文档
///
/// - {@link com.patra.ingest.domain.model.vo.batch} - Domain 层批次值对象
///   - {@link com.patra.ingest.app.usecase.execution.strategy.batch} - Application 层批次生成策略
///   - {@link com.patra.ingest.infra.integration.provenance.ProvenanceDataAdapter} - 数据源适配器
///
/// @author linqibin
/// @since 0.1.0
package com.patra.ingest.infra.mapper;
