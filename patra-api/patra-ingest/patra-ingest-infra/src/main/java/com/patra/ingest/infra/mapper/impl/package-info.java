/// 数据源参数映射器实现包。
/// 
/// ## 职责
/// 
/// 本包包含了 {@link com.patra.ingest.infra.mapper.ProviderParameterMapper} 接口的各数据源具体实现，负责将通用的
/// {@link com.patra.ingest.domain.model.vo.batch.Batch} 转换为数据源特定的请求参数。
/// 
/// ## 架构定位
/// 
/// - **层级**: Infrastructure 层 → mapper 子包 → impl 实现包
///   - **设计模式**: 策略模式（Strategy Pattern）实现
///   - **依赖注入**: 所有实现类使用 `@Component` 标注，由 Spring 自动扫描
///   - **自动注册**: {@link com.patra.ingest.infra.mapper.ProviderParameterMapperRegistry} 自动发现并注册
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.ingest.infra.mapper.impl.PubmedParameterMapper} - PubMed 参数映射器（retstart/retmax + WebEnv/query_key）
///   - {@link com.patra.ingest.infra.mapper.impl.EpmcParameterMapper} - EPMC 参数映射器（pageSize/page + cursorMark）
///   - {@link com.patra.ingest.infra.mapper.impl.DoajParameterMapper} - DOAJ 参数映射器（pageSize/page + Scroll API）
/// 
/// ## 实现规范
/// 
/// 每个参数映射器必须：
/// 
/// ## 参数映射规则示例
/// 
/// ### PubMed 映射规则
/// 
/// ```java
/// // Domain 模型（通用）
/// Batch batch = new Batch(1, "cancer", 0, 500);
/// 
/// // PubMed 特定参数（使用 PubMedParamKeys 常量）
/// {
///   "retstart": 0,                      // batch.offset()
///   "retmax": 500,                      // batch.limit()
///   "WebEnv": "...",                    // metadata.stateToken(StateTokenKeys.PUBMED_WEBENV)
///   "query_key": "..."                  // metadata.stateToken(StateTokenKeys.PUBMED_QUERY_KEY)
/// ```
/// 
/// ### EPMC 映射规则
/// 
/// ```java
/// // Domain 模型（通用）
/// Batch batch = new Batch(1, "cancer", 0, 500);
/// 
/// // EPMC 特定参数（使用 EpmcParamKeys 常量）
/// {
///   "pageSize": 500,                    // batch.limit()
///   "cursorMark": "*"                   // metadata.stateToken(StateTokenKeys.EPMC_CURSOR_MARK)
/// ```
/// 
/// ### DOAJ 映射规则
/// 
/// ```java
/// // Domain 模型（通用）
/// Batch batch = new Batch(1, "cancer", 0, 500);
/// 
/// // DOAJ 特定参数（使用 DoajParamKeys 常量）
/// {
///   "pageSize": 500,                    // batch.limit()
///   "page": 1,                          // batch.offset() / batch.limit() + 1
///   "scroll_id": "..."                  // metadata.stateToken(StateTokenKeys.DOAJ_CURSOR_MARK)（如有）
/// ```
/// 
/// ## 扩展指南
/// 
/// **新增数据源参数映射器**（以 Crossref 为例）:
/// 
/// ```java
/// package com.patra.ingest.infra.mapper.impl;
/// 
/// import com.patra.common.enums.ProvenanceCode;
/// import com.patra.common.provenance.api.params.CrossrefParamKeys;
/// import com.patra.ingest.infra.mapper.ProviderParameterMapper;
/// import org.springframework.stereotype.Component;
/// 
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
///         // Crossref 使用 offset + rows 参数（使用常量）
///         params.put(CrossrefParamKeys.OFFSET, batch.offset());
///         params.put(CrossrefParamKeys.ROWS, batch.limit());
/// 
///         // 添加游标令牌（如有）
///         metadata.stateToken().ifPresent(token -> {
///             if (token.containsKey(StateTokenKeys.CROSSREF_CURSOR)) {
///                 params.put(CrossrefParamKeys.CURSOR, token.get(StateTokenKeys.CROSSREF_CURSOR)););
/// 
///         return params;
/// ```
/// 
/// **零配置自动注册**:
/// 
/// ## 使用的常量类
/// 
/// - {@link com.patra.common.provenance.api.params.PubMedParamKeys} - PubMed API 参数键
///   - {@link com.patra.common.provenance.api.params.EpmcParamKeys} - EPMC API 参数键
///   - {@link com.patra.common.provenance.api.params.DoajParamKeys} - DOAJ API 参数键
///   - {@link com.patra.ingest.infra.mapper.StateTokenKeys} - StateToken 内部键（会话状态传递）
/// 
/// ## 设计原则
/// 
/// ### 1. 单一职责原则（SRP）
/// 
/// 每个 Mapper 只负责一个数据源的参数映射，职责清晰。
/// 
/// ### 2. 开闭原则（OCP）
/// 
/// 新增数据源时无需修改现有代码，只需添加新的 Mapper 实现。
/// 
/// ### 3. 依赖倒置原则（DIP）
/// 
/// 依赖抽象接口 `ProviderParameterMapper`，而非具体实现。
/// 
/// ### 4. 常量优先
/// 
/// 使用 `patra-common-provenance-api` 中定义的参数键常量，避免硬编码字符串。
/// 
/// ## 相关文档
/// 
/// - {@link com.patra.ingest.infra.mapper} - 参数映射器包文档
///   - {@link com.patra.ingest.infra.mapper.ProviderParameterMapper} - 参数映射器接口
///   - {@link com.patra.ingest.infra.mapper.ProviderParameterMapperRegistry} - 映射器注册表
///   - {@link com.patra.ingest.infra.mapper.StateTokenKeys} - StateToken 键常量
///   - {@link com.patra.common.provenance.api.params} - Provenance API 参数键常量包
/// 
/// @author linqibin
/// @since 0.3.0
package com.patra.ingest.infra.mapper.impl;
