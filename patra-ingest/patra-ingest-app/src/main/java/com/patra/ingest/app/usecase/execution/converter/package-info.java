/**
 * 数据转换器包。
 *
 * <p>本包提供执行过程中的数据转换逻辑，特别是 Provenance 配置的转换。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>将配置快照 JSON 转换为强类型对象
 *   <li>提取配置中的特定参数（如 API 端点、超时设置）
 *   <li>适配不同数据源的配置格式
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code ProvenanceConfigConverter} - Provenance 配置转换器
 *       <ul>
 *         <li>将配置快照 JSON 转换为 {@code ProvenanceConfig} 对象
 *         <li>提取 API 配置、参数配置、超时配置
 *       </ul>
 * </ul>
 *
 * <h2>配置快照示例</h2>
 * <h3>PubMed 配置快照</h3>
 * <pre>
 * {
 *   "provenanceCode": "pubmed",
 *   "displayName": "PubMed",
 *   "apiConfig": {
 *     "baseUrl": "https://eutils.ncbi.nlm.nih.gov/entrez/eutils",
 *     "searchEndpoint": "/esearch.fcgi",
 *     "fetchEndpoint": "/efetch.fcgi",
 *     "timeout": 30000,
 *     "retryMaxAttempts": 3
 *   },
 *   "defaultParams": {
 *     "db": "pubmed",
 *     "retmode": "xml",
 *     "retmax": "10000"
 *   }
 * }
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class ProvenanceConfigConverter {
 *     private final ObjectMapper objectMapper;
 *
 *     public ProvenanceConfig convert(String configSnapshotJson) {
 *         try {
 *             // 1. 反序列化 JSON
 *             var configNode = objectMapper.readTree(configSnapshotJson);
 *
 *             // 2. 提取配置
 *             var apiConfig = extractApiConfig(configNode.get("apiConfig"));
 *             var defaultParams = extractDefaultParams(configNode.get("defaultParams"));
 *
 *             // 3. 构建配置对象
 *             return ProvenanceConfig.builder()
 *                 .provenanceCode(configNode.get("provenanceCode").asText())
 *                 .displayName(configNode.get("displayName").asText())
 *                 .apiConfig(apiConfig)
 *                 .defaultParams(defaultParams)
 *                 .build();
 *         } catch (JsonProcessingException e) {
 *             throw new ConfigConversionException("Failed to parse config snapshot", e);
 *         }
 *     }
 *
 *     private ApiConfig extractApiConfig(JsonNode apiConfigNode) {
 *         return ApiConfig.builder()
 *             .baseUrl(apiConfigNode.get("baseUrl").asText())
 *             .searchEndpoint(apiConfigNode.get("searchEndpoint").asText())
 *             .fetchEndpoint(apiConfigNode.get("fetchEndpoint").asText())
 *             .timeout(apiConfigNode.get("timeout").asInt())
 *             .retryMaxAttempts(apiConfigNode.get("retryMaxAttempts").asInt())
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.execution.converter;
