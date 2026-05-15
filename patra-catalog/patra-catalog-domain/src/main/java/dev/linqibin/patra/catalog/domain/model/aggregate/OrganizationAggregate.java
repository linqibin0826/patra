package dev.linqibin.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import dev.linqibin.patra.catalog.domain.model.enums.ExternalIdType;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationStatus;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationType;
import dev.linqibin.patra.catalog.domain.model.vo.organization.AdminInfo;
import dev.linqibin.patra.catalog.domain.model.vo.organization.ExternalId;
import dev.linqibin.patra.catalog.domain.model.vo.organization.GeoLocation;
import dev.linqibin.patra.catalog.domain.model.vo.organization.OrganizationId;
import dev.linqibin.patra.catalog.domain.model.vo.organization.OrganizationLink;
import dev.linqibin.patra.catalog.domain.model.vo.organization.OrganizationName;
import dev.linqibin.patra.catalog.domain.model.vo.organization.OrganizationRelation;
import dev.linqibin.patra.catalog.domain.model.vo.organization.RorId;
import dev.linqibin.commons.domain.AggregateRoot;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;

/// 机构聚合根。
///
/// 基于 ROR (Research Organization Registry) Schema v2.0 设计。
/// 表示一个研究机构，包含其标识信息、名称、地理位置、关系等。
///
/// **CQRS 设计**：
///
/// 本聚合根遵循 CQRS 模式，**仅用于写入侧**（数据导入、更新）：
///
/// - 只包含验证不变量所需的最小属性集
/// - 数据从 ROR 数据源流入，经聚合根验证后持久化
/// - 读取场景通过 DO 或专门的读模型实现，不经过聚合根重建
///
/// **聚合边界**：
///
/// - OrganizationName（子实体，1:N）：机构名称集合，支持多语言和多类型
/// - ExternalId（子实体，1:N）：外部标识符集合（GRID、ISNI 等）
/// - OrganizationRelation（子实体，1:N）：机构关系集合（父级、子级等）
/// - GeoLocation（子实体，1:N）：地理位置集合（支持多校区）
///
/// **嵌入式值对象**（作为 JSON 字段存储在主表）：
///
/// - types：机构类型集合（Set<OrganizationType>）
/// - domains：域名列表（List<String>）
/// - links：网站链接（List<OrganizationLink>）
/// - adminInfo：ROR 管理元数据（AdminInfo）
///
/// **验证规则**：
///
/// - rorId 和 displayName 和 status 必填
/// - rorId 全局唯一
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/fields">ROR Fields Documentation</a>
@Getter
public class OrganizationAggregate extends AggregateRoot<OrganizationId> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 核心属性（不变量） ==========

  /// ROR ID（必填，全局唯一）
  private final RorId rorId;

  /// 显示名称（必填）
  private final String displayName;

  /// 机构状态（必填）
  private final OrganizationStatus status;

  // ========== 基本属性 ==========

  /// 成立年份
  private Integer established;

  /// ROR 管理元数据（JSON 嵌入）
  private AdminInfo adminInfo;

  // ========== JSON 存储字段 ==========

  /// 机构类型集合
  private final Set<OrganizationType> types;

  /// 域名列表
  private final List<String> domains;

  /// 网站链接
  private final List<OrganizationLink> links;

  // ========== 子实体集合（独立子表） ==========

  /// 多语言名称
  private final List<OrganizationName> names;

  /// 外部标识符
  private final List<ExternalId> externalIds;

  /// 机构关系
  private final List<OrganizationRelation> relations;

  /// 地理位置
  private final List<GeoLocation> locations;

  /// 私有构造函数（通过工厂方法创建）。
  ///
  /// @param id 主键 ID（新建时为 null）
  /// @param rorId ROR ID
  /// @param displayName 显示名称
  /// @param status 机构状态
  private OrganizationAggregate(
      OrganizationId id, RorId rorId, String displayName, OrganizationStatus status) {
    super(id);

    Assert.notNull(rorId, "ROR ID 不能为空");
    Assert.notBlank(displayName, "显示名称不能为空");
    Assert.notNull(status, "状态不能为空");

    this.rorId = rorId;
    this.displayName = displayName;
    this.status = status;

    // 初始化集合
    this.types = new HashSet<>();
    this.domains = new ArrayList<>();
    this.links = new ArrayList<>();
    this.names = new ArrayList<>();
    this.externalIds = new ArrayList<>();
    this.relations = new ArrayList<>();
    this.locations = new ArrayList<>();
  }

  // ========== 工厂方法 ==========

  /// 从 ROR 数据创建机构。
  ///
  /// @param rorId ROR ID（必填）
  /// @param displayName 显示名称
  /// @param status 机构状态
  /// @return 机构聚合根
  public static OrganizationAggregate fromRor(
      RorId rorId, String displayName, OrganizationStatus status) {
    return new OrganizationAggregate(null, rorId, displayName, status);
  }

  /// 从持久化状态重建聚合根（由 Repository 使用）。
  ///
  /// @param id 主键 ID（OrganizationId 值对象）
  /// @param rorId ROR ID
  /// @param displayName 显示名称
  /// @param status 机构状态
  /// @param version 乐观锁版本
  /// @return 重建的聚合根
  public static OrganizationAggregate restore(
      OrganizationId id, RorId rorId, String displayName, OrganizationStatus status, Long version) {
    OrganizationAggregate aggregate = new OrganizationAggregate(id, rorId, displayName, status);
    aggregate.assignVersion(version != null ? version : 0L);
    return aggregate;
  }

  // ========== 基本属性设置方法 ==========

  /// 设置成立年份。
  ///
  /// @param established 成立年份
  /// @return 当前对象
  public OrganizationAggregate withEstablished(Integer established) {
    this.established = established;
    return this;
  }

  /// 设置管理元数据。
  ///
  /// @param adminInfo 管理元数据
  /// @return 当前对象
  public OrganizationAggregate withAdminInfo(AdminInfo adminInfo) {
    this.adminInfo = adminInfo;
    return this;
  }

  // ========== 类型管理方法 ==========

  /// 添加机构类型。
  ///
  /// @param type 机构类型
  public void addType(OrganizationType type) {
    Assert.notNull(type, "机构类型不能为空");
    types.add(type);
  }

  /// 批量设置机构类型（替换现有类型）。
  ///
  /// @param types 机构类型集合
  /// @return 当前对象
  public OrganizationAggregate withTypes(Set<OrganizationType> types) {
    this.types.clear();
    if (types != null) {
      this.types.addAll(types);
    }
    return this;
  }

  /// 获取机构类型（不可变视图）。
  ///
  /// @return 机构类型集合
  public Set<OrganizationType> getTypes() {
    return Collections.unmodifiableSet(types);
  }

  // ========== 域名管理方法 ==========

  /// 添加域名。
  ///
  /// @param domain 域名
  public void addDomain(String domain) {
    Assert.notBlank(domain, "域名不能为空");
    if (!domains.contains(domain)) {
      domains.add(domain);
    }
  }

  /// 批量设置域名（替换现有域名）。
  ///
  /// @param domains 域名列表
  /// @return 当前对象
  public OrganizationAggregate withDomains(List<String> domains) {
    this.domains.clear();
    if (domains != null) {
      this.domains.addAll(domains);
    }
    return this;
  }

  /// 获取域名（不可变视图）。
  ///
  /// @return 域名列表
  public List<String> getDomains() {
    return Collections.unmodifiableList(domains);
  }

  // ========== 链接管理方法 ==========

  /// 添加链接。
  ///
  /// @param link 链接
  public void addLink(OrganizationLink link) {
    Assert.notNull(link, "链接不能为空");
    if (!links.contains(link)) {
      links.add(link);
    }
  }

  /// 批量设置链接（替换现有链接）。
  ///
  /// @param links 链接列表
  /// @return 当前对象
  public OrganizationAggregate withLinks(List<OrganizationLink> links) {
    this.links.clear();
    if (links != null) {
      this.links.addAll(links);
    }
    return this;
  }

  /// 获取链接（不可变视图）。
  ///
  /// @return 链接列表
  public List<OrganizationLink> getLinks() {
    return Collections.unmodifiableList(links);
  }

  /// 获取官网 URL。
  ///
  /// @return 官网 URL，如果不存在则返回 empty
  public Optional<String> getWebsiteUrl() {
    return links.stream()
        .filter(OrganizationLink::isWebsite)
        .map(OrganizationLink::value)
        .findFirst();
  }

  /// 获取 Wikipedia URL。
  ///
  /// @return Wikipedia URL，如果不存在则返回 empty
  public Optional<String> getWikipediaUrl() {
    return links.stream()
        .filter(OrganizationLink::isWikipedia)
        .map(OrganizationLink::value)
        .findFirst();
  }

  // ========== 名称管理方法 ==========

  /// 添加名称。
  ///
  /// @param name 名称
  public void addName(OrganizationName name) {
    Assert.notNull(name, "名称不能为空");
    // 基于 value + lang 判断重复（OrganizationName 的 equals）
    if (!names.contains(name)) {
      names.add(name);
    }
  }

  /// 移除名称。
  ///
  /// @param name 名称
  /// @return 是否成功移除
  public boolean removeName(OrganizationName name) {
    return names.remove(name);
  }

  /// 批量设置名称（替换现有名称）。
  ///
  /// 此方法会完整跟踪子实体变更：先记录所有删除，再记录所有添加。
  /// 适用于批量更新场景，`updateBatch()` 会正确处理这些变更。
  ///
  /// @param names 名称列表
  /// @return 当前对象
  public OrganizationAggregate withNames(List<OrganizationName> names) {
    this.names.clear();
    if (names != null) {
      this.names.addAll(names);
    }
    return this;
  }

  /// 获取名称（不可变视图）。
  ///
  /// @return 名称列表
  public List<OrganizationName> getNames() {
    return Collections.unmodifiableList(names);
  }

  // ========== 外部标识符管理方法 ==========

  /// 添加或更新外部标识符。
  ///
  /// 每种类型只能有一个外部标识符，如果已存在则替换。
  ///
  /// @param extId 外部标识符
  public void addExternalId(ExternalId extId) {
    Assert.notNull(extId, "外部标识符不能为空");

    final ExternalIdType targetType = extId.type();
    // 查找是否已存在相同类型的标识符
    Optional<ExternalId> existing =
        externalIds.stream().filter(e -> e.type() == targetType).findFirst();

    ExternalId toSave = extId;
    if (existing.isPresent()) {
      ExternalId current = existing.get();
      if (extId.id() == null && current.id() != null) {
        toSave = extId.withId(current.id());
      }
      // 替换现有标识符
      int index = externalIds.indexOf(current);
      externalIds.set(index, toSave);
    } else {
      externalIds.add(toSave);
    }
  }

  /// 移除外部标识符。
  ///
  /// @param type 标识符类型
  /// @return 是否成功移除
  public boolean removeExternalId(ExternalIdType type) {
    Optional<ExternalId> existing = externalIds.stream().filter(e -> e.type() == type).findFirst();

    if (existing.isPresent()) {
      externalIds.remove(existing.get());
      trackChildRemoved(ExternalId.class, existing.get());
      return true;
    }
    return false;
  }

  /// 获取特定类型的外部标识符。
  ///
  /// @param type 标识符类型
  /// @return 外部标识符，如果不存在则返回 empty
  public Optional<ExternalId> getExternalId(ExternalIdType type) {
    return externalIds.stream().filter(e -> e.type() == type).findFirst();
  }

  /// 批量设置外部标识符（替换现有标识符）。
  ///
  /// 此方法会完整跟踪子实体变更：先记录所有删除，再记录所有添加。
  /// 适用于批量更新场景，`updateBatch()` 会正确处理这些变更。
  ///
  /// @param externalIds 外部标识符列表
  /// @return 当前对象
  public OrganizationAggregate withExternalIds(List<ExternalId> externalIds) {
    this.externalIds.clear();
    if (externalIds != null) {
      this.externalIds.addAll(externalIds);
    }
    return this;
  }

  /// 获取外部标识符（不可变视图）。
  ///
  /// @return 外部标识符列表
  public List<ExternalId> getExternalIds() {
    return Collections.unmodifiableList(externalIds);
  }

  // ========== 地理位置管理方法 ==========

  /// 添加地理位置。
  ///
  /// @param location 地理位置
  public void addLocation(GeoLocation location) {
    Assert.notNull(location, "地理位置不能为空");
    // 基于 geonamesId 判断重复（GeoLocation 的 equals）
    if (!locations.contains(location)) {
      locations.add(location);
    }
  }

  /// 移除地理位置。
  ///
  /// @param location 地理位置
  /// @return 是否成功移除
  public boolean removeLocation(GeoLocation location) {
    return locations.remove(location);
  }

  /// 批量设置地理位置（替换现有位置）。
  ///
  /// 此方法会完整跟踪子实体变更：先记录所有删除，再记录所有添加。
  /// 适用于批量更新场景，`updateBatch()` 会正确处理这些变更。
  ///
  /// @param locations 地理位置列表
  /// @return 当前对象
  public OrganizationAggregate withLocations(List<GeoLocation> locations) {
    this.locations.clear();
    if (locations != null) {
      this.locations.addAll(locations);
    }
    return this;
  }

  /// 获取地理位置（不可变视图）。
  ///
  /// @return 地理位置列表
  public List<GeoLocation> getLocations() {
    return Collections.unmodifiableList(locations);
  }

  // ========== 关系管理方法 ==========

  /// 添加机构关系。
  ///
  /// @param relation 机构关系
  public void addRelation(OrganizationRelation relation) {
    Assert.notNull(relation, "机构关系不能为空");
    // 基于 type + relatedRorId 判断重复（OrganizationRelation 的 equals）
    if (!relations.contains(relation)) {
      relations.add(relation);
    }
  }

  /// 移除机构关系。
  ///
  /// @param relation 机构关系
  /// @return 是否成功移除
  public boolean removeRelation(OrganizationRelation relation) {
    return relations.remove(relation);
  }

  /// 批量设置机构关系（替换现有关系）。
  ///
  /// 此方法会完整跟踪子实体变更：先记录所有删除，再记录所有添加。
  /// 适用于批量更新场景，`updateBatch()` 会正确处理这些变更。
  ///
  /// @param relations 机构关系列表
  /// @return 当前对象
  public OrganizationAggregate withRelations(List<OrganizationRelation> relations) {
    this.relations.clear();
    if (relations != null) {
      this.relations.addAll(relations);
    }
    return this;
  }

  /// 获取机构关系（不可变视图）。
  ///
  /// @return 机构关系列表
  public List<OrganizationRelation> getRelations() {
    return Collections.unmodifiableList(relations);
  }

  /// 获取所有父级关系。
  ///
  /// @return 父级关系列表
  public List<OrganizationRelation> getParents() {
    return relations.stream().filter(OrganizationRelation::isParent).toList();
  }

  /// 获取所有子级关系。
  ///
  /// @return 子级关系列表
  public List<OrganizationRelation> getChildren() {
    return relations.stream().filter(OrganizationRelation::isChild).toList();
  }

  /// 获取所有关联关系。
  ///
  /// @return 关联关系列表
  public List<OrganizationRelation> getRelatedOrganizations() {
    return relations.stream().filter(OrganizationRelation::isRelated).toList();
  }

  /// 获取所有后继关系。
  ///
  /// @return 后继关系列表
  public List<OrganizationRelation> getSuccessors() {
    return relations.stream().filter(OrganizationRelation::isSuccessor).toList();
  }

  /// 获取所有前身关系。
  ///
  /// @return 前身关系列表
  public List<OrganizationRelation> getPredecessors() {
    return relations.stream().filter(OrganizationRelation::isPredecessor).toList();
  }

  // ========== 便捷判断方法 ==========

  /// 判断机构是否活跃。
  ///
  /// @return true 如果状态为 ACTIVE
  public boolean isActive() {
    return status == OrganizationStatus.ACTIVE;
  }

  /// 判断机构是否已停止活动。
  ///
  /// @return true 如果状态为 INACTIVE
  public boolean isInactive() {
    return status == OrganizationStatus.INACTIVE;
  }

  /// 判断机构是否已撤销。
  ///
  /// @return true 如果状态为 WITHDRAWN
  public boolean isWithdrawn() {
    return status == OrganizationStatus.WITHDRAWN;
  }

  /// 判断是否为教育机构。
  ///
  /// @return true 如果类型包含 EDUCATION
  public boolean isEducation() {
    return types.contains(OrganizationType.EDUCATION);
  }

  /// 判断是否为企业。
  ///
  /// @return true 如果类型包含 COMPANY
  public boolean isCompany() {
    return types.contains(OrganizationType.COMPANY);
  }

  /// 判断是否为医疗机构。
  ///
  /// @return true 如果类型包含 HEALTHCARE
  public boolean isHealthcare() {
    return types.contains(OrganizationType.HEALTHCARE);
  }

  /// 判断是否为资助机构。
  ///
  /// @return true 如果类型包含 FUNDER
  public boolean isFunder() {
    return types.contains(OrganizationType.FUNDER);
  }

  /// 判断是否有父级机构。
  ///
  /// @return true 如果有父级关系
  public boolean hasParent() {
    return relations.stream().anyMatch(OrganizationRelation::isParent);
  }

  /// 判断是否有子级机构。
  ///
  /// @return true 如果有子级关系
  public boolean hasChildren() {
    return relations.stream().anyMatch(OrganizationRelation::isChild);
  }

  // ========== 不变量验证 ==========

  /// 验证聚合根的业务不变量。
  ///
  /// @throws IllegalStateException 如果不变量被违反
  @Override
  protected void assertInvariants() {
    // ROR ID 不能为空
    if (rorId == null) {
      throw new IllegalStateException("ROR ID 不能为空");
    }

    // 名称不能为空
    if (StrUtil.isBlank(displayName)) {
      throw new IllegalStateException("显示名称不能为空");
    }

    // 状态不能为空
    if (status == null) {
      throw new IllegalStateException("状态不能为空");
    }
  }

  @Override
  public String toString() {
    return String.format(
        "OrganizationAggregate[id=%s, rorId=%s, name=%s, status=%s]",
        getId(), rorId.getId(), displayName, status.getCode());
  }
}
