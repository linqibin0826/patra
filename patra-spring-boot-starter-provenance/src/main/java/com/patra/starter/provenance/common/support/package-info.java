/// Provenance 通用支持工具包。
/// 
/// 提供 JSON 处理、字符串操作等通用工具类。
/// 
/// ## 职责
/// 
/// - JSON 序列化和反序列化辅助方法
///   - 字符串处理和验证工具
///   - 通用数据结构操作
/// 
/// ## 核心组件
/// 
/// - {@link JsonHelpers} - JSON 处理辅助类
/// 
/// ## 使用示例
/// 
/// ```java
/// // JSON 转对象
/// ESearchResponse response = JsonHelpers.parse(json, ESearchResponse.class);
/// 
/// // 对象转 JSON
/// String json = JsonHelpers.toJson(response);
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.common.support;
