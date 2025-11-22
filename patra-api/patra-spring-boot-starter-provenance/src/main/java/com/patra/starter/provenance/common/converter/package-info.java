/// Provenance 通用转换器包。
///
/// 提供格式转换等通用工具。
///
/// ## 职责
///
/// - XML 到 JSON 格式转换
///   - 数据结构映射和转换
///
/// ## 核心组件
///
/// - {@link XmlToJsonConverter} - XML 到 JSON 转换器
///
/// ## 使用示例
///
/// ```java
/// // XML 转 JSON
/// XmlToJsonConverter converter = new XmlToJsonConverter(objectMapper);
/// String json = converter.convert(xmlString);
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.common.converter;
