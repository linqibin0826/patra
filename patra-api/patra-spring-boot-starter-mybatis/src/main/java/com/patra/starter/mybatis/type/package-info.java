/// MyBatis 自定义类型转换器包。
///
/// 本包提供 MyBatis 的 {@link org.apache.ibatis.type.TypeHandler} 实现,用于在 Java
/// 对象与数据库列类型之间进行自动转换,特别是处理复杂类型(如 JSON)的序列化和反序列化。
///
/// ## 职责
///
/// - 实现 Java 对象到数据库列的双向转换逻辑
///   - 支持多种数据库(MySQL、PostgreSQL)的 JSON 列类型
///   - 处理 NULL 值和空字符串边界情况
///   - 集成 Spring 管理的 {@link com.fasterxml.jackson.databind.ObjectMapper} 确保 JSON 配置一致性
///
/// ## 核心组件
///
/// - {@link com.patra.starter.mybatis.type.JsonToJsonNodeTypeHandler} - Jackson `JsonNode`
///       类型转换器
///   - {@link com.patra.starter.mybatis.type.JsonToMapTypeHandler} - `Map<String, Object>`
///       类型转换器
///
/// ## 设计决策
///
/// - **依赖注入 ObjectMapper:** 使用 Spring 管理的 Jackson 实例,避免全局配置不一致(如日期格式、空值处理等)
///   - **多数据库兼容:** 支持 MySQL 的 JSON/TEXT 列和 PostgreSQL 的 JSON/JSONB 列
///   - **灵活的读取逻辑:** 处理 String、CLOB、字节数组、PGobject 等多种 JDBC 表示形式
///   - **严格的 NULL 处理:** 将数据库 NULL、空字符串和空白字符串统一转换为 Java null,避免解析错误
///
/// ## 数据库兼容性
///
/// <table border="1">
///   <tr><th>数据库</th><th>列类型</th><th>JDBC 类型</th><th>支持情况</th></tr>
///   <tr><td>MySQL 5.7+</td><td>JSON</td><td>VARCHAR/LONGVARCHAR</td><td>✓ 完全支持</td></tr>
///   <tr><td>MySQL</td><td>TEXT/LONGTEXT</td><td>CLOB/LONGVARCHAR</td><td>✓ 完全支持</td></tr>
///   <tr><td>PostgreSQL</td><td>JSON</td><td>OTHER (PGobject)</td><td>✓ 完全支持</td></tr>
///   <tr><td>PostgreSQL</td><td>JSONB</td><td>OTHER (PGobject)</td><td>✓ 完全支持</td></tr>
///   <tr><td>H2/Oracle</td><td>CLOB</td><td>CLOB</td><td>✓ 完全支持</td></tr>
/// </table>
///
/// ## 使用示例
///
/// **实体类定义(使用 JsonNode):**
///
/// ```java
/// @Data
/// @EqualsAndHashCode(callSuper = true)
/// @TableName("provenance")
/// public class ProvenanceDO extends BaseDO {
///     @TableField(value = "config", typeHandler = JsonToJsonNodeTypeHandler.class)
///     private JsonNode config; // 数据库列为 JSON 或 TEXT 类型
/// ```
///
/// **实体类定义(使用 Map):**
///
/// ```java
/// @Data
/// @EqualsAndHashCode(callSuper = true)
/// @TableName("user_profile")
/// public class UserProfileDO extends BaseDO {
///     @TableField(value = "settings", typeHandler = JsonToMapTypeHandler.class)
///     private Map<String, Object> settings;
/// ```
///
/// **插入操作(自动序列化):**
///
/// ```java
/// ObjectMapper mapper = new ObjectMapper();
/// JsonNode config = mapper.createObjectNode()
///     .put("apiKey", "abc123")
///     .put("timeout", 5000);
///
/// ProvenanceDO provenance = ProvenanceDO.builder()
///     .name("PubMed")
///     .config(config) // 自动序列化为 JSON 字符串存入数据库
///     .build();
/// provenanceMapper.insert(provenance);
/// ```
///
/// **查询操作(自动反序列化):**
///
/// ```java
/// ProvenanceDO provenance = provenanceMapper.selectById(id);
/// JsonNode config = provenance.getConfig(); // 自动从 JSON 字符串反序列化
/// String apiKey = config.get("apiKey").asText();
/// ```
///
/// **全局注册 TypeHandler:**
///
/// ```java
/// @Bean
/// public ConfigurationCustomizer typeHandlerCustomizer(ObjectMapper objectMapper) {
///     return configuration -> {
///         configuration.getTypeHandlerRegistry()
///             .register(JsonNode.class, new JsonToJsonNodeTypeHandler(objectMapper));;
/// ```
///
/// ## 错误处理
///
/// - **序列化失败:** 抛出 `SQLException` 并包含详细错误信息
///   - **反序列化失败:** 抛出 `SQLException` 并记录原始 JSON 值
///   - **NULL 值:** 返回 Java null,不抛出异常
///   - **空字符串/空白字符串:** 返回 Java null,避免 JSON 解析错误
///
/// ## 注意事项
///
/// - **ObjectMapper 配置:** 确保使用 Spring 管理的 ObjectMapper,避免配置不一致
///   - **PostgreSQL PGobject:** 自动识别 PostgreSQL 的 JSON/JSONB 类型,无需特殊配置
///   - **性能考虑:** JSON 字段适合存储小型配置对象,大型数据应考虑单独存储
///   - **索引限制:** JSON 列无法直接创建索引,如需查询应提取为单独字段
///
/// ## 相关模块
///
/// - {@link com.patra.starter.mybatis.autoconfig.PatraMybatisAutoConfiguration} - 自动注册类型处理器
///   - {@link com.patra.starter.mybatis.entity.BaseDO} - 提供 `recordRemarks` 字段示例
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.mybatis.type;
