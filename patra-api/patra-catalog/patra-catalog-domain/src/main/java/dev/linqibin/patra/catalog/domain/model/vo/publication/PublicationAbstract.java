package dev.linqibin.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.util.StrUtil;
import dev.linqibin.patra.catalog.domain.model.enums.AbstractType;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import lombok.Builder;

/// 文献摘要值对象。
///
/// 封装文献的摘要信息，支持结构化和非结构化两种形式。
///
/// **摘要类型**：
///
/// - **STRUCTURED**：结构化摘要，含多个命名段落（BACKGROUND、METHODS、RESULTS、CONCLUSIONS）
/// - **UNSTRUCTURED**：非结构化摘要，纯文本段落
/// - **GRAPHICAL**：图形化摘要（通常为图片，此处仅存储描述）
/// - **NONE**：无摘要
///
/// **聚合边界内管理**：
///
/// - 摘要是 Publication 聚合的嵌入式值对象
/// - 与主文献 1:1 关系
/// - 通过 `PublicationAggregate.updateAbstract()` 更新
///
/// **结构化摘要段落标准命名**：
///
/// - BACKGROUND / INTRODUCTION - 背景/引言
/// - OBJECTIVE / AIM / PURPOSE - 目的
/// - METHODS / MATERIALS - 方法/材料
/// - RESULTS / FINDINGS - 结果
/// - CONCLUSIONS / SUMMARY - 结论
/// - DISCUSSION - 讨论
///
/// 使用示例：
///
/// ```java
/// // 创建纯文本摘要
/// PublicationAbstract abs1 = PublicationAbstract.ofPlainText("This study examines...");
///
/// // 创建结构化摘要
/// Map<String, String> sections = Map.of(
///     "BACKGROUND", "Cancer is...",
///     "METHODS", "We conducted...",
///     "RESULTS", "The study found...",
///     "CONCLUSIONS", "Our findings suggest..."
/// );
/// PublicationAbstract abs2 = PublicationAbstract.ofStructured(sections);
///
/// // 获取特定段落
/// Optional<String> methods = abs2.getSection("METHODS");
///
/// // 创建空摘要
/// PublicationAbstract empty = PublicationAbstract.empty();
/// ```
///
/// @param plainText 纯文本摘要（非结构化摘要的全文）
/// @param structuredSections 结构化摘要段落（段落名 → 段落内容）
/// @param copyright 版权信息/使用限制
/// @param abstractType 摘要类型
/// @author linqibin
/// @since 0.1.0
@Builder(toBuilder = true)
public record PublicationAbstract(
    String plainText,
    Map<String, String> structuredSections,
    String copyright,
    AbstractType abstractType)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：处理防御性拷贝和默认值。
  public PublicationAbstract {
    // 防御性拷贝：确保 structuredSections 不可变
    structuredSections = structuredSections != null ? Map.copyOf(structuredSections) : Map.of();

    // 推断摘要类型（如果未指定）
    if (abstractType == null) {
      if (!structuredSections.isEmpty()) {
        abstractType = AbstractType.STRUCTURED;
      } else if (StrUtil.isNotBlank(plainText)) {
        abstractType = AbstractType.UNSTRUCTURED;
      } else {
        abstractType = AbstractType.NONE;
      }
    }
  }

  /// 创建纯文本摘要。
  ///
  /// @param plainText 纯文本内容
  /// @return 非结构化摘要
  public static PublicationAbstract ofPlainText(String plainText) {
    return new PublicationAbstract(plainText, null, null, AbstractType.UNSTRUCTURED);
  }

  /// 创建带版权信息的纯文本摘要。
  ///
  /// @param plainText 纯文本内容
  /// @param copyright 版权信息
  /// @return 非结构化摘要
  public static PublicationAbstract ofPlainText(String plainText, String copyright) {
    return new PublicationAbstract(plainText, null, copyright, AbstractType.UNSTRUCTURED);
  }

  /// 创建结构化摘要。
  ///
  /// @param sections 段落映射（段落名 → 段落内容）
  /// @return 结构化摘要
  public static PublicationAbstract ofStructured(Map<String, String> sections) {
    return new PublicationAbstract(null, sections, null, AbstractType.STRUCTURED);
  }

  /// 创建带版权信息的结构化摘要。
  ///
  /// @param sections 段落映射
  /// @param copyright 版权信息
  /// @return 结构化摘要
  public static PublicationAbstract ofStructured(Map<String, String> sections, String copyright) {
    return new PublicationAbstract(null, sections, copyright, AbstractType.STRUCTURED);
  }

  /// 创建同时包含纯文本和结构化段落的摘要。
  ///
  /// 某些数据源同时提供两种格式。
  ///
  /// @param plainText 纯文本内容
  /// @param sections 结构化段落
  /// @param copyright 版权信息
  /// @return 结构化摘要（优先标记为结构化）
  public static PublicationAbstract ofBoth(
      String plainText, Map<String, String> sections, String copyright) {
    return new PublicationAbstract(plainText, sections, copyright, AbstractType.STRUCTURED);
  }

  /// 创建空摘要。
  ///
  /// @return 无内容的摘要
  public static PublicationAbstract empty() {
    return new PublicationAbstract(null, null, null, AbstractType.NONE);
  }

  /// 判断是否有摘要内容。
  ///
  /// @return true 如果有纯文本或结构化段落
  public boolean hasContent() {
    return StrUtil.isNotBlank(plainText) || !structuredSections.isEmpty();
  }

  /// 判断是否为结构化摘要。
  ///
  /// @return true 如果摘要类型为 STRUCTURED
  public boolean isStructured() {
    return abstractType == AbstractType.STRUCTURED;
  }

  /// 判断是否有版权信息。
  ///
  /// @return true 如果 copyright 不为空
  public boolean hasCopyright() {
    return StrUtil.isNotBlank(copyright);
  }

  /// 获取指定段落的内容。
  ///
  /// @param sectionName 段落名称（如 "METHODS"、"RESULTS"）
  /// @return 段落内容（如果存在）
  public Optional<String> getSection(String sectionName) {
    return Optional.ofNullable(structuredSections.get(sectionName));
  }

  /// 获取指定段落的内容（不区分大小写）。
  ///
  /// @param sectionName 段落名称
  /// @return 段落内容（如果存在）
  public Optional<String> getSectionIgnoreCase(String sectionName) {
    if (sectionName == null) {
      return Optional.empty();
    }
    String upperName = sectionName.toUpperCase(Locale.ROOT);
    return structuredSections.entrySet().stream()
        .filter(e -> e.getKey().toUpperCase(Locale.ROOT).equals(upperName))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  /// 判断是否包含指定段落。
  ///
  /// @param sectionName 段落名称
  /// @return true 如果包含该段落
  public boolean hasSection(String sectionName) {
    return structuredSections.containsKey(sectionName);
  }

  /// 获取所有段落名称。
  ///
  /// @return 段落名称集合（不可变）
  public java.util.Set<String> getSectionNames() {
    return structuredSections.keySet();
  }

  /// 获取段落数量。
  ///
  /// @return 结构化段落的数量
  public int getSectionCount() {
    return structuredSections.size();
  }

  /// 获取摘要的完整文本。
  ///
  /// 对于结构化摘要，将所有段落按顺序拼接。
  ///
  /// @return 完整的摘要文本
  public String getFullText() {
    if (StrUtil.isNotBlank(plainText)) {
      return plainText;
    }
    if (!structuredSections.isEmpty()) {
      return String.join(" ", structuredSections.values());
    }
    return "";
  }

  /// 获取摘要文本长度。
  ///
  /// @return 完整文本的字符数
  public int getTextLength() {
    return getFullText().length();
  }
}
