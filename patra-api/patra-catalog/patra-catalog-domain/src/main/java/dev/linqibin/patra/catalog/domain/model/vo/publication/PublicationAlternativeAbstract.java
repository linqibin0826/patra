package dev.linqibin.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import dev.linqibin.patra.catalog.domain.model.enums.QualityLevel;
import dev.linqibin.patra.catalog.domain.model.enums.TranslationType;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;

/// 文献翻译摘要值对象。
///
/// 封装文献摘要的多语言翻译版本（官方翻译、专业翻译、机器翻译等）。
///
/// **补充数据（聚合边界外）**：
///
/// - 翻译摘要通过 Repository 独立管理
/// - 一个文献可有多个不同语言的翻译摘要
/// - 使用 `PublicationRepository.replaceAlternativeAbstractsBatch()` 批量替换
///
/// **唯一性约束**：
///
/// - 同一文献 + 同一语言 + 同一来源类型只能有一个翻译摘要
///
/// **翻译类型优先级**：
///
/// - OFFICIAL > PROFESSIONAL > COMMUNITY > MACHINE
///
/// 使用示例：
///
/// ```java
/// // 创建官方中文翻译摘要
/// PublicationAlternativeAbstract chinese = PublicationAlternativeAbstract.builder()
///     .languageCode("zh-CN")
///     .languageName("Chinese")
///     .plainText("本研究探讨了...")
///     .translationType(TranslationType.OFFICIAL)
///     .isOfficial(true)
///     .qualityLevel(QualityLevel.EXCELLENT)
///     .build();
///
/// // 创建机器翻译日文摘要
/// PublicationAlternativeAbstract japanese = PublicationAlternativeAbstract.ofMachine(
///     "ja", "Japanese", "この研究では...");
/// ```
///
/// @param languageCode 语言代码（ISO 639-1，如 "zh-CN"、"ja"）
/// @param languageName 语言名称（如 "Chinese"、"Japanese"）
/// @param plainText 纯文本摘要
/// @param structuredSections 结构化摘要段落
/// @param sourceType 摘要来源类型（如 `publisher`、`plain-language-summary`）
/// @param translationType 翻译类型
/// @param translator 译者姓名或机构
/// @param translationDate 翻译日期
/// @param qualityLevel 质量级别
/// @param isOfficial 是否官方翻译
/// @param orderNum 顺序号
/// @author linqibin
/// @since 0.1.0
@Builder(toBuilder = true)
public record PublicationAlternativeAbstract(
    String languageCode,
    String languageName,
    String plainText,
    Map<String, String> structuredSections,
    String sourceType,
    TranslationType translationType,
    String translator,
    LocalDate translationDate,
    QualityLevel qualityLevel,
    boolean isOfficial,
    Integer orderNum)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;
  private static final String UNKNOWN_SOURCE_TYPE = "unknown";

  /// 紧凑构造器：验证必填字段并处理防御性拷贝。
  ///
  /// @throws IllegalArgumentException 如果语言代码为空
  public PublicationAlternativeAbstract {
    Assert.notBlank(languageCode, "语言代码不能为空");

    // 防御性拷贝：确保 structuredSections 不可变
    structuredSections = structuredSections != null ? Map.copyOf(structuredSections) : Map.of();

    // 规范化来源类型（空值回退为 unknown）。
    sourceType = normalizeSourceType(sourceType);

    // 如果标记为官方翻译，确保翻译类型一致
    if (isOfficial && translationType != TranslationType.OFFICIAL) {
      translationType = TranslationType.OFFICIAL;
    }
  }

  /// 创建官方翻译摘要。
  ///
  /// @param languageCode 语言代码
  /// @param languageName 语言名称
  /// @param plainText 摘要文本
  /// @return 官方翻译摘要
  public static PublicationAlternativeAbstract ofOfficial(
      String languageCode, String languageName, String plainText) {
    return PublicationAlternativeAbstract.builder()
        .languageCode(languageCode)
        .languageName(languageName)
        .plainText(plainText)
        .sourceType("publisher")
        .translationType(TranslationType.OFFICIAL)
        .isOfficial(true)
        .build();
  }

  /// 创建专业翻译摘要。
  ///
  /// @param languageCode 语言代码
  /// @param languageName 语言名称
  /// @param plainText 摘要文本
  /// @param translator 译者
  /// @return 专业翻译摘要
  public static PublicationAlternativeAbstract ofProfessional(
      String languageCode, String languageName, String plainText, String translator) {
    return PublicationAlternativeAbstract.builder()
        .languageCode(languageCode)
        .languageName(languageName)
        .plainText(plainText)
        .sourceType("professional")
        .translationType(TranslationType.PROFESSIONAL)
        .translator(translator)
        .build();
  }

  /// 创建机器翻译摘要。
  ///
  /// @param languageCode 语言代码
  /// @param languageName 语言名称
  /// @param plainText 摘要文本
  /// @return 机器翻译摘要
  public static PublicationAlternativeAbstract ofMachine(
      String languageCode, String languageName, String plainText) {
    return PublicationAlternativeAbstract.builder()
        .languageCode(languageCode)
        .languageName(languageName)
        .plainText(plainText)
        .sourceType("machine")
        .translationType(TranslationType.MACHINE)
        .qualityLevel(QualityLevel.FAIR)
        .build();
  }

  /// 判断是否有摘要内容。
  ///
  /// @return true 如果有纯文本或结构化段落
  public boolean hasContent() {
    return StrUtil.isNotBlank(plainText) || !structuredSections.isEmpty();
  }

  /// 判断是否有结构化段落。
  ///
  /// @return true 如果有结构化摘要
  public boolean hasStructuredSections() {
    return !structuredSections.isEmpty();
  }

  /// 判断是否为人工翻译。
  ///
  /// @return true 如果不是机器翻译
  public boolean isHumanTranslated() {
    return translationType != null && translationType.isHumanTranslated();
  }

  /// 判断是否有译者信息。
  ///
  /// @return true 如果 translator 不为空
  public boolean hasTranslator() {
    return StrUtil.isNotBlank(translator);
  }

  /// 判断质量是否可接受。
  ///
  /// @return true 如果质量级别为 FAIR 及以上
  public boolean isQualityAcceptable() {
    return qualityLevel != null && qualityLevel.isAcceptable();
  }

  /// 获取指定段落的内容。
  ///
  /// @param sectionName 段落名称
  /// @return 段落内容（如果存在）
  public Optional<String> getSection(String sectionName) {
    return Optional.ofNullable(structuredSections.get(sectionName));
  }

  /// 获取完整的翻译文本。
  ///
  /// @return 完整文本（纯文本或结构化段落拼接）
  public String getFullText() {
    if (StrUtil.isNotBlank(plainText)) {
      return plainText;
    }
    if (!structuredSections.isEmpty()) {
      return String.join(" ", structuredSections.values());
    }
    return "";
  }

  /// 获取显示文本。
  ///
  /// @return 语言和翻译类型信息
  public String toDisplayString() {
    String lang = StrUtil.isNotBlank(languageName) ? languageName : languageCode;
    String type = translationType != null ? translationType.getDescription() : "Translation";
    return String.format("%s (%s)", lang, type);
  }

  /// 规范化摘要来源类型。
  private static String normalizeSourceType(String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN_SOURCE_TYPE;
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
