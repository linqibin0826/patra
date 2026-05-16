package dev.linqibin.patra.catalog.domain.port.registry;

import dev.linqibin.patra.catalog.domain.model.enums.DictionaryType;
import dev.linqibin.patra.catalog.domain.model.vo.common.SourceStandard;
import java.util.Map;
import java.util.Set;

/// 字典解析端口。
///
/// 提供将原始字典值解析为标准代码的通用能力。通过 patra-registry 服务的字典解析 API 实现。
///
/// **设计原则**：
///
/// - **通用化**：一个端口支持所有字典类型（国家、语言、学科等）
/// - **类型安全**：使用 DictionaryType 枚举和 SourceStandard 值对象避免字符串魔法值
/// - **显式指定**：调用方必须显式指定来源标准，避免隐式依赖
/// - **容错性**：解析失败不抛出异常，返回空 Map 或部分结果
///
/// **使用场景**：
///
/// 1. **国家编码标准化**：PubMed LSIOU 国家英文全名 → ISO 3166-1 alpha-2
/// 2. **语言编码转换**：ISO 639-3 三字母代码 → BCP 47 语言标签
/// 3. **学科分类映射**：MeSH 主题词 → 内部统一分类
///
/// **错误处理策略**：
///
/// - 远程服务不可用时返回空 Map，不影响主流程
/// - 单个值解析失败时，该值不在结果中
/// - 不抛出异常，因为字典解析通常是可选的增强操作
///
/// **使用示例**：
///
/// ```java
/// // 场景 1：解析国家编码（PubMed LSIOU 使用英文全名）
/// Set<String> rawCountries = Set.of("China", "United States", "Japan");
/// Map<String, String> countryMap = dictionaryResolver.resolve(
///     DictionaryType.COUNTRY,
///     SourceStandard.NAME_EN,
///     rawCountries
/// );
/// // 结果: {"China": "CN", "United States": "US", "Japan": "JP"}
///
/// // 场景 2：解析语言编码（PubMed 使用 ISO 639-3）
/// Set<String> rawLanguages = Set.of("eng", "chi", "jpn");
/// Map<String, String> languageMap = dictionaryResolver.resolve(
///     DictionaryType.LANGUAGE,
///     SourceStandard.ISO_639_3,
///     rawLanguages
/// );
/// // 结果: {"eng": "en", "chi": "zh", "jpn": "ja"}
/// ```
///
/// @author linqibin
/// @since 0.1.0
public interface DictionaryResolverPort {

  /// 批量解析字典值。
  ///
  /// 将原始字典值集合解析为系统规范标准代码。调用方必须显式指定来源标准，
  /// 以表明原始数据采用的编码格式。
  ///
  /// @param dictionaryType 字典类型（如 COUNTRY、LANGUAGE）
  /// @param sourceStandard 来源标准（如 NAME_EN、ISO_639_3）
  /// @param rawValues 原始值集合
  /// @return 原始值 → 标准代码的映射，解析失败的值不在结果中；
  ///         如果 rawValues 为空或 null，返回空 Map
  Map<String, String> resolve(
      DictionaryType dictionaryType, SourceStandard sourceStandard, Set<String> rawValues);
}
