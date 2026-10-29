package dev.patra.catalog.domain.publication.model.vo;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import dev.patra.catalog.domain.publication.model.enums.AbstractType;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.Builder;

/// 文献摘要值对象。
///
/// 封装文献的摘要信息，支持结构化和非结构化两种形式。
///
/// **摘要类型**：
///
/// - **STRUCTURED**：结构化摘要，含多个带标签段落（BACKGROUND、METHODS、RESULTS、CONCLUSIONS）
/// - **UNSTRUCTURED**：非结构化摘要，纯文本段落
/// - **GRAPHICAL**：图形化摘要（通常为图片，此处仅存储描述）
/// - **NONE**：无摘要
///
/// **段落形态**：
///
/// - 段落是有序列表 `List<PublicationAbstractSection>`，而非 Map——保 section 顺序、
///   容纳重复 label 与无 label 段（混合形态的无标签段以 `label=null` 保位）
/// - 本层判型只看段落列表：非空 → STRUCTURED；为空且 plainText 非空 → UNSTRUCTURED；
///   否则 NONE。本层不扫描 label
/// - 「全无 label 视为非结构化摘要、段落不入库」是上游导入 Processor 的分流规则，
///   传入本层的段落列表已是导入侧决定要持久化的最终形态
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
/// List<PublicationAbstractSection> sections = List.of(
///     PublicationAbstractSection.of("BACKGROUND", "Cancer is..."),
///     PublicationAbstractSection.of("METHODS", "We conducted..."),
///     PublicationAbstractSection.of("RESULTS", "The study found..."),
///     PublicationAbstractSection.of("CONCLUSIONS", "Our findings suggest...")
/// );
/// PublicationAbstract abs2 = PublicationAbstract.ofStructured(sections);
///
/// // 按标签查段落（可能命中多条）
/// List<PublicationAbstractSection> methods = abs2.findSectionsByLabel("METHODS");
///
/// // 创建空摘要
/// PublicationAbstract empty = PublicationAbstract.empty();
/// ```
///
/// @param plainText 纯文本摘要（非结构化摘要的全文）
/// @param structuredSections 结构化摘要段落（有序列表）
/// @param copyright 版权信息/使用限制
/// @param abstractType 摘要类型
/// @author linqibin
/// @since 0.1.0
@Builder(toBuilder = true)
public record PublicationAbstract(
    String plainText,
    List<PublicationAbstractSection> structuredSections,
    String copyright,
    AbstractType abstractType)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：处理防御性拷贝和默认值。
  public PublicationAbstract {
    // 防御性拷贝：确保 structuredSections 不可变
    structuredSections = structuredSections != null ? List.copyOf(structuredSections) : List.of();

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
  /// @param sections 段落有序列表（非空）
  /// @return 结构化摘要
  /// @throws IllegalArgumentException 如果段落列表为空
  public static PublicationAbstract ofStructured(List<PublicationAbstractSection> sections) {
    return ofStructured(sections, null);
  }

  /// 创建带版权信息的结构化摘要。
  ///
  /// 工厂方法名已承诺 STRUCTURED，故拒绝空段落列表——否则会产出
  /// `isStructured()` 为 true 但 `hasContent()` 为 false 的矛盾态。
  ///
  /// @param sections 段落有序列表（非空）
  /// @param copyright 版权信息
  /// @return 结构化摘要
  /// @throws IllegalArgumentException 如果段落列表为空
  public static PublicationAbstract ofStructured(
      List<PublicationAbstractSection> sections, String copyright) {
    Assert.notEmpty(sections, "结构化摘要段落不能为空");
    return new PublicationAbstract(null, sections, copyright, AbstractType.STRUCTURED);
  }

  /// 创建同时包含纯文本和结构化段落的摘要。
  ///
  /// 某些数据源同时提供两种格式。
  ///
  /// @param plainText 纯文本内容
  /// @param sections 段落有序列表
  /// @param copyright 版权信息
  /// @return 结构化摘要（优先标记为结构化）
  public static PublicationAbstract ofBoth(
      String plainText, List<PublicationAbstractSection> sections, String copyright) {
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

  /// 按标签查段落（忽略大小写与首尾空白），可返回多条（重复 label 是合法形态）。
  ///
  /// @param label 段落标签
  /// @return 命中的段落列表（可能为空）
  public List<PublicationAbstractSection> findSectionsByLabel(String label) {
    if (label == null) {
      return List.of();
    }
    String upper = label.trim().toUpperCase(Locale.ROOT);
    return structuredSections.stream()
        .filter(s -> s.label() != null && s.label().toUpperCase(Locale.ROOT).equals(upper))
        .toList();
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
      return structuredSections.stream()
          .map(PublicationAbstractSection::text)
          .collect(Collectors.joining(" "));
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
