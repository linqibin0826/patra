/**
 * 数据源参数映射器实现包。
 *
 * <h2>职责</h2>
 *
 * <p>本包包含了 {@link com.patra.ingest.infra.mapper.ProviderParameterMapper} 接口的各数据源具体实现，负责将通用的
 * {@link com.patra.ingest.domain.model.vo.batch.Batch} 转换为数据源特定的请求参数。
 *
 * <h2>架构定位</h2>
 *
 * <ul>
 *   <li><strong>层级</strong>: Infrastructure 层 → mapper 子包 → impl 实现包
 *   <li><strong>设计模式</strong>: 策略模式（Strategy Pattern）实现
 *   <li><strong>依赖注入</strong>: 所有实现类使用 {@code @Component} 标注，由 Spring 自动扫描
 *   <li><strong>自动注册</strong>: {@link com.patra.ingest.infra.mapper.ProviderParameterMapperRegistry} 自动发现并注册
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.infra.mapper.impl.PubmedParameterMapper} - PubMed 参数映射器（retstart/retmax + WebEnv/query_key）
 *   <li>{@link com.patra.ingest.infra.mapper.impl.EpmcParameterMapper} - EPMC 参数映射器（pageSize/page + cursorMark）
 *   <li>{@link com.patra.ingest.infra.mapper.impl.DoajParameterMapper} - DOAJ 参数映射器（pageSize/page + Scroll API）
 * </ul>
 *
 * <h2>实现规范</h2>
 *
 * <p>每个参数映射器必须：
 *
 * <ol>
 *   <li><strong>实现接口</strong>: 实现 {@code ProviderParameterMapper} 接口
 *   <li><strong>声明支持</strong>: 实现 {@code getSupportedProvenance()} 返回支持的数据源代码
 *   <li><strong>参数映射</strong>: 实现 {@code mapParameters()} 完成参数转换
 *   <li><strong>Spring Bean</strong>: 使用 {@code @Component} 标注为 Spring 管理的 Bean
 *   <li><strong>状态令牌</strong>: 从 {@link com.patra.ingest.domain.model.vo.query.QuerySession#stateToken()} 读取会话状态
 * </ol>
 *
 * <h2>参数映射规则示例</h2>
 *
 * <h3>PubMed 映射规则</h3>
 *
 * <pre>{@code
 * // Domain 模型（通用）
 * Batch batch = new Batch(1, "cancer", 0, 500);
 *
 * // PubMed 特定参数（使用 PubMedParamKeys 常量）
 * {
 *   "retstart": 0,                      // batch.offset()
 *   "retmax": 500,                      // batch.limit()
 *   "WebEnv": "...",                    // metadata.stateToken(StateTokenKeys.PUBMED_WEBENV)
 *   "query_key": "..."                  // metadata.stateToken(StateTokenKeys.PUBMED_QUERY_KEY)
 * }
 * }</pre>
 *
 * <h3>EPMC 映射规则</h3>
 *
 * <pre>{@code
 * // Domain 模型（通用）
 * Batch batch = new Batch(1, "cancer", 0, 500);
 *
 * // EPMC 特定参数（使用 EpmcParamKeys 常量）
 * {
 *   "pageSize": 500,                    // batch.limit()
 *   "cursorMark": "*"                   // metadata.stateToken(StateTokenKeys.EPMC_CURSOR_MARK)
 * }
 * }</pre>
 *
 * <h3>DOAJ 映射规则</h3>
 *
 * <pre>{@code
 * // Domain 模型（通用）
 * Batch batch = new Batch(1, "cancer", 0, 500);
 *
 * // DOAJ 特定参数（使用 DoajParamKeys 常量）
 * {
 *   "pageSize": 500,                    // batch.limit()
 *   "page": 1,                          // batch.offset() / batch.limit() + 1
 *   "scroll_id": "..."                  // metadata.stateToken(StateTokenKeys.DOAJ_CURSOR_MARK)（如有）
 * }
 * }</pre>
 *
 * <h2>扩展指南</h2>
 *
 * <p><strong>新增数据源参数映射器</strong>（以 Crossref 为例）:
 *
 * <pre>{@code
 * package com.patra.ingest.infra.mapper.impl;
 *
 * import com.patra.common.enums.ProvenanceCode;
 * import com.patra.common.provenance.api.params.CrossrefParamKeys;
 * import com.patra.ingest.infra.mapper.ProviderParameterMapper;
 * import org.springframework.stereotype.Component;
 *
 * @Component
 * public class CrossrefParameterMapper implements ProviderParameterMapper {
 *
 *     @Override
 *     public ProvenanceCode getSupportedProvenance() {
 *         return ProvenanceCode.CROSSREF;  // 声明支持的数据源
 *     }
 *
 *     @Override
 *     public JsonNode mapParameters(Batch batch, JsonNode baseParams, QuerySession metadata) {
 *         ObjectMapper mapper = JsonMapperHolder.getObjectMapper();
 *         ObjectNode params = baseParams != null && baseParams.isObject()
 *             ? ((ObjectNode) baseParams).deepCopy()
 *             : mapper.createObjectNode();
 *
 *         // Crossref 使用 offset + rows 参数（使用常量）
 *         params.put(CrossrefParamKeys.OFFSET, batch.offset());
 *         params.put(CrossrefParamKeys.ROWS, batch.limit());
 *
 *         // 添加游标令牌（如有）
 *         metadata.stateToken().ifPresent(token -> {
 *             if (token.containsKey(StateTokenKeys.CROSSREF_CURSOR)) {
 *                 params.put(CrossrefParamKeys.CURSOR, token.get(StateTokenKeys.CROSSREF_CURSOR));
 *             }
 *         });
 *
 *         return params;
 *     }
 * }
 * }</pre>
 *
 * <p><strong>零配置自动注册</strong>:
 *
 * <ol>
 *   <li>创建新的 Mapper 类并实现 {@code ProviderParameterMapper} 接口
 *   <li>使用 {@code @Component} 标注为 Spring Bean
 *   <li>{@code ProviderParameterMapperRegistry} 会在启动时自动发现并注册
 *   <li>无需修改任何配置文件或注册代码
 * </ol>
 *
 * <h2>使用的常量类</h2>
 *
 * <ul>
 *   <li>{@link com.patra.common.provenance.api.params.PubMedParamKeys} - PubMed API 参数键
 *   <li>{@link com.patra.common.provenance.api.params.EpmcParamKeys} - EPMC API 参数键
 *   <li>{@link com.patra.common.provenance.api.params.DoajParamKeys} - DOAJ API 参数键
 *   <li>{@link com.patra.ingest.infra.mapper.StateTokenKeys} - StateToken 内部键（会话状态传递）
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <h3>1. 单一职责原则（SRP）</h3>
 *
 * <p>每个 Mapper 只负责一个数据源的参数映射，职责清晰。
 *
 * <h3>2. 开闭原则（OCP）</h3>
 *
 * <p>新增数据源时无需修改现有代码，只需添加新的 Mapper 实现。
 *
 * <h3>3. 依赖倒置原则（DIP）</h3>
 *
 * <p>依赖抽象接口 {@code ProviderParameterMapper}，而非具体实现。
 *
 * <h3>4. 常量优先</h3>
 *
 * <p>使用 {@code patra-common-provenance-api} 中定义的参数键常量，避免硬编码字符串。
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.infra.mapper} - 参数映射器包文档
 *   <li>{@link com.patra.ingest.infra.mapper.ProviderParameterMapper} - 参数映射器接口
 *   <li>{@link com.patra.ingest.infra.mapper.ProviderParameterMapperRegistry} - 映射器注册表
 *   <li>{@link com.patra.ingest.infra.mapper.StateTokenKeys} - StateToken 键常量
 *   <li>{@link com.patra.common.provenance.api.params} - Provenance API 参数键常量包
 * </ul>
 *
 * @author linqibin
 * @since 0.3.0
 */
package com.patra.ingest.infra.mapper.impl;
