/**
 * MyBatis 自定义类型转换器包。
 *
 * <p>本包提供 MyBatis 的 {@link org.apache.ibatis.type.TypeHandler} 实现,用于在 Java 对象与数据库列类型之间进行自动转换,特别是处理复杂类型(如 JSON)的序列化和反序列化。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>实现 Java 对象到数据库列的双向转换逻辑
 *   <li>支持多种数据库(MySQL、PostgreSQL)的 JSON 列类型
 *   <li>处理 NULL 值和空字符串边界情况
 *   <li>集成 Spring 管理的 {@link com.fasterxml.jackson.databind.ObjectMapper} 确保 JSON 配置一致性
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.mybatis.type.JsonToJsonNodeTypeHandler} - Jackson {@code JsonNode} 类型转换器
 *   <li>{@link com.patra.starter.mybatis.type.JsonToMapTypeHandler} - {@code Map<String, Object>} 类型转换器
 * </ul>
 *
 * <h2>设计决策</h2>
 *
 * <ul>
 *   <li><b>依赖注入 ObjectMapper:</b> 使用 Spring 管理的 Jackson 实例,避免全局配置不一致(如日期格式、空值处理等)
 *   <li><b>多数据库兼容:</b> 支持 MySQL 的 JSON/TEXT 列和 PostgreSQL 的 JSON/JSONB 列
 *   <li><b>灵活的读取逻辑:</b> 处理 String、CLOB、字节数组、PGobject 等多种 JDBC 表示形式
 *   <li><b>严格的 NULL 处理:</b> 将数据库 NULL、空字符串和空白字符串统一转换为 Java null,避免解析错误
 * </ul>
 *
 * <h2>数据库兼容性</h2>
 *
 * <table border="1">
 *   <tr><th>数据库</th><th>列类型</th><th>JDBC 类型</th><th>支持情况</th></tr>
 *   <tr><td>MySQL 5.7+</td><td>JSON</td><td>VARCHAR/LONGVARCHAR</td><td>✓ 完全支持</td></tr>
 *   <tr><td>MySQL</td><td>TEXT/LONGTEXT</td><td>CLOB/LONGVARCHAR</td><td>✓ 完全支持</td></tr>
 *   <tr><td>PostgreSQL</td><td>JSON</td><td>OTHER (PGobject)</td><td>✓ 完全支持</td></tr>
 *   <tr><td>PostgreSQL</td><td>JSONB</td><td>OTHER (PGobject)</td><td>✓ 完全支持</td></tr>
 *   <tr><td>H2/Oracle</td><td>CLOB</td><td>CLOB</td><td>✓ 完全支持</td></tr>
 * </table>
 *
 * <h2>使用示例</h2>
 *
 * <p><b>实体类定义(使用 JsonNode):</b>
 * <pre>{@code
 * @Data
 * @EqualsAndHashCode(callSuper = true)
 * @TableName("provenance")
 * public class ProvenanceDO extends BaseDO {
 *     @TableField(value = "config", typeHandler = JsonToJsonNodeTypeHandler.class)
 *     private JsonNode config; // 数据库列为 JSON 或 TEXT 类型
 * }
 * }</pre>
 *
 * <p><b>实体类定义(使用 Map):</b>
 * <pre>{@code
 * @Data
 * @EqualsAndHashCode(callSuper = true)
 * @TableName("user_profile")
 * public class UserProfileDO extends BaseDO {
 *     @TableField(value = "settings", typeHandler = JsonToMapTypeHandler.class)
 *     private Map<String, Object> settings;
 * }
 * }</pre>
 *
 * <p><b>插入操作(自动序列化):</b>
 * <pre>{@code
 * ObjectMapper mapper = new ObjectMapper();
 * JsonNode config = mapper.createObjectNode()
 *     .put("apiKey", "abc123")
 *     .put("timeout", 5000);
 *
 * ProvenanceDO provenance = ProvenanceDO.builder()
 *     .name("PubMed")
 *     .config(config) // 自动序列化为 JSON 字符串存入数据库
 *     .build();
 * provenanceMapper.insert(provenance);
 * }</pre>
 *
 * <p><b>查询操作(自动反序列化):</b>
 * <pre>{@code
 * ProvenanceDO provenance = provenanceMapper.selectById(id);
 * JsonNode config = provenance.getConfig(); // 自动从 JSON 字符串反序列化
 * String apiKey = config.get("apiKey").asText();
 * }</pre>
 *
 * <p><b>全局注册 TypeHandler:</b>
 * <pre>{@code
 * @Bean
 * public ConfigurationCustomizer typeHandlerCustomizer(ObjectMapper objectMapper) {
 *     return configuration -> {
 *         configuration.getTypeHandlerRegistry()
 *             .register(JsonNode.class, new JsonToJsonNodeTypeHandler(objectMapper));
 *     };
 * }
 * }</pre>
 *
 * <h2>错误处理</h2>
 *
 * <ul>
 *   <li><b>序列化失败:</b> 抛出 {@code SQLException} 并包含详细错误信息
 *   <li><b>反序列化失败:</b> 抛出 {@code SQLException} 并记录原始 JSON 值
 *   <li><b>NULL 值:</b> 返回 Java null,不抛出异常
 *   <li><b>空字符串/空白字符串:</b> 返回 Java null,避免 JSON 解析错误
 * </ul>
 *
 * <h2>注意事项</h2>
 *
 * <ul>
 *   <li><b>ObjectMapper 配置:</b> 确保使用 Spring 管理的 ObjectMapper,避免配置不一致
 *   <li><b>PostgreSQL PGobject:</b> 自动识别 PostgreSQL 的 JSON/JSONB 类型,无需特殊配置
 *   <li><b>性能考虑:</b> JSON 字段适合存储小型配置对象,大型数据应考虑单独存储
 *   <li><b>索引限制:</b> JSON 列无法直接创建索引,如需查询应提取为单独字段
 * </ul>
 *
 * <h2>相关模块</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.mybatis.autoconfig.PatraMybatisAutoConfiguration} - 自动注册类型处理器
 *   <li>{@link com.patra.starter.mybatis.entity.BaseDO} - 提供 {@code recordRemarks} 字段示例
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.mybatis.type;
