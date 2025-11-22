/// JSON 序列化全局访问桥接包。
///
/// 本包提供对 Spring 管理的 {@link com.fasterxml.jackson.databind.ObjectMapper} 的全局访问能力, 连接 Spring DI
/// 上下文和非 Spring 代码路径(如静态工具类、共享库)。
///
/// ## 职责
///
/// - 将 Spring 管理的 `ObjectMapper` 暴露给非 Spring 代码
///   - 确保整个应用使用统一配置的 JSON 序列化器
///   - 桥接 Spring 上下文和 {@link com.patra.common.json.JsonMapperHolder} 静态持有者
///
/// ## 核心组件
///
/// - {@link com.patra.starter.core.json.ObjectMapperProvider} - ObjectMapper 全局提供者
///   - {@link com.patra.starter.core.json.JsonNodeSupport} - JsonNode 操作支持类
///
/// ## 设计原则
///
/// - **DI 优先** - Spring 组件内部首选构造注入
///   - **桥接设计** - 仅作为 DI 不可行场景的桥梁(静态工具、共享库)
///   - **配置统一** - 确保 Spring 和非 Spring 代码共享相同的 Jackson 配置
///
/// ## 使用指南
///
/// ### Spring 组件(推荐方式)
///
/// ```java
/// @Service
/// public class MyService {
///     private final ObjectMapper objectMapper;
///
///     public MyService(ObjectMapper objectMapper) {  // 构造注入
///         this.objectMapper = objectMapper;
///
///     public String serialize(MyObject obj) throws JsonProcessingException {
///         return objectMapper.writeValueAsString(obj);
/// ```
///
/// ### 非 Spring 代码(桥接方式)
///
/// ```java
/// public class StaticJsonUtils {
///     public static String toJson(Object obj) {
///         // 通过 JsonMapperHolder 访问 Spring 管理的 ObjectMapper
///         return JsonMapperHolder.getObjectMapper().writeValueAsString(obj);
/// ```
///
/// ## 生命周期
///
/// @since 0.1.0
/// @author linqibin
/// @see com.patra.common.json.JsonMapperHolder
package com.patra.starter.core.json;
