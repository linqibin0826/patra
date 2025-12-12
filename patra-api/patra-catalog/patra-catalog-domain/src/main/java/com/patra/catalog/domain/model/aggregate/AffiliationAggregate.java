package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.enums.AffiliationType;
import com.patra.catalog.domain.model.vo.affiliation.AffiliationId;
import com.patra.catalog.domain.model.vo.affiliation.GridId;
import com.patra.catalog.domain.model.vo.affiliation.RorId;
import com.patra.catalog.domain.model.vo.common.DedupKey;
import com.patra.common.domain.AggregateRoot;
import java.io.Serial;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/// 机构聚合根。管理学术机构的基本信息、标识符和去重策略。
///
/// **一致性边界**：
///
/// - ROR ID 全局唯一(如果提供)
///   - GRID ID 全局唯一(如果提供)
///   - 去重键用于识别可能的重复机构
///   - 机构名称不能为空
///
/// **去重策略**：
///
/// **设计说明**：
///
/// - Affiliation 和 Author 是独立的聚合根
///   - 机构-作者关联通过 Repository 管理(不在聚合内)
///   - parent_affiliation 是文本字段,不关联 Affiliation 聚合
///   - 去重键由应用层计算并设置
///   - 机构层次结构(department/division/section)在聚合内管理
///
/// **业务规则**：
///
/// - ROR ID 是最可靠的去重标识符
///   - GRID ID 已合并到 ROR,但历史数据仍保留
///   - 机构名称应标准化(应用层职责)
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class AffiliationAggregate extends AggregateRoot<AffiliationId> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 名称信息 ==========

  /// 机构名称(标准化后)
  private final String name;

  /// 原始名称(外部采集,未标准化)
  @Setter(AccessLevel.PACKAGE)
  private String originalName;

  // ========== 层次结构 ==========

  /// 部门/科室(如 "Department of Medicine")
  @Setter(AccessLevel.PACKAGE)
  private String department;

  /// 分部/分院(如 "School of Medicine")
  @Setter(AccessLevel.PACKAGE)
  private String division;

  /// 科/组(如 "Cardiology Section")
  @Setter(AccessLevel.PACKAGE)
  private String section;

  // ========== 地理位置 ==========

  /// 城市(如 "Boston")
  private String city;

  /// 州/省(如 "Massachusetts","广东")
  private String stateProvince;

  /// 国家(ISO 3166-1 alpha-3,如 "USA","CHN")
  private String country;

  /// 邮政编码(如 "02115")
  private String postalCode;

  // ========== 标识符 ==========

  /// ROR 标识符(全局唯一,如果提供)
  private RorId rorId;

  /// GRID 标识符(全局唯一,如果提供)
  private GridId gridId;

  /// ISNI 标识符(如 "0000 0004 1936 8948")
  @Setter(AccessLevel.PACKAGE)
  private String isni;

  /// Ringgold ID(如 "1812")
  @Setter(AccessLevel.PACKAGE)
  private String ringgoldId;

  // ========== 关系和分类 ==========

  /// 上级机构(如 "Harvard University",文本字段)
  @Setter(AccessLevel.PACKAGE)
  private String parentAffiliation;

  /// 机构类型(教育、医疗、企业等)
  @Setter(AccessLevel.PACKAGE)
  private AffiliationType affiliationType;

  // ========== 去重和元数据 ==========

  /// 复合去重键(MD5哈希,应用层计算)
  @Setter(AccessLevel.PACKAGE)
  private DedupKey dedupKey;

  /// 机构元数据(JSON,灵活扩展)
  @Setter(AccessLevel.PACKAGE)
  private String metadataJson;

  /// 私有构造函数。
  ///
  /// @param id 主键ID(新建时为null)
  /// @param name 机构名称
  /// @param originalName 原始名称
  /// @param department 部门
  /// @param division 分部
  /// @param section 科/组
  /// @param city 城市
  /// @param stateProvince 州/省
  /// @param country 国家
  /// @param postalCode 邮政编码
  /// @param rorId ROR 标识符
  /// @param gridId GRID 标识符
  /// @param isni ISNI 标识符
  /// @param ringgoldId Ringgold ID
  /// @param parentAffiliation 上级机构
  /// @param affiliationType 机构类型
  /// @param dedupKey 去重键
  private AffiliationAggregate(
      AffiliationId id,
      String name,
      String originalName,
      String department,
      String division,
      String section,
      String city,
      String stateProvince,
      String country,
      String postalCode,
      RorId rorId,
      GridId gridId,
      String isni,
      String ringgoldId,
      String parentAffiliation,
      AffiliationType affiliationType,
      DedupKey dedupKey) {
    super(id);

    // 必填字段验证
    Assert.notBlank(name, "机构名称不能为空");

    // 赋值
    this.name = name;
    this.originalName = originalName;
    this.department = department;
    this.division = division;
    this.section = section;
    this.city = city;
    this.stateProvince = stateProvince;
    this.country = country;
    this.postalCode = postalCode;
    this.rorId = rorId;
    this.gridId = gridId;
    this.isni = isni;
    this.ringgoldId = ringgoldId;
    this.parentAffiliation = parentAffiliation;
    this.affiliationType = affiliationType;
    this.dedupKey = dedupKey;
  }

  // ========== 工厂方法 ==========

  /// 创建机构聚合根(最小信息)。
  ///
  /// @param name 机构名称
  /// @return 机构聚合根
  public static AffiliationAggregate create(String name) {
    return new AffiliationAggregate(
        null, // 新建时ID为null
        name, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null);
  }

  /// 创建机构聚合根(含 ROR ID)。
  ///
  /// @param name 机构名称
  /// @param rorId ROR 标识符
  /// @return 机构聚合根
  public static AffiliationAggregate create(String name, RorId rorId) {
    return new AffiliationAggregate(
        null, name, null, null, null, null, null, null, null, null, rorId, null, null, null, null,
        null, null);
  }

  /// 创建机构聚合根(含地理位置)。
  ///
  /// @param name 机构名称
  /// @param city 城市
  /// @param country 国家
  /// @return 机构聚合根
  public static AffiliationAggregate create(String name, String city, String country) {
    return new AffiliationAggregate(
        null, name, null, null, null, null, city, null, country, null, null, null, null, null, null,
        null, null);
  }

  /// 从持久化状态重建聚合根(由Repository使用)。
  ///
  /// @param id 主键ID
  /// @param name 机构名称
  /// @param originalName 原始名称
  /// @param department 部门
  /// @param division 分部
  /// @param section 科/组
  /// @param city 城市
  /// @param stateProvince 州/省
  /// @param country 国家
  /// @param postalCode 邮政编码
  /// @param rorId ROR 标识符
  /// @param gridId GRID 标识符
  /// @param isni ISNI 标识符
  /// @param ringgoldId Ringgold ID
  /// @param parentAffiliation 上级机构
  /// @param affiliationType 机构类型
  /// @param dedupKey 去重键
  /// @param version 乐观锁版本
  /// @return 重建的聚合根
  public static AffiliationAggregate restore(
      AffiliationId id,
      String name,
      String originalName,
      String department,
      String division,
      String section,
      String city,
      String stateProvince,
      String country,
      String postalCode,
      RorId rorId,
      GridId gridId,
      String isni,
      String ringgoldId,
      String parentAffiliation,
      AffiliationType affiliationType,
      DedupKey dedupKey,
      Long version) {
    AffiliationAggregate aggregate =
        new AffiliationAggregate(
            id,
            name,
            originalName,
            department,
            division,
            section,
            city,
            stateProvince,
            country,
            postalCode,
            rorId,
            gridId,
            isni,
            ringgoldId,
            parentAffiliation,
            affiliationType,
            dedupKey);
    aggregate.assignVersion(version);
    return aggregate;
  }

  // ========== 业务方法 ==========

  /// 设置 ROR 标识符。
  ///
  /// @param rorId ROR 标识符
  public void setRorId(RorId rorId) {
    Assert.notNull(rorId, "ROR ID 不能为空");
    this.rorId = rorId;
  }

  /// 设置 GRID 标识符(含验证逻辑)。
  ///
  /// @param gridId GRID 标识符
  public void setGridId(GridId gridId) {
    Assert.notNull(gridId, "GRID ID 不能为空");
    this.gridId = gridId;
  }

  /// 设置地理位置(复合业务方法)。
  ///
  /// @param city 城市
  /// @param stateProvince 州/省
  /// @param country 国家
  /// @param postalCode 邮政编码
  public void setGeographicLocation(
      String city, String stateProvince, String country, String postalCode) {
    this.city = city;
    this.stateProvince = stateProvince;
    this.country = country;
    this.postalCode = postalCode;
  }

  // ========== 便捷判断方法 ==========

  /// 判断是否有 ROR ID。
  ///
  /// @return true 如果有 ROR ID
  public boolean hasRorId() {
    return rorId != null;
  }

  /// 判断是否有 GRID ID。
  ///
  /// @return true 如果有 GRID ID
  public boolean hasGridId() {
    return gridId != null;
  }

  /// 判断是否有 ISNI。
  ///
  /// @return true 如果有 ISNI
  public boolean hasIsni() {
    return StrUtil.isNotBlank(isni);
  }

  /// 判断是否有去重键。
  ///
  /// @return true 如果有去重键
  public boolean hasDedupKey() {
    return dedupKey != null;
  }

  /// 判断是否有地理位置信息。
  ///
  /// @return true 如果有城市或国家信息
  public boolean hasGeographicLocation() {
    return StrUtil.isNotBlank(city) || StrUtil.isNotBlank(country);
  }

  /// 判断是否有上级机构。
  ///
  /// @return true 如果有上级机构
  public boolean hasParentAffiliation() {
    return StrUtil.isNotBlank(parentAffiliation);
  }

  /// 判断是否有层次结构信息。
  ///
  /// @return true 如果有部门/分部/科组信息
  public boolean hasHierarchy() {
    return StrUtil.isNotBlank(department)
        || StrUtil.isNotBlank(division)
        || StrUtil.isNotBlank(section);
  }

  /// 获取机构的完整显示名称。
  ///
  /// @return 完整名称(包含层次结构)
  public String getFullDisplayName() {
    StringBuilder sb = new StringBuilder(name);

    if (hasHierarchy()) {
      if (StrUtil.isNotBlank(department)) {
        sb.append(", ").append(department);
      }
      if (StrUtil.isNotBlank(division)) {
        sb.append(", ").append(division);
      }
      if (StrUtil.isNotBlank(section)) {
        sb.append(", ").append(section);
      }
    }

    if (hasGeographicLocation()) {
      if (StrUtil.isNotBlank(city)) {
        sb.append(" (").append(city);
        if (StrUtil.isNotBlank(country)) {
          sb.append(", ").append(country);
        }
        sb.append(")");
      } else if (StrUtil.isNotBlank(country)) {
        sb.append(" (").append(country).append(")");
      }
    }

    return sb.toString();
  }

  // ========== 不变量验证 ==========

  /// 验证聚合根的业务不变量。
  ///
  /// @throws IllegalStateException 如果不变量被违反
  @Override
  protected void assertInvariants() {
    // 名称不能为空
    if (StrUtil.isBlank(name)) {
      throw new IllegalStateException("机构名称不能为空");
    }
  }

  @Override
  public String toString() {
    return String.format(
        "AffiliationAggregate[id=%d, name=%s, rorId=%s, city=%s, country=%s]",
        getId(),
        name,
        hasRorId() ? rorId.value() : "null",
        city != null ? city : "null",
        country != null ? country : "null");
  }
}
