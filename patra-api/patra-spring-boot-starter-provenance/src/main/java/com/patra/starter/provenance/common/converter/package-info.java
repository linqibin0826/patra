/**
 * Provenance 通用转换器包。
 *
 * <p>提供配置转换、格式转换等通用工具。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>配置对象转换和合并
 *   <li>XML 到 JSON 格式转换
 *   <li>数据结构映射和转换
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link ProvenanceConfigConverter} - Provenance 配置转换器
 *   <li>{@link XmlToJsonConverter} - XML 到 JSON 转换器
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // XML 转 JSON
 * XmlToJsonConverter converter = new XmlToJsonConverter(objectMapper);
 * String json = converter.convert(xmlString);
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.common.converter;
