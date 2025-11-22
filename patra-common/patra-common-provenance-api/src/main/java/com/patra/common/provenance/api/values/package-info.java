/// Provenance API 参数值枚举
/// 
/// 提供类型安全的 API 参数值枚举，替代魔法字符串，支持编译时检查和 IDE 自动补全。
/// 
/// ## 职责
/// 
/// - 定义 API 参数的所有可选值（如格式、类型、模式等）
///   - 提供类型安全的枚举替代字符串常量
///   - 支持字符串解析和 Jackson 序列化/反序列化
///   - 提供便捷的转换方法（如布尔值 ↔ 枚举）
/// 
/// ## 子包组织
/// 
/// - {@link com.patra.common.provenance.api.values.pubmed} - PubMed API 参数值枚举
///   - {@link com.patra.common.provenance.api.values.epmc} - EPMC API 参数值枚举
/// 
/// ## 使用示例
/// 
/// ```java
/// import com.patra.common.provenance.api.values.pubmed.*;
/// 
/// // ❌ Before: 魔法字符串
/// params.put("retmode", "json");  // 可能拼写错误
/// if (retmode.equals("xml")) { ...
/// 
/// // ✅ After: 类型安全枚举
/// params.put("retmode", RetMode.JSON.value());  // 编译时检查
/// if (request.retmode() == RetMode.XML) { ...  // null-safe
/// 
/// // 从字符串解析
/// RetMode mode = RetMode.fromString("json");
/// 
/// // 安全解析（失败返回默认值）
/// RetMode mode = RetMode.fromStringOrDefault(input, RetMode.JSON);
/// ```
/// 
/// ## 设计原则
/// 
/// 所有枚举遵循统一的设计模式：
/// 
/// - ✅ **value() 方法** - 返回 API 参数字符串值，标注 `@JsonValue`
///   - ✅ **fromString() 方法** - 从字符串解析（严格模式，失败抛异常）
///   - ✅ **fromStringOrDefault() 方法** - 安全解析（失败返回默认值）
///   - ✅ **toString() 重写** - 返回可读的字符串值
///   - ✅ **完整 JavaDoc** - 类级和字段级文档
/// 
/// ## 优势
/// 
/// - **编译时检查** - 拼写错误编译失败，避免运行时错误
///   - **IDE 支持** - 自动补全、参数提示、重构安全
///   - **类型安全** - 枚举比较，避免字符串比较的陷阱
///   - **自解释** - 枚举名称即文档，无需查 API 文档
///   - **可扩展** - 新增可选值只需添加枚举项
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.common.provenance.api.values;
