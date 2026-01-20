package com.patra.catalog.infra.adapter.lookup;

import com.patra.catalog.domain.model.enums.DictionaryType;
import com.patra.catalog.domain.model.vo.common.SourceStandard;
import com.patra.catalog.domain.port.lookup.LanguageLookupPort;
import com.patra.catalog.domain.port.registry.DictionaryResolverPort;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/// 默认语言查找适配器（无缓存）。
///
/// 直接调用 DictionaryResolverPort 解析语言代码，适用于：
///
/// - API 单次查询
/// - 事件处理
/// - 低频查询场景
///
/// **解析策略**：
///
/// 1. 优先使用 patra-registry 字典服务解析
/// 2. Registry 未解析的代码使用静态 fallback 映射
/// 3. 完全未知的代码返回 "unknown"
///
/// **Fallback 映射**：覆盖 PubMed 最常见的 50+ 种语言代码。
///
/// 作为 `@Primary` 实现，在未指定 `@Qualifier` 时默认注入此实现。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DefaultLanguageLookupAdapter implements LanguageLookupPort {

  private final DictionaryResolverPort dictionaryResolverPort;

  /// 静态 fallback 映射：ISO 639-3 → BCP 47 基础代码。
  ///
  /// 覆盖 PubMed 数据中最常见的语言代码，作为 Registry 不可用时的备选。
  private static final Map<String, String> FALLBACK_MAPPING =
      Map.ofEntries(
          // 最常见语言（PubMed 数据 Top 20）
          Map.entry("eng", "en"), // English
          Map.entry("chi", "zh"), // Chinese
          Map.entry("jpn", "ja"), // Japanese
          Map.entry("ger", "de"), // German
          Map.entry("fre", "fr"), // French
          Map.entry("spa", "es"), // Spanish
          Map.entry("ita", "it"), // Italian
          Map.entry("por", "pt"), // Portuguese
          Map.entry("rus", "ru"), // Russian
          Map.entry("kor", "ko"), // Korean
          Map.entry("pol", "pl"), // Polish
          Map.entry("dut", "nl"), // Dutch
          Map.entry("tur", "tr"), // Turkish
          Map.entry("ukr", "uk"), // Ukrainian
          Map.entry("cze", "cs"), // Czech
          Map.entry("dan", "da"), // Danish
          Map.entry("fin", "fi"), // Finnish
          Map.entry("gre", "el"), // Greek
          Map.entry("hun", "hu"), // Hungarian
          Map.entry("nor", "no"), // Norwegian

          // 常见语言（扩展）
          Map.entry("ara", "ar"), // Arabic
          Map.entry("heb", "he"), // Hebrew
          Map.entry("hin", "hi"), // Hindi
          Map.entry("ind", "id"), // Indonesian
          Map.entry("may", "ms"), // Malay
          Map.entry("per", "fa"), // Persian
          Map.entry("tha", "th"), // Thai
          Map.entry("vie", "vi"), // Vietnamese
          Map.entry("swe", "sv"), // Swedish
          Map.entry("rum", "ro"), // Romanian
          Map.entry("bul", "bg"), // Bulgarian
          Map.entry("cat", "ca"), // Catalan
          Map.entry("hrv", "hr"), // Croatian
          Map.entry("slv", "sl"), // Slovenian
          Map.entry("srp", "sr"), // Serbian
          Map.entry("slo", "sk"), // Slovak
          Map.entry("lit", "lt"), // Lithuanian
          Map.entry("lav", "lv"), // Latvian
          Map.entry("est", "et"), // Estonian
          Map.entry("ice", "is"), // Icelandic

          // 其他语言
          Map.entry("afr", "af"), // Afrikaans
          Map.entry("alb", "sq"), // Albanian
          Map.entry("arm", "hy"), // Armenian
          Map.entry("baq", "eu"), // Basque
          Map.entry("bos", "bs"), // Bosnian
          Map.entry("geo", "ka"), // Georgian
          Map.entry("mac", "mk"), // Macedonian
          Map.entry("mlt", "mt"), // Maltese
          Map.entry("wel", "cy"), // Welsh
          Map.entry("gle", "ga") // Irish
          );

  @Override
  public Map<String, String> resolve(Set<String> iso639Codes) {
    if (iso639Codes == null || iso639Codes.isEmpty()) {
      return Map.of();
    }

    Map<String, String> result = new HashMap<>();

    // 1. 尝试 Registry 解析
    Map<String, String> registryResult =
        dictionaryResolverPort.resolve(
            DictionaryType.LANGUAGE, SourceStandard.ISO_639_3, iso639Codes);

    // 2. 合并 Registry 结果
    result.putAll(registryResult);

    // 3. 对未解析的代码使用 fallback 或返回 unknown
    for (String code : iso639Codes) {
      if (!result.containsKey(code)) {
        String fallback = FALLBACK_MAPPING.get(code);
        result.put(code, fallback != null ? fallback : UNKNOWN_LANGUAGE);
      }
    }

    return result;
  }
}
