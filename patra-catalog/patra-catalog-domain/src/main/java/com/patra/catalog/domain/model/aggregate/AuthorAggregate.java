package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.enums.AuthorStatus;
import com.patra.catalog.domain.model.enums.DataSourceCode;
import com.patra.catalog.domain.model.vo.author.AuthorId;
import com.patra.catalog.domain.model.vo.author.AuthorNameVariant;
import com.patra.catalog.domain.model.vo.author.Orcid;
import com.patra.common.domain.AggregateRoot;
import java.io.Serial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

/// 作者聚合根。
///
/// 管理学术作者的基本信息、名字变体和 ORCID 标识符。
/// 设计适配 PubMed Computed Authors 数据源。
///
/// **聚合边界**：
///
/// - 名字变体（AuthorNameVariant）：值对象集合，随聚合持久化
/// - ORCID（Orcid）：值对象集合，随聚合持久化
///
/// **业务键**：
///
/// - `normalizedKey`：与 PubMed Computed Authors 的 name 字段对齐（如 "Lu+Z"）
///
/// **状态转换**：
///
/// ```
/// ACTIVE ──┬──> MERGED
///          └──> INACTIVE
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class AuthorAggregate extends AggregateRoot<AuthorId> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 核心属性 ==========

  /// 规范化标识（业务键），与 PubMed Computed Authors 的 name 字段对齐。
  ///
  /// 格式如 "Lu+Z"、"Smith+JK"，用于去重和外部数据源对齐。
  private final String normalizedKey;

  /// 数据来源代码。
  ///
  /// 标识作者数据最初来源于哪个系统（PUBMED、ORCID、OPENALEX 等）。
  private final DataSourceCode provenanceCode;

  /// 作者状态。
  ///
  /// ACTIVE：活跃状态（正常使用）
  /// MERGED：已合并状态（合并到其他作者）
  /// INACTIVE：已停用状态（标记为无效）
  private AuthorStatus status;

  /// 展示名称（冗余字段）。
  ///
  /// 从首个名字变体派生，格式如 "Zhiyong Lu"。
  /// 用于界面显示和搜索，避免每次都解析名字变体。
  private String displayName;

  /// 最后同步时间。
  ///
  /// 记录上次与外部数据源同步的时间（UTC）。
  private Instant lastSyncedAt;

  // ========== 聚合边界内集合 ==========

  /// 名字变体集合。
  ///
  /// 存储作者在不同文献中出现的各种名字形式，
  /// 解析自 PubMed Computed Authors 的 names 数组。
  private final List<AuthorNameVariant> nameVariants;

  /// ORCID 标识符列表。
  ///
  /// 支持一对多关系（少数作者有多个 ORCID）。
  /// 第一个添加的为主要 ORCID。
  private final List<Orcid> orcids;

  // ========== 私有构造函数 ==========

  /// 私有构造函数，通过工厂方法创建实例。
  ///
  /// @param id 主键 ID（新建时为 null）
  /// @param normalizedKey 规范化标识（业务键）
  /// @param displayName 展示名称
  /// @param provenanceCode 数据来源代码
  /// @param status 作者状态
  /// @param lastSyncedAt 最后同步时间
  /// @param version 乐观锁版本号
  private AuthorAggregate(
      AuthorId id,
      String normalizedKey,
      String displayName,
      DataSourceCode provenanceCode,
      AuthorStatus status,
      Instant lastSyncedAt,
      Long version) {
    super(id);

    // 必填字段验证
    Assert.notBlank(normalizedKey, "规范化标识不能为空");
    Assert.notNull(provenanceCode, "数据来源代码不能为空");
    Assert.notNull(status, "作者状态不能为空");

    this.normalizedKey = normalizedKey;
    this.displayName = displayName;
    this.provenanceCode = provenanceCode;
    this.status = status;
    this.lastSyncedAt = lastSyncedAt;
    this.nameVariants = new ArrayList<>();
    this.orcids = new ArrayList<>();

    // 设置版本号（重建时使用）
    if (version != null) {
      assignVersion(version);
    }
  }

  // ========== 工厂方法 ==========

  /// 从 PubMed Computed Authors 创建新的作者聚合根。
  ///
  /// @param normalizedKey 规范化标识（如 "Lu+Z"）
  /// @return 新建的作者聚合根
  /// @throws IllegalArgumentException 如果 normalizedKey 为空
  public static AuthorAggregate fromPubMedComputed(String normalizedKey) {
    return new AuthorAggregate(
        null, // 新建时 ID 为 null
        normalizedKey,
        null, // displayName 将从首个名字变体派生
        DataSourceCode.PUBMED,
        AuthorStatus.ACTIVE,
        null, // 尚未同步
        null // 新建时无版本号
        );
  }

  /// 从持久化状态重建聚合根。
  ///
  /// 由 Repository 使用，用于从数据库读取后重建领域对象。
  ///
  /// @param id 主键 ID
  /// @param normalizedKey 规范化标识
  /// @param displayName 展示名称
  /// @param provenanceCode 数据来源代码
  /// @param status 作者状态
  /// @param lastSyncedAt 最后同步时间
  /// @param version 乐观锁版本号
  /// @return 重建的聚合根
  public static AuthorAggregate restore(
      AuthorId id,
      String normalizedKey,
      String displayName,
      DataSourceCode provenanceCode,
      AuthorStatus status,
      Instant lastSyncedAt,
      Long version) {
    Assert.notNull(id, "重建时 ID 不能为空");
    return new AuthorAggregate(
        id, normalizedKey, displayName, provenanceCode, status, lastSyncedAt, version);
  }

  // ========== 名字变体管理 ==========

  /// 添加名字变体。
  ///
  /// 如果是首个名字变体，会自动更新展示名称。
  ///
  /// @param variant 名字变体
  /// @throws IllegalArgumentException 如果 variant 为 null
  public void addNameVariant(AuthorNameVariant variant) {
    Assert.notNull(variant, "名字变体不能为空");

    nameVariants.add(variant);

    // 首个名字变体时更新展示名称
    if (nameVariants.size() == 1) {
      updateDisplayNameFromVariant(variant);
    }
  }

  /// 批量设置名字变体。
  ///
  /// 清空现有变体后添加新变体，支持链式调用。
  ///
  /// @param variants 名字变体列表
  /// @return 当前聚合根实例（支持链式调用）
  public AuthorAggregate withNameVariants(List<AuthorNameVariant> variants) {
    // 清空现有变体
    nameVariants.clear();

    // 添加新变体
    if (variants != null && !variants.isEmpty()) {
      for (AuthorNameVariant variant : variants) {
        nameVariants.add(variant);
      }
      // 从首个变体更新展示名称
      updateDisplayNameFromVariant(variants.getFirst());
    }

    return this;
  }

  /// 获取名字变体的不可变视图。
  ///
  /// @return 名字变体列表的不可变副本
  public List<AuthorNameVariant> getNameVariants() {
    return Collections.unmodifiableList(nameVariants);
  }

  /// 从名字变体更新展示名称。
  ///
  /// @param variant 名字变体
  private void updateDisplayNameFromVariant(AuthorNameVariant variant) {
    this.displayName = variant.toDisplayString();
  }

  // ========== ORCID 管理 ==========

  /// 添加 ORCID。
  ///
  /// @param orcid ORCID 标识符
  /// @throws IllegalArgumentException 如果 orcid 为 null
  public void addOrcid(Orcid orcid) {
    Assert.notNull(orcid, "ORCID 不能为空");

    orcids.add(orcid);
  }

  /// 批量设置 ORCID。
  ///
  /// 清空现有 ORCID 后添加新的，支持链式调用。
  ///
  /// @param orcidList ORCID 列表
  /// @return 当前聚合根实例（支持链式调用）
  public AuthorAggregate withOrcids(List<Orcid> orcidList) {
    // 清空现有 ORCID
    orcids.clear();

    // 添加新 ORCID
    if (orcidList != null && !orcidList.isEmpty()) {
      for (Orcid orcid : orcidList) {
        orcids.add(orcid);
      }
    }

    return this;
  }

  /// 获取 ORCID 列表的不可变视图。
  ///
  /// @return ORCID 列表的不可变副本
  public List<Orcid> getOrcids() {
    return Collections.unmodifiableList(orcids);
  }

  /// 判断是否有 ORCID。
  ///
  /// @return true 如果有至少一个 ORCID
  public boolean hasOrcid() {
    return !orcids.isEmpty();
  }

  /// 获取主要 ORCID（第一个添加的）。
  ///
  /// @return 主要 ORCID，如果没有则返回空 Optional
  public Optional<Orcid> getPrimaryOrcid() {
    if (orcids.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(orcids.getFirst());
  }

  // ========== 状态转换 ==========

  /// 标记为已合并状态。
  ///
  /// 当作者被合并到另一个作者记录时调用。
  public void markAsMerged() {
    this.status = AuthorStatus.MERGED;
  }

  /// 标记为已停用状态。
  ///
  /// 当作者记录被标记为无效时调用。
  public void markAsInactive() {
    this.status = AuthorStatus.INACTIVE;
  }

  /// 判断是否为活跃状态。
  ///
  /// @return true 如果为活跃状态
  public boolean isActive() {
    return status == AuthorStatus.ACTIVE;
  }

  // ========== 同步时间管理 ==========

  /// 更新最后同步时间。
  ///
  /// @param syncTime 同步时间
  public void updateLastSyncedAt(Instant syncTime) {
    this.lastSyncedAt = syncTime;
  }

  /// 标记已同步（使用当前时间）。
  public void markSynced() {
    this.lastSyncedAt = Instant.now();
  }

  // ========== 不变量验证 ==========

  /// 验证聚合根的业务不变量。
  ///
  /// @throws IllegalStateException 如果不变量被违反
  @Override
  protected void assertInvariants() {
    if (normalizedKey == null || normalizedKey.isBlank()) {
      throw new IllegalStateException("规范化标识不能为空");
    }
    if (provenanceCode == null) {
      throw new IllegalStateException("数据来源代码不能为空");
    }
    if (status == null) {
      throw new IllegalStateException("作者状态不能为空");
    }
  }

  @Override
  public String toString() {
    return String.format(
        "AuthorAggregate[id=%s, normalizedKey=%s, displayName=%s, status=%s, orcids=%d]",
        getId(), normalizedKey, displayName, status, orcids.size());
  }
}
