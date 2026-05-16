package dev.linqibin.patra.catalog.domain.port.lookup;

import java.util.Map;
import java.util.Set;

/// 语言查找端口。
///
/// 提供语言代码解析能力，将外部数据源的语言代码（如 PubMed 的 ISO 639-3）
/// 转换为项目标准代码（BCP 47）。
///
/// **设计原则**：
///
/// - **批量优先**：主接口为批量解析，支持 Chunk 级别调用
/// - **容错性**：无法解析的语言返回默认占位符 "unknown"
/// - **可扩展**：实现层可选择不同缓存策略（无缓存、Step 级别、应用级别等）
///
/// **使用场景**：
///
/// - API 单次查询（无缓存实现）
/// - 批量数据导入（Step 级别缓存实现）
/// - 事件处理（无缓存实现）
///
/// **解析示例**：
///
/// ```java
/// Set<String> iso639Codes = Set.of("eng", "chi", "jpn", "unknowncode");
/// Map<String, String> result = languageLookup.resolve(iso639Codes);
/// // 结果: {"eng": "en", "chi": "zh", "jpn": "ja", "unknowncode": "unknown"}
/// ```
///
/// @author linqibin
/// @since 0.1.0
public interface LanguageLookupPort {

  /// 无法解析语言时的默认占位符。
  String UNKNOWN_LANGUAGE = "unknown";

  /// 批量解析语言代码。
  ///
  /// 将外部数据源语言代码（ISO 639-3 三字母代码）转换为项目标准代码（BCP 47）。
  ///
  /// @param iso639Codes ISO 639-3 语言代码集合（如 "eng", "chi", "jpn"）
  /// @return 原始代码 → 标准代码的映射；无法解析的代码映射为 "unknown"；
  ///         如果 iso639Codes 为空或 null，返回空 Map
  Map<String, String> resolve(Set<String> iso639Codes);

  /// 解析单个语言代码。
  ///
  /// 便捷方法，内部调用批量接口。
  ///
  /// @param iso639Code ISO 639-3 语言代码（如 "eng"）
  /// @return 标准代码（BCP 47），如 "en"；无法解析时返回 "unknown"
  default String resolve(String iso639Code) {
    if (iso639Code == null || iso639Code.isBlank()) {
      return UNKNOWN_LANGUAGE;
    }
    return resolve(Set.of(iso639Code)).getOrDefault(iso639Code, UNKNOWN_LANGUAGE);
  }
}
