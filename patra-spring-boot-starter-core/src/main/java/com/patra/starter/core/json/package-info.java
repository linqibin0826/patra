/**
 * JSON 序列化全局访问桥接包。
 *
 * <p>本包提供对 Spring 管理的 {@link com.fasterxml.jackson.databind.ObjectMapper} 的全局访问能力,
 * 连接 Spring DI 上下文和非 Spring 代码路径(如静态工具类、共享库)。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>将 Spring 管理的 {@code ObjectMapper} 暴露给非 Spring 代码
 *   <li>确保整个应用使用统一配置的 JSON 序列化器
 *   <li>桥接 Spring 上下文和 {@link com.patra.common.json.JsonMapperHolder} 静态持有者
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.core.json.ObjectMapperProvider} - ObjectMapper 全局提供者
 *   <li>{@link com.patra.starter.core.json.JsonNodeSupport} - JsonNode 操作支持类
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>DI 优先</strong> - Spring 组件内部首选构造注入
 *   <li><strong>桥接设计</strong> - 仅作为 DI 不可行场景的桥梁(静态工具、共享库)
 *   <li><strong>配置统一</strong> - 确保 Spring 和非 Spring 代码共享相同的 Jackson 配置
 * </ul>
 *
 * <h2>使用指南</h2>
 *
 * <h3>Spring 组件(推荐方式)</h3>
 * <pre>{@code
 * @Service
 * public class MyService {
 *     private final ObjectMapper objectMapper;
 *
 *     public MyService(ObjectMapper objectMapper) {  // 构造注入
 *         this.objectMapper = objectMapper;
 *     }
 *
 *     public String serialize(MyObject obj) throws JsonProcessingException {
 *         return objectMapper.writeValueAsString(obj);
 *     }
 * }
 * }</pre>
 *
 * <h3>非 Spring 代码(桥接方式)</h3>
 * <pre>{@code
 * public class StaticJsonUtils {
 *     public static String toJson(Object obj) {
 *         // 通过 JsonMapperHolder 访问 Spring 管理的 ObjectMapper
 *         return JsonMapperHolder.getObjectMapper().writeValueAsString(obj);
 *     }
 * }
 * }</pre>
 *
 * <h2>生命周期</h2>
 *
 * <ol>
 *   <li>Spring 启动时,{@code ObjectMapperProvider} 获取容器管理的 {@code ObjectMapper}
 *   <li>将其注册到 {@link com.patra.common.json.JsonMapperHolder} 静态持有者
 *   <li>非 Spring 代码通过 {@code JsonMapperHolder} 访问相同的实例
 * </ol>
 *
 * @since 0.1.0
 * @author linqibin
 * @see com.patra.common.json.JsonMapperHolder
 */
package com.patra.starter.core.json;
