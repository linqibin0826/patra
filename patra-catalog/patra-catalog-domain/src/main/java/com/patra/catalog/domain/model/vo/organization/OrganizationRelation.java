package com.patra.catalog.domain.model.vo.organization;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.enums.OrganizationRelationType;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/// 机构关系值对象。
///
/// 字段映射：cat_organization_relation 表
///
/// 基于 ROR Schema v2.0 的 relationships 字段定义。表示机构之间的
/// 层级关系（PARENT/CHILD）、关联关系（RELATED）和时序关系
/// （SUCCESSOR/PREDECESSOR）。
///
/// **字段说明**：
///
/// | 字段 | 说明 |
/// |------|------|
/// | id | 数据库主键（雪花 ID） |
/// | type | 关系类型 |
/// | relatedRorId | 关联机构的 ROR ID |
/// | relatedLabel | 关联机构的显示名称（冗余字段） |
/// | relatedOrgId | 关联机构的内部 ID（延迟填充，导入后关联） |
///
/// **ROR Schema 结构**：
///
/// ```json
/// "relationships": [{
///   "type": "parent",
///   "label": "Harvard University",
///   "id": "https://ror.org/03vek6s52"
/// }]
/// ```
///
/// **关系解释**：
/// - PARENT：该机构是当前机构的父级（如大学是学院的父级）
/// - CHILD：该机构是当前机构的子级（如学院是大学的子级）
/// - RELATED：非层级的关联关系
/// - SUCCESSOR：该机构是当前机构的后继（接替者）
/// - PREDECESSOR：该机构是当前机构的前身
///
/// **相等性**：基于 type + relatedRorId
///
/// @param id 数据库主键（持久化后填充）
/// @param type 关系类型
/// @param relatedRorId 关联机构的 ROR ID
/// @param relatedLabel 关联机构的显示名称
/// @param relatedOrgId 关联机构的内部 ID（可选，延迟填充）
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/fields#relationships">ROR Relationships Field</a>
@SuppressWarnings("java:S6218") // 自定义 equals/hashCode 基于业务语义（type + relatedRorId）
public record OrganizationRelation(
    Long id,
    OrganizationRelationType type,
    RorId relatedRorId,
    String relatedLabel,
    Long relatedOrgId)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证参数。
  public OrganizationRelation {
    Assert.notNull(type, "关系类型不能为空");
    Assert.notNull(relatedRorId, "关联机构 ROR ID 不能为空");
    Assert.notBlank(relatedLabel, "关联机构名称不能为空");
  }

  // ========== 工厂方法 ==========

  /// 创建机构关系（无 ID）。
  ///
  /// @param type 关系类型
  /// @param relatedRorId 关联机构的 ROR ID
  /// @param relatedLabel 关联机构的显示名称
  /// @return 机构关系值对象
  public static OrganizationRelation create(
      OrganizationRelationType type, RorId relatedRorId, String relatedLabel) {
    return new OrganizationRelation(null, type, relatedRorId, relatedLabel, null);
  }

  /// 创建带 ID 的机构关系（用于从数据库加载）。
  ///
  /// @param id 数据库主键
  /// @param type 关系类型
  /// @param relatedRorId 关联机构的 ROR ID
  /// @param relatedLabel 关联机构的显示名称
  /// @param relatedOrgId 关联机构的内部 ID
  /// @return 机构关系值对象
  public static OrganizationRelation createWithId(
      Long id,
      OrganizationRelationType type,
      RorId relatedRorId,
      String relatedLabel,
      Long relatedOrgId) {
    return new OrganizationRelation(id, type, relatedRorId, relatedLabel, relatedOrgId);
  }

  // ========== with-style 方法 ==========

  /// 添加 ID（返回新实例）。
  ///
  /// @param id 数据库主键
  /// @return 带 ID 的新实例
  public OrganizationRelation withId(Long id) {
    return new OrganizationRelation(
        id, this.type, this.relatedRorId, this.relatedLabel, this.relatedOrgId);
  }

  /// 关联到系统内机构（返回新实例）。
  ///
  /// @param orgId 关联机构的内部 ID
  /// @return 已关联的新实例
  public OrganizationRelation linkToOrganization(Long orgId) {
    return new OrganizationRelation(
        this.id, this.type, this.relatedRorId, this.relatedLabel, orgId);
  }

  // ========== 查询方法 ==========

  /// 判断是否已持久化（有 ID）。
  ///
  /// @return true 如果已持久化
  public boolean hasId() {
    return id != null;
  }

  /// 判断是否已关联到系统内机构。
  ///
  /// @return true 如果已关联
  public boolean isLinkedToOrganization() {
    return relatedOrgId != null;
  }

  /// 判断是否为父级关系。
  ///
  /// @return true 如果是 PARENT
  public boolean isParent() {
    return type == OrganizationRelationType.PARENT;
  }

  /// 判断是否为子级关系。
  ///
  /// @return true 如果是 CHILD
  public boolean isChild() {
    return type == OrganizationRelationType.CHILD;
  }

  /// 判断是否为关联关系。
  ///
  /// @return true 如果是 RELATED
  public boolean isRelated() {
    return type == OrganizationRelationType.RELATED;
  }

  /// 判断是否为后继关系。
  ///
  /// @return true 如果是 SUCCESSOR
  public boolean isSuccessor() {
    return type == OrganizationRelationType.SUCCESSOR;
  }

  /// 判断是否为前身关系。
  ///
  /// @return true 如果是 PREDECESSOR
  public boolean isPredecessor() {
    return type == OrganizationRelationType.PREDECESSOR;
  }

  /// 判断是否为层级关系（PARENT 或 CHILD）。
  ///
  /// @return true 如果是层级关系
  public boolean isHierarchical() {
    return type.isHierarchical();
  }

  /// 判断是否为时序关系（SUCCESSOR 或 PREDECESSOR）。
  ///
  /// @return true 如果是时序关系
  public boolean isTemporal() {
    return type.isTemporal();
  }

  @Override
  public String toString() {
    return String.format(
        "OrganizationRelation[type=%s, relatedRorId=%s, label=%s]",
        type, relatedRorId, relatedLabel);
  }

  /// 业务相等性：type + relatedRorId。
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof OrganizationRelation that)) {
      return false;
    }
    return type == that.type && Objects.equals(relatedRorId, that.relatedRorId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, relatedRorId);
  }
}
