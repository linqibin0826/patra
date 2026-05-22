package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import java.util.Locale;
import lombok.Getter;

/// 期刊关联类型枚举。
///
/// 字段映射：cat_venue_relation.relation_type
///
/// 定义期刊之间的演变关系，来源于 NLM LSIOU 的 TitleType 属性（参考 nlmserials DTD）。
///
/// **前后刊关系**：
/// - PRECEDING / SUCCEEDING: 前刊 / 后刊
/// - PRECEDING_IN_PART / SUCCEEDING_IN_PART: 部分前刊 / 部分后刊
///
/// **吸收关系**：
/// - ABSORBED / ABSORBED_BY: 被吸收 / 吸收了
/// - ABSORBED_IN_PART / ABSORBED_IN_PART_BY: 部分被吸收 / 部分吸收了
///
/// **合并关系**：
/// - MERGED_TO / MERGER_OF: 合并到 / 合并来源
///
/// **分拆关系**：
/// - SPLIT_FROM / SPLIT_TO: 分拆自 / 分拆到
///
/// **取代关系**：
/// - SUPERSEDES / SUPERSEDED_BY: 取代 / 被取代
/// - SUPERSEDES_IN_PART / SUPERSEDED_IN_PART_BY: 部分取代 / 部分被取代
///
/// **其他关系**：
/// - ANALYTIC: 分析型关系
/// - RELATED: 一般相关
/// - REVERSION: 回归原刊名
/// - SERIES / SERIES_AUTHORITY: 丛书关系
/// - TRANSLATED: 翻译版本
/// - OTHER / UNDETERMINED: 其他 / 未确定
///
/// 使用示例：
///
/// ```java
/// VenueRelationType type = VenueRelationType.fromLsiouTitleType("Preceding");
/// if (type.isPredecessor()) {
///     // 处理前刊关系
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum VenueRelationType {

  /// 前刊（本刊继承自该刊）
  PRECEDING("PRECEDING", "前刊"),

  /// 部分前刊（本刊部分继承自该刊）
  PRECEDING_IN_PART("PRECEDING_IN_PART", "部分前刊"),

  /// 后刊（本刊被该刊继承）
  SUCCEEDING("SUCCEEDING", "后刊"),

  /// 部分后刊（本刊被该刊部分继承）
  SUCCEEDING_IN_PART("SUCCEEDING_IN_PART", "部分后刊"),

  /// 被吸收（本刊被该刊吸收）
  ABSORBED("ABSORBED", "被吸收"),

  /// 吸收了（本刊吸收了该刊）
  ABSORBED_BY("ABSORBED_BY", "吸收了"),

  /// 部分被吸收（本刊部分被该刊吸收）
  ABSORBED_IN_PART("ABSORBED_IN_PART", "部分被吸收"),

  /// 部分吸收了（本刊部分吸收了该刊）
  ABSORBED_IN_PART_BY("ABSORBED_IN_PART_BY", "部分吸收了"),

  /// 合并到（本刊合并到该刊）
  MERGED_TO("MERGED_TO", "合并到"),

  /// 合并来源（本刊由该刊合并而来）
  MERGER_OF("MERGER_OF", "合并来源"),

  /// 分拆自（本刊从该刊分拆而来）
  SPLIT_FROM("SPLIT_FROM", "分拆自"),

  /// 分拆到（本刊分拆出该刊）
  SPLIT_TO("SPLIT_TO", "分拆到"),

  /// 取代（本刊取代该刊）
  SUPERSEDES("SUPERSEDES", "取代"),

  /// 被取代（本刊被该刊取代）
  SUPERSEDED_BY("SUPERSEDED_BY", "被取代"),

  /// 部分取代（本刊部分取代该刊）
  SUPERSEDES_IN_PART("SUPERSEDES_IN_PART", "部分取代"),

  /// 部分被取代（本刊部分被该刊取代）
  SUPERSEDED_IN_PART_BY("SUPERSEDED_IN_PART_BY", "部分被取代"),

  /// 分析型（分析性关系，如论文与期刊）
  ANALYTIC("ANALYTIC", "分析"),

  /// 相关（一般相关关系）
  RELATED("RELATED", "相关"),

  /// 回归（恢复原刊名）
  REVERSION("REVERSION", "回归"),

  /// 丛书（属于某丛书）
  SERIES("SERIES", "丛书"),

  /// 丛书权威（丛书的权威记录）
  SERIES_AUTHORITY("SERIES_AUTHORITY", "丛书权威"),

  /// 翻译（翻译版本关系）
  TRANSLATED("TRANSLATED", "翻译"),

  /// 其他（未分类的关系）
  OTHER("OTHER", "其他"),

  /// 未确定（关系类型未确定）
  UNDETERMINED("UNDETERMINED", "未确定");

  /// 数据库存储的代码值
  private final String code;

  /// 中文描述
  private final String description;

  VenueRelationType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static VenueRelationType fromCode(String value) {
    Assert.notBlank(value, "关联类型代码不能为空");
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    for (VenueRelationType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的关联类型：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static VenueRelationType fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    for (VenueRelationType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    return null;
  }

  /// 从 LSIOU TitleType 属性值转换。
  ///
  /// LSIOU 中的 TitleType 属性值与本枚举的映射关系（参考 nlmserials DTD）：
  ///
  /// | TitleType | 枚举值 |
  /// |-----------|--------|
  /// | Absorbed | ABSORBED |
  /// | AbsorbedBy | ABSORBED_BY |
  /// | AbsorbedInPart | ABSORBED_IN_PART |
  /// | AbsorbedInPartBy | ABSORBED_IN_PART_BY |
  /// | Analytic | ANALYTIC |
  /// | MergedTo | MERGED_TO |
  /// | MergerOf | MERGER_OF |
  /// | Other | OTHER |
  /// | Preceding | PRECEDING |
  /// | PrecedingInPart | PRECEDING_IN_PART |
  /// | Related | RELATED |
  /// | Reversion | REVERSION |
  /// | Series | SERIES |
  /// | SeriesAuthority | SERIES_AUTHORITY |
  /// | SplitFrom | SPLIT_FROM |
  /// | SplitTo | SPLIT_TO |
  /// | Succeeding | SUCCEEDING |
  /// | SucceedingInPart | SUCCEEDING_IN_PART |
  /// | SupersededBy | SUPERSEDED_BY |
  /// | SupersededInPartBy | SUPERSEDED_IN_PART_BY |
  /// | Supersedes | SUPERSEDES |
  /// | SupersedesInPart | SUPERSEDES_IN_PART |
  /// | Translated | TRANSLATED |
  /// | Undetermined | UNDETERMINED |
  ///
  /// @param titleType LSIOU 中的 TitleType 值
  /// @return 对应的枚举值，无法识别则返回 null
  public static VenueRelationType fromLsiouTitleType(String titleType) {
    if (titleType == null || titleType.isBlank()) {
      return null;
    }
    return switch (titleType.trim()) {
      case "Absorbed" -> ABSORBED;
      case "AbsorbedBy" -> ABSORBED_BY;
      case "AbsorbedInPart" -> ABSORBED_IN_PART;
      case "AbsorbedInPartBy" -> ABSORBED_IN_PART_BY;
      case "Analytic" -> ANALYTIC;
      case "MergedTo" -> MERGED_TO;
      case "MergerOf" -> MERGER_OF;
      case "Other" -> OTHER;
      case "Preceding" -> PRECEDING;
      case "PrecedingInPart" -> PRECEDING_IN_PART;
      case "Related" -> RELATED;
      case "Reversion" -> REVERSION;
      case "Series" -> SERIES;
      case "SeriesAuthority" -> SERIES_AUTHORITY;
      case "SplitFrom" -> SPLIT_FROM;
      case "SplitTo" -> SPLIT_TO;
      case "Succeeding" -> SUCCEEDING;
      case "SucceedingInPart" -> SUCCEEDING_IN_PART;
      case "SupersededBy" -> SUPERSEDED_BY;
      case "SupersededInPartBy" -> SUPERSEDED_IN_PART_BY;
      case "Supersedes" -> SUPERSEDES;
      case "SupersedesInPart" -> SUPERSEDES_IN_PART;
      case "Translated" -> TRANSLATED;
      case "Undetermined" -> UNDETERMINED;
      // 兼容旧值映射
      case "Merged" -> MERGED_TO;
      case "ContinuedBy" -> SUCCEEDING;
      case "Continues" -> PRECEDING;
      default -> null;
    };
  }

  /// 判断是否为前驱关系（本刊来源于其他刊）。
  ///
  /// @return true 如果为前驱类型关系
  public boolean isPredecessor() {
    return this == PRECEDING
        || this == PRECEDING_IN_PART
        || this == SPLIT_FROM
        || this == MERGER_OF
        || this == SUPERSEDES
        || this == SUPERSEDES_IN_PART;
  }

  /// 判断是否为后继关系（本刊演变为其他刊）。
  ///
  /// @return true 如果为后继类型关系
  public boolean isSuccessor() {
    return this == SUCCEEDING
        || this == SUCCEEDING_IN_PART
        || this == ABSORBED
        || this == ABSORBED_IN_PART
        || this == MERGED_TO
        || this == SPLIT_TO
        || this == SUPERSEDED_BY
        || this == SUPERSEDED_IN_PART_BY;
  }

  /// 判断是否为合并相关关系。
  ///
  /// @return true 如果为合并/吸收相关关系
  public boolean isMergeRelated() {
    return this == MERGED_TO
        || this == MERGER_OF
        || this == ABSORBED
        || this == ABSORBED_BY
        || this == ABSORBED_IN_PART
        || this == ABSORBED_IN_PART_BY;
  }

  /// 获取逆向关系类型。
  ///
  /// @return 逆向关系类型，如果没有明确的逆向关系则返回 null
  public VenueRelationType getInverse() {
    return switch (this) {
      case PRECEDING -> SUCCEEDING;
      case SUCCEEDING -> PRECEDING;
      case PRECEDING_IN_PART -> SUCCEEDING_IN_PART;
      case SUCCEEDING_IN_PART -> PRECEDING_IN_PART;
      case ABSORBED -> ABSORBED_BY;
      case ABSORBED_BY -> ABSORBED;
      case ABSORBED_IN_PART -> ABSORBED_IN_PART_BY;
      case ABSORBED_IN_PART_BY -> ABSORBED_IN_PART;
      case MERGED_TO -> MERGER_OF;
      case MERGER_OF -> MERGED_TO;
      case SPLIT_FROM -> SPLIT_TO;
      case SPLIT_TO -> SPLIT_FROM;
      case SUPERSEDES -> SUPERSEDED_BY;
      case SUPERSEDED_BY -> SUPERSEDES;
      case SUPERSEDES_IN_PART -> SUPERSEDED_IN_PART_BY;
      case SUPERSEDED_IN_PART_BY -> SUPERSEDES_IN_PART;
      // 以下关系无明确的逆向关系
      case ANALYTIC,
          RELATED,
          REVERSION,
          SERIES,
          SERIES_AUTHORITY,
          TRANSLATED,
          OTHER,
          UNDETERMINED ->
          null;
    };
  }
}
