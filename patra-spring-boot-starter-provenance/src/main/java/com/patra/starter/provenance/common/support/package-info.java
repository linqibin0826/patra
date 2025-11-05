/**
 * Provenance 通用支持工具包。
 *
 * <p>提供 JSON 处理、字符串操作等通用工具类。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>JSON 序列化和反序列化辅助方法
 *   <li>字符串处理和验证工具
 *   <li>通用数据结构操作
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link JsonHelpers} - JSON 处理辅助类
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // JSON 转对象
 * ESearchResponse response = JsonHelpers.parse(json, ESearchResponse.class);
 *
 * // 对象转 JSON
 * String json = JsonHelpers.toJson(response);
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.common.support;
