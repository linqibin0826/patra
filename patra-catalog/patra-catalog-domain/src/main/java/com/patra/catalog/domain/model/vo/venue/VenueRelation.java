package com.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.enums.VenueRelationType;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/// 期刊关联关系值对象（不可变）。
///
/// **设计说明**：
///
/// - 作为值对象存在（不是实体）
/// - 使用 Record 实现不可变性
/// - 通过 `VenueRepository` 统一管理
/// - 存储期刊之间的历史关系（前身、后继、合并、拆分等）
/// - 数据主要来源于 NLM Serfile 的 TitleRelated 元素
///
/// **关系类型说明**：
///
/// | 类型 | 含义 | 示例 |
/// |------|------|------|
/// | PRECEDING | 前身期刊 | 期刊 A 是当前期刊的前身 |
/// | SUCCEEDING | 后继期刊 | 期刊 B 是当前期刊的后继 |
/// | ABSORBED | 吸收了其他期刊 | 当前期刊吸收了期刊 C |
/// | ABSORBED_BY | 被其他期刊吸收 | 当前期刊被期刊 D 吸收 |
/// | MERGED | 合并形成新期刊 | 当前期刊与其他期刊合并 |
/// | SPLIT_FROM | 从其他期刊拆分 | 当前期刊从期刊 E 拆分出来 |
///
/// **示例**：
///
/// ```java
/// // 创建前身关系
/// VenueRelation relation = VenueRelation.create(
///     "Journal of Medicine",
///     VenueRelationType.PRECEDING,
///     "101234567"
/// );
///
/// // 设置生效日期（返回新实例）
/// VenueRelation updated = relation.withEffectiveDate(LocalDate.of(2000, 1, 1));
/// ```
///
/// @param relatedVenueId 关联期刊 ID（如果已在系统中，可选）
/// @param relatedNlmId 关联期刊 NLM ID（可选）
/// @param relatedTitle 关联期刊标题（必填）
/// @param relationType 关系类型（必填）
/// @param effectiveDate 生效日期（可选）
/// @param notes 备注说明（可选）
/// @author linqibin
/// @since 0.1.0
@SuppressWarnings("java:S6218") // 自定义 equals/hashCode 基于业务语义（标题 + 关系类型）
public record VenueRelation(
    Long relatedVenueId,
    String relatedNlmId,
    String relatedTitle,
    VenueRelationType relationType,
    LocalDate effectiveDate,
    String notes)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证参数。
  public VenueRelation {
    Assert.notBlank(relatedTitle, "关联期刊标题不能为空");
    Assert.notNull(relationType, "关系类型不能为空");
  }

  // ========== 工厂方法 ==========

  /// 创建期刊关联关系。
  ///
  /// @param relatedTitle 关联期刊标题
  /// @param relationType 关系类型
  /// @return 关联关系值对象
  public static VenueRelation create(String relatedTitle, VenueRelationType relationType) {
    return new VenueRelation(null, null, relatedTitle, relationType, null, null);
  }

  /// 创建带 NLM ID 的期刊关联关系。
  ///
  /// @param relatedTitle 关联期刊标题
  /// @param relationType 关系类型
  /// @param relatedNlmId 关联期刊 NLM ID
  /// @return 关联关系值对象
  public static VenueRelation create(
      String relatedTitle, VenueRelationType relationType, String relatedNlmId) {
    return new VenueRelation(null, relatedNlmId, relatedTitle, relationType, null, null);
  }

  /// 创建完整的期刊关联关系。
  ///
  /// @param relatedTitle 关联期刊标题
  /// @param relationType 关系类型
  /// @param relatedNlmId 关联期刊 NLM ID
  /// @param effectiveDate 生效日期
  /// @param notes 备注说明
  /// @return 关联关系值对象
  public static VenueRelation create(
      String relatedTitle,
      VenueRelationType relationType,
      String relatedNlmId,
      LocalDate effectiveDate,
      String notes) {
    return new VenueRelation(null, relatedNlmId, relatedTitle, relationType, effectiveDate, notes);
  }

  // ========== with-style 方法（返回新实例） ==========

  /// 添加生效日期。
  ///
  /// @param date 生效日期
  /// @return 新的关联关系值对象
  public VenueRelation withEffectiveDate(LocalDate date) {
    return new VenueRelation(
        this.relatedVenueId,
        this.relatedNlmId,
        this.relatedTitle,
        this.relationType,
        date,
        this.notes);
  }

  /// 关联到系统内期刊。
  ///
  /// @param venueId 系统内期刊 ID
  /// @return 新的关联关系值对象
  public VenueRelation linkToVenue(Long venueId) {
    return new VenueRelation(
        venueId,
        this.relatedNlmId,
        this.relatedTitle,
        this.relationType,
        this.effectiveDate,
        this.notes);
  }

  // ========== 查询方法 ==========

  /// 判断是否已关联到系统内期刊。
  ///
  /// @return true 如果已关联
  public boolean isLinkedToVenue() {
    return relatedVenueId != null;
  }

  /// 判断是否有 NLM ID。
  ///
  /// @return true 如果有 NLM ID
  public boolean hasNlmId() {
    return relatedNlmId != null && !relatedNlmId.isBlank();
  }

  /// 判断是否有生效日期。
  ///
  /// @return true 如果有生效日期
  public boolean hasEffectiveDate() {
    return effectiveDate != null;
  }

  /// 判断是否为前身关系。
  ///
  /// @return true 如果为前身关系
  public boolean isPreceding() {
    return relationType == VenueRelationType.PRECEDING;
  }

  /// 判断是否为后继关系。
  ///
  /// @return true 如果为后继关系
  public boolean isSucceeding() {
    return relationType == VenueRelationType.SUCCEEDING;
  }

  @Override
  public String toString() {
    return String.format(
        "VenueRelation[title=%s, type=%s, nlmId=%s]", relatedTitle, relationType, relatedNlmId);
  }

  /// 业务相等性：关联期刊标题 + 关系类型。
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VenueRelation that)) {
      return false;
    }
    return Objects.equals(relatedTitle, that.relatedTitle) && relationType == that.relationType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(relatedTitle, relationType);
  }
}
