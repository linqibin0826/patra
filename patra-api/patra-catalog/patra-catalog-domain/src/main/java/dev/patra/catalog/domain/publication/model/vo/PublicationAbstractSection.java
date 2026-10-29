package dev.patra.catalog.domain.publication.model.vo;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// 摘要段落值对象——主摘要 / 翻译摘要 / Entity 序列化共享的 section 定义。
///
/// 存储形态为有序数组 `[{"label","text"}]`：保 section 顺序、容纳重复 label 与无 label 段。
///
/// **label 语义**：混合形态的无标签段以 `label=null` 保位，段落本身不承担判型职责——
/// 「全无 label 视为非结构化摘要、段落不入库」的分流由上游导入 Processor 完成，
/// 落到本层的段落列表已是导入侧决定要持久化的最终形态。
///
/// @param label 段落标签（如 BACKGROUND / METHODS；无标签段为 null，空白串归一化为 null）
/// @param text 段落内容（非空）
/// @author linqibin
/// @since 0.6.0
public record PublicationAbstractSection(String label, String text) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：label 空白归一化为 null 并 trim，text 非空校验。
  ///
  /// @throws IllegalArgumentException 如果段落内容为空白
  public PublicationAbstractSection {
    label = (label == null || label.isBlank()) ? null : label.trim();
    Assert.notBlank(text, "摘要段落内容不能为空");
  }

  /// 创建摘要段落。
  ///
  /// @param label 段落标签（可为 null / 空白）
  /// @param text 段落内容
  /// @return 段落值对象
  public static PublicationAbstractSection of(String label, String text) {
    return new PublicationAbstractSection(label, text);
  }

  /// 是否带标签段落（STRUCTURED 判型依据）。
  ///
  /// 命名刻意避开 `isXxx` / `getXxx` bean getter 形态——本记录会由 Hibernate 的
  /// JsonFormatMapper 直接序列化进 jsonb 列，`isLabeled()` 会被 Jackson 拾取为
  /// 额外的 `labeled` 属性写入库中，污染库内 wire format
  /// （`PublicationJpaMapperTest` 的 key 集合断言会红）。
  ///
  /// @return true 如果 label 非空
  public boolean hasLabel() {
    return label != null;
  }
}
