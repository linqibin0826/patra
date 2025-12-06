package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.enums.VenueRelationType;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Getter;

/// 期刊关联关系实体（聚合内实体，不是聚合根）。
///
/// 设计说明：
///
/// - 作为 VenueAggregate 的聚合内实体存在
/// - 存储期刊之间的历史关系（前身、后继、合并、拆分等）
/// - 数据主要来源于 NLM Serfile 的 TitleRelated 元素
///
/// 关系类型说明：
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
/// 使用示例：
///
/// ```java
/// // 创建前身关系
/// VenueRelation relation = VenueRelation.create(
///     "Journal of Medicine",
///     VenueRelationType.PRECEDING,
///     "101234567"
/// );
///
/// // 设置生效日期
/// relation = relation.withEffectiveDate(LocalDate.of(2000, 1, 1));
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class VenueRelation implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 标识符 ==========

  /// 主键 ID（由 Repository 在持久化时分配）
  private Long id;

  // ========== 业务字段 ==========

  /// 关联期刊 ID（如果已在系统中）
  private final Long relatedVenueId;

  /// 关联期刊 NLM ID
  private final String relatedNlmId;

  /// 关联期刊标题（必填）
  private final String relatedTitle;

  /// 关系类型
  private final VenueRelationType relationType;

  /// 生效日期
  private final LocalDate effectiveDate;

  /// 备注说明
  private final String notes;

  /// 私有构造函数。
  ///
  /// @param id 主键 ID（新建时为 null）
  /// @param relatedVenueId 关联期刊 ID
  /// @param relatedNlmId 关联期刊 NLM ID
  /// @param relatedTitle 关联期刊标题
  /// @param relationType 关系类型
  /// @param effectiveDate 生效日期
  /// @param notes 备注说明
  private VenueRelation(
      Long id,
      Long relatedVenueId,
      String relatedNlmId,
      String relatedTitle,
      VenueRelationType relationType,
      LocalDate effectiveDate,
      String notes) {
    Assert.notBlank(relatedTitle, "关联期刊标题不能为空");
    Assert.notNull(relationType, "关系类型不能为空");

    this.id = id;
    this.relatedVenueId = relatedVenueId;
    this.relatedNlmId = relatedNlmId;
    this.relatedTitle = relatedTitle;
    this.relationType = relationType;
    this.effectiveDate = effectiveDate;
    this.notes = notes;
  }

  // ========== 工厂方法 ==========

  /// 创建期刊关联关系。
  ///
  /// @param relatedTitle 关联期刊标题
  /// @param relationType 关系类型
  /// @return 关联关系实体
  public static VenueRelation create(String relatedTitle, VenueRelationType relationType) {
    return new VenueRelation(null, null, null, relatedTitle, relationType, null, null);
  }

  /// 创建带 NLM ID 的期刊关联关系。
  ///
  /// @param relatedTitle 关联期刊标题
  /// @param relationType 关系类型
  /// @param relatedNlmId 关联期刊 NLM ID
  /// @return 关联关系实体
  public static VenueRelation create(
      String relatedTitle, VenueRelationType relationType, String relatedNlmId) {
    return new VenueRelation(null, null, relatedNlmId, relatedTitle, relationType, null, null);
  }

  /// 创建完整的期刊关联关系。
  ///
  /// @param relatedTitle 关联期刊标题
  /// @param relationType 关系类型
  /// @param relatedNlmId 关联期刊 NLM ID
  /// @param effectiveDate 生效日期
  /// @param notes 备注说明
  /// @return 关联关系实体
  public static VenueRelation create(
      String relatedTitle,
      VenueRelationType relationType,
      String relatedNlmId,
      LocalDate effectiveDate,
      String notes) {
    return new VenueRelation(
        null, null, relatedNlmId, relatedTitle, relationType, effectiveDate, notes);
  }

  /// 从持久化状态重建实体（由 Repository 使用）。
  ///
  /// @param id 主键 ID
  /// @param relatedVenueId 关联期刊 ID
  /// @param relatedNlmId 关联期刊 NLM ID
  /// @param relatedTitle 关联期刊标题
  /// @param relationType 关系类型
  /// @param effectiveDate 生效日期
  /// @param notes 备注说明
  /// @return 重建的实体
  public static VenueRelation restore(
      Long id,
      Long relatedVenueId,
      String relatedNlmId,
      String relatedTitle,
      VenueRelationType relationType,
      LocalDate effectiveDate,
      String notes) {
    return new VenueRelation(
        id, relatedVenueId, relatedNlmId, relatedTitle, relationType, effectiveDate, notes);
  }

  // ========== 业务方法 ==========

  /// 设置 ID（由 Repository 在持久化后回写）。
  ///
  /// @param id 主键 ID
  public void assignId(Long id) {
    this.id = id;
  }

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

  /// 添加生效日期（返回新对象）。
  ///
  /// @param date 生效日期
  /// @return 新的关联关系实体
  public VenueRelation withEffectiveDate(LocalDate date) {
    return new VenueRelation(
        this.id,
        this.relatedVenueId,
        this.relatedNlmId,
        this.relatedTitle,
        this.relationType,
        date,
        this.notes);
  }

  /// 关联到系统内期刊（返回新对象）。
  ///
  /// @param venueId 系统内期刊 ID
  /// @return 新的关联关系实体
  public VenueRelation linkToVenue(Long venueId) {
    return new VenueRelation(
        this.id,
        venueId,
        this.relatedNlmId,
        this.relatedTitle,
        this.relationType,
        this.effectiveDate,
        this.notes);
  }

  @Override
  public String toString() {
    return String.format(
        "VenueRelation[title=%s, type=%s, nlmId=%s]", relatedTitle, relationType, relatedNlmId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VenueRelation that)) {
      return false;
    }
    // 业务相等性：关联期刊标题 + 关系类型
    return Objects.equals(relatedTitle, that.relatedTitle) && relationType == that.relationType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(relatedTitle, relationType);
  }
}
