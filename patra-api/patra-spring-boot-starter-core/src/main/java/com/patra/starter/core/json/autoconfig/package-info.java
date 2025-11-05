/**
 * JSON 序列化自动配置包。
 *
 * <p>本包提供基于 Jackson 的 JSON 序列化器自动配置,确保整个平台使用统一的序列化规则。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>配置 Spring 管理的 {@link com.fasterxml.jackson.databind.ObjectMapper}
 *   <li>注册 {@link com.patra.starter.core.json.ObjectMapperProvider} 桥接 Spring 和非 Spring 代码
 *   <li>应用全局 Jackson 配置(日期格式、空值处理、未知属性等)
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.core.json.autoconfig.JacksonAutoConfiguration} - Jackson ObjectMapper 自动配置类
 * </ul>
 *
 * <h2>配置能力</h2>
 *
 * <ul>
 *   <li><strong>日期格式</strong> - 统一使用 ISO-8601 格式,禁用时间戳形式
 *   <li><strong>空值处理</strong> - 配置 null 值序列化策略(默认忽略)
 *   <li><strong>未知属性</strong> - 配置反序列化时遇到未知字段的处理方式
 *   <li><strong>命名策略</strong> - 统一字段命名策略(camelCase/snake_case)
 * </ul>
 *
 * <h2>Spring Boot 集成</h2>
 *
 * <p>本自动配置遵循 Spring Boot 约定,支持通过 {@code spring.jackson.*} 属性进行配置:
 *
 * <pre>{@code
 * spring:
 *   jackson:
 *     default-property-inclusion: non_null        # 忽略 null 值
 *     serialization:
 *       write-dates-as-timestamps: false          # 使用 ISO-8601 日期格式
 *     deserialization:
 *       fail-on-unknown-properties: true          # 未知属性抛异常
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 自动配置生效后,直接注入使用
 * @RestController
 * public class ApiController {
 *     private final ObjectMapper objectMapper;  // Spring 管理的实例
 *
 *     @PostMapping("/data")
 *     public ResponseEntity<String> handleData(@RequestBody MyData data) {
 *         // 自动使用统一的序列化配置
 *         String json = objectMapper.writeValueAsString(data);
 *         return ResponseEntity.ok(json);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.core.json.autoconfig;
