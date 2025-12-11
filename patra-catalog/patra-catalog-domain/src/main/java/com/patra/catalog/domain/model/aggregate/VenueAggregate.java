package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.ProvenanceInfo;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.common.domain.AggregateRoot;
import java.io.Serial;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

/// 出版载体聚合根（最小聚合）。
///
/// **CQRS 设计**：
///
/// 本聚合根遵循 CQRS 模式，**仅用于写入侧**（数据采集、更新）：
///
/// - 只包含验证不变量所需的最小属性集
/// - 数据从外部源（OpenAlex/PubMed）流入，经聚合根验证后持久化
/// - 读取场景通过 DO 或专门的读模型实现，不经过聚合根重建
///
/// **聚合边界**：
///
/// - VenueIdentifier（值对象，1:N）：标识符集合，保护 ISSN-L 唯一性不变量
///
/// **补充数据（通过 VenueRepository 独立管理，不属于聚合边界）**：
///
/// - VenueDetail：详情信息（出版信息、索引状态、OA 状态、宿主机构等）
/// - VenueStats：统计快照（works_count、cited_by_count 等）
/// - ApcInfo：APC 信息
/// - Society：关联学会列表
/// - VenuePublicationStats：年度指标集合（来自 OpenAlex）
/// - VenueMesh：MeSH 主题词集合（来自 Serfile）
/// - VenueRelation：期刊关联关系集合（来自 Serfile）
/// - VenueIndexingHistory：索引历史记录集合（来自 Serfile）
///
/// **验证规则**：
///
/// - 所有类型：displayName 和 venueType 必填
/// - 来自 OpenAlex：必须包含 OPENALEX 类型的标识符
/// - 来自 PubMed：必须包含 NLM 或 ISSN_L 类型的标识符
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class VenueAggregate extends AggregateRoot<Long> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 核心属性（最小集） ==========

  /// 载体类型（必填，不变量）
  private final VenueType venueType;

  /// 显示名称（必填，不变量）
  private final String displayName;

  /// 标识符集合（聚合边界内值对象）
  private final List<VenueIdentifier> identifiers;

  /// 数据来源信息
  private ProvenanceInfo provenance;

  /// 私有构造函数（通过工厂方法创建）。
  ///
  /// @param id 主键 ID（新建时为 null）
  /// @param venueType 载体类型
  /// @param displayName 显示名称
  private VenueAggregate(Long id, VenueType venueType, String displayName) {
    super(id);

    Assert.notNull(venueType, "载体类型不能为空");
    Assert.notBlank(displayName, "显示名称不能为空");

    this.venueType = venueType;
    this.displayName = displayName;
    this.identifiers = new ArrayList<>();
  }

  // ========== 工厂方法 ==========

  /// 从 OpenAlex Source 创建载体。
  ///
  /// @param openalexId OpenAlex Source ID（必填）
  /// @param venueType 载体类型
  /// @param displayName 显示名称
  /// @return 载体聚合根
  public static VenueAggregate fromOpenAlex(
      String openalexId, VenueType venueType, String displayName) {
    Assert.notBlank(openalexId, "OpenAlex ID 不能为空");

    VenueAggregate aggregate = new VenueAggregate(null, venueType, displayName);

    // 添加 OpenAlex 标识符
    aggregate.addIdentifier(VenueIdentifier.forOpenAlex(openalexId));

    // 设置来源信息
    aggregate.provenance = ProvenanceInfo.ofCode(ProvenanceInfo.CODE_OPENALEX);

    return aggregate;
  }

  /// 从 PubMed 创建期刊载体。
  ///
  /// @param displayName 期刊名称
  /// @param nlmId NLM 唯一标识符（nlmId 或 issnL 至少一个必填）
  /// @param issnL Linking ISSN（nlmId 或 issnL 至少一个必填）
  /// @return 载体聚合根
  public static VenueAggregate fromPubMed(String displayName, String nlmId, String issnL) {
    Assert.isTrue(
        StrUtil.isNotBlank(nlmId) || StrUtil.isNotBlank(issnL),
        "来自 PubMed 的载体必须提供 NLM ID 或 ISSN-L");

    VenueAggregate aggregate = new VenueAggregate(null, VenueType.JOURNAL, displayName);

    // 添加标识符
    if (StrUtil.isNotBlank(nlmId)) {
      aggregate.addIdentifier(VenueIdentifier.forNlm(nlmId));
    }
    if (StrUtil.isNotBlank(issnL)) {
      aggregate.addIdentifier(VenueIdentifier.forIssnL(issnL));
    }

    // 设置来源信息
    aggregate.provenance = ProvenanceInfo.forPubMed();

    return aggregate;
  }

  /// 从持久化状态重建聚合根（由 Repository 使用）。
  ///
  /// @param id 主键 ID
  /// @param venueType 载体类型
  /// @param displayName 显示名称
  /// @param version 乐观锁版本
  /// @return 重建的聚合根
  public static VenueAggregate restore(
      Long id, VenueType venueType, String displayName, Long version) {
    VenueAggregate aggregate = new VenueAggregate(id, venueType, displayName);
    aggregate.assignVersion(version);
    return aggregate;
  }

  // ========== 属性设置方法 ==========

  /// 设置数据来源信息。
  ///
  /// @param provenance 来源信息
  /// @return 当前对象
  public VenueAggregate withProvenance(ProvenanceInfo provenance) {
    this.provenance = provenance;
    markDirty();
    return this;
  }

  /// 设置 OpenAlex 来源信息。
  ///
  /// @param sourceCreatedDate 源数据创建日期
  /// @param sourceUpdatedDate 源数据更新日期
  /// @return 当前对象
  public VenueAggregate withOpenAlexProvenance(
      LocalDate sourceCreatedDate, LocalDate sourceUpdatedDate) {
    this.provenance = ProvenanceInfo.forOpenAlex(sourceCreatedDate, sourceUpdatedDate);
    markDirty();
    return this;
  }

  // ========== 标识符管理方法 ==========

  /// 添加标识符。
  ///
  /// 如果已存在相同类型和值的标识符，则忽略。
  /// 成功添加后会将聚合根标记为脏，触发版本号递增。
  ///
  /// @param identifier 标识符
  public void addIdentifier(VenueIdentifier identifier) {
    Assert.notNull(identifier, "标识符不能为空");

    // 检查是否已存在相同的标识符（基于 Record 的 equals）
    if (!identifiers.contains(identifier)) {
      identifiers.add(identifier);
      markDirty();
    }
  }

  /// 添加标识符（便捷方法）。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  public void addIdentifier(VenueIdentifierType type, String value) {
    addIdentifier(new VenueIdentifier(type, value));
  }

  /// 移除标识符。
  ///
  /// 成功移除后会将聚合根标记为脏，触发版本号递增。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @return 是否成功移除
  public boolean removeIdentifier(VenueIdentifierType type, String value) {
    VenueIdentifier target = new VenueIdentifier(type, value);
    boolean removed = identifiers.remove(target);
    if (removed) {
      markDirty();
    }
    return removed;
  }

  /// 获取特定类型的标识符值（返回第一个匹配）。
  ///
  /// @param type 标识符类型
  /// @return 标识符值，如果不存在则返回 empty
  public Optional<String> getIdentifier(VenueIdentifierType type) {
    return identifiers.stream()
        .filter(i -> i.type() == type)
        .map(VenueIdentifier::value)
        .findFirst();
  }

  /// 获取特定类型的所有标识符值。
  ///
  /// @param type 标识符类型
  /// @return 标识符值列表
  public List<String> getIdentifiers(VenueIdentifierType type) {
    return identifiers.stream().filter(i -> i.type() == type).map(VenueIdentifier::value).toList();
  }

  /// 获取所有标识符（不可变视图）。
  ///
  /// @return 标识符列表
  public List<VenueIdentifier> getIdentifiers() {
    return Collections.unmodifiableList(identifiers);
  }

  // ========== 便捷判断方法 ==========

  /// 判断是否为期刊。
  ///
  /// @return true 如果为期刊类型
  public boolean isJournal() {
    return venueType.isJournal();
  }

  /// 判断是否为仓库（预印本服务器等）。
  ///
  /// @return true 如果为仓库类型
  public boolean isRepository() {
    return venueType.isRepository();
  }

  /// 判断是否为会议。
  ///
  /// @return true 如果为会议类型
  public boolean isConference() {
    return venueType.isConference();
  }

  /// 判断是否来自 OpenAlex。
  ///
  /// @return true 如果来自 OpenAlex
  public boolean isFromOpenAlex() {
    return provenance != null && provenance.isFromOpenAlex();
  }

  /// 判断是否来自 PubMed。
  ///
  /// @return true 如果来自 PubMed
  public boolean isFromPubMed() {
    return provenance != null && provenance.isFromPubMed();
  }

  // ========== 不变量验证 ==========

  /// 验证聚合根的业务不变量。
  ///
  /// @throws IllegalStateException 如果不变量被违反
  @Override
  protected void assertInvariants() {
    // 载体类型不能为空
    if (venueType == null) {
      throw new IllegalStateException("载体类型不能为空");
    }

    // 名称不能为空
    if (StrUtil.isBlank(displayName)) {
      throw new IllegalStateException("显示名称不能为空");
    }

    // 来自 OpenAlex 的载体必须有 OPENALEX 类型的标识符
    if (provenance != null && provenance.isFromOpenAlex()) {
      boolean hasOpenAlexId =
          identifiers.stream().anyMatch(i -> i.type() == VenueIdentifierType.OPENALEX);
      if (!hasOpenAlexId) {
        throw new IllegalStateException("来自 OpenAlex 的载体必须提供 OpenAlex 标识符");
      }
    }
  }

  @Override
  public String toString() {
    String openalexId = getIdentifier(VenueIdentifierType.OPENALEX).orElse(null);
    String issnL = getIdentifier(VenueIdentifierType.ISSN_L).orElse(null);
    return String.format(
        "VenueAggregate[id=%d, type=%s, name=%s, openalexId=%s, issnL=%s]",
        getId(), venueType.getCode(), displayName, openalexId, issnL);
  }
}
