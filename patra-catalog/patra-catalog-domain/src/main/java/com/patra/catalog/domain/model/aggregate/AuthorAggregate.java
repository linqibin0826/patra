package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.vo.author.AuthorId;
import com.patra.catalog.domain.model.vo.author.AuthorName;
import com.patra.catalog.domain.model.vo.author.Orcid;
import com.patra.catalog.domain.model.vo.common.DedupKey;
import com.patra.common.domain.AggregateRoot;
import java.io.Serial;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/// 作者聚合根。管理学术作者的基本信息、标识符和去重策略。
///
/// **一致性边界**：
///
/// - ORCID 全局唯一(如果提供)
///   - 去重键用于识别可能的重复作者
///   - 作者姓名不能全部为空
///   - 邮箱格式必须有效(如果提供)
///
/// **去重策略**：
///
/// **设计说明**：
///
/// - Author 和 Organization 是独立的聚合根
///   - 作者-机构关联通过 Repository 管理(不在聚合内)
///   - organizationName 是文本字段,不关联 Organization 聚合
///   - 去重键由应用层计算并设置
///
/// **业务规则**：
///
/// - ORCID 是最可靠的去重标识符
///   - valid 标志用于标记信息有效性(如已合并的重复作者)
///   - equalContribution 标志用于标记同等贡献作者
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class AuthorAggregate extends AggregateRoot<AuthorId> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 姓名信息 ==========

  /// 作者姓名(姓+名+缩写+后缀)
  private final AuthorName name;

  /// 机构名称(文本,不关联 Organization 表)
  @Setter(AccessLevel.PACKAGE)
  private String organizationName;

  // ========== 标识符 ==========

  /// ORCID 标识符(全局唯一,如果提供)
  private Orcid orcid;

  /// Researcher ID(ResearcherID/Publons)
  @Setter(AccessLevel.PACKAGE)
  private String researcherId;

  /// Scopus 作者 ID
  @Setter(AccessLevel.PACKAGE)
  private String scopusId;

  // ========== 联系方式 ==========

  /// 邮箱地址
  private String email;

  // ========== 去重和状态 ==========

  /// 复合去重键(MD5哈希,应用层计算)
  @Setter(AccessLevel.PACKAGE)
  private DedupKey dedupKey;

  /// 同等贡献标志(用于标记同等贡献作者)
  private boolean equalContribution;

  /// 信息是否有效(false=无效,如已合并的重复作者)
  private boolean valid;

  // ========== 扩展字段 ==========

  /// 作者元数据(JSON,灵活扩展)
  @Setter(AccessLevel.PACKAGE)
  private String metadataJson;

  /// 私有构造函数。
  ///
  /// @param id 主键ID(新建时为null)
  /// @param name 作者姓名
  /// @param organizationName 机构名称
  /// @param orcid ORCID 标识符
  /// @param researcherId Researcher ID
  /// @param scopusId Scopus ID
  /// @param email 邮箱地址
  /// @param dedupKey 去重键
  /// @param equalContribution 同等贡献标志
  /// @param valid 信息是否有效
  private AuthorAggregate(
      AuthorId id,
      AuthorName name,
      String organizationName,
      Orcid orcid,
      String researcherId,
      String scopusId,
      String email,
      DedupKey dedupKey,
      boolean equalContribution,
      boolean valid) {
    super(id);

    // 必填字段验证
    Assert.notNull(name, "作者姓名不能为空");

    // 邮箱格式验证(如果提供)
    if (StrUtil.isNotBlank(email)) {
      Assert.isTrue(email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"), "邮箱格式无效：%s", email);
    }

    // 赋值
    this.name = name;
    this.organizationName = organizationName;
    this.orcid = orcid;
    this.researcherId = researcherId;
    this.scopusId = scopusId;
    this.email = email;
    this.dedupKey = dedupKey;
    this.equalContribution = equalContribution;
    this.valid = valid;
  }

  // ========== 工厂方法 ==========

  /// 创建作者聚合根(最小信息)。
  ///
  /// @param name 作者姓名
  /// @return 作者聚合根
  public static AuthorAggregate create(AuthorName name) {
    return new AuthorAggregate(
        null, // 新建时ID为null
        name, null, null, null, null, null, null, false, true); // 新建时默认有效
  }

  /// 创建作者聚合根(含ORCID)。
  ///
  /// @param name 作者姓名
  /// @param orcid ORCID 标识符
  /// @return 作者聚合根
  public static AuthorAggregate create(AuthorName name, Orcid orcid) {
    return new AuthorAggregate(null, name, null, orcid, null, null, null, null, false, true);
  }

  /// 创建作者聚合根(含机构和邮箱)。
  ///
  /// @param name 作者姓名
  /// @param organizationName 机构名称
  /// @param email 邮箱地址
  /// @return 作者聚合根
  public static AuthorAggregate create(AuthorName name, String organizationName, String email) {
    return new AuthorAggregate(
        null, name, organizationName, null, null, null, email, null, false, true);
  }

  /// 从持久化状态重建聚合根(由Repository使用)。
  ///
  /// @param id 主键ID
  /// @param name 作者姓名
  /// @param organizationName 机构名称
  /// @param orcid ORCID 标识符
  /// @param researcherId Researcher ID
  /// @param scopusId Scopus ID
  /// @param email 邮箱地址
  /// @param dedupKey 去重键
  /// @param equalContribution 同等贡献标志
  /// @param valid 信息是否有效
  /// @param version 乐观锁版本
  /// @return 重建的聚合根
  public static AuthorAggregate restore(
      AuthorId id,
      AuthorName name,
      String organizationName,
      Orcid orcid,
      String researcherId,
      String scopusId,
      String email,
      DedupKey dedupKey,
      boolean equalContribution,
      boolean valid,
      Long version) {
    AuthorAggregate aggregate =
        new AuthorAggregate(
            id,
            name,
            organizationName,
            orcid,
            researcherId,
            scopusId,
            email,
            dedupKey,
            equalContribution,
            valid);
    aggregate.assignVersion(version != null ? version : 0L);
    return aggregate;
  }

  // ========== 业务方法 ==========

  /// 设置 ORCID 标识符。
  ///
  /// @param orcid ORCID 标识符
  public void setOrcid(Orcid orcid) {
    Assert.notNull(orcid, "ORCID 不能为空");
    Assert.isTrue(orcid.isChecksumValid(), "ORCID 校验位无效：%s", orcid.value());
    this.orcid = orcid;
  }

  /// 设置邮箱地址(含验证逻辑)。
  ///
  /// @param email 邮箱地址
  public void setEmail(String email) {
    if (StrUtil.isNotBlank(email)) {
      Assert.isTrue(email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"), "邮箱格式无效：%s", email);
    }
    this.email = email;
  }

  /// 标记为同等贡献作者。
  public void markAsEqualContribution() {
    this.equalContribution = true;
  }

  /// 取消同等贡献标记。
  public void unmarkEqualContribution() {
    this.equalContribution = false;
  }

  /// 标记为无效(如已合并的重复作者)。
  public void markAsInvalid() {
    this.valid = false;
  }

  /// 标记为有效。
  public void markAsValid() {
    this.valid = true;
  }

  // ========== 便捷判断方法 ==========

  /// 判断是否有 ORCID。
  ///
  /// @return true 如果有 ORCID
  public boolean hasOrcid() {
    return orcid != null;
  }

  /// 判断是否有邮箱。
  ///
  /// @return true 如果有邮箱
  public boolean hasEmail() {
    return StrUtil.isNotBlank(email);
  }

  /// 判断是否有机构名称。
  ///
  /// @return true 如果有机构名称
  public boolean hasOrganization() {
    return StrUtil.isNotBlank(organizationName);
  }

  /// 判断是否有去重键。
  ///
  /// @return true 如果有去重键
  public boolean hasDedupKey() {
    return dedupKey != null;
  }

  /// 判断是否为同等贡献作者。
  ///
  /// @return true 如果为同等贡献作者
  public boolean isEqualContribution() {
    return equalContribution;
  }

  /// 判断信息是否有效。
  ///
  /// @return true 如果有效
  public boolean isValid() {
    return valid;
  }

  /// 获取作者的显示名称。
  ///
  /// @return 作者显示名称
  public String getDisplayName() {
    return name.toDisplayString();
  }

  /// 获取作者的简短名称(用于引用)。
  ///
  /// @return 作者简短名称
  public String getShortName() {
    return name.toShortForm();
  }

  // ========== 不变量验证 ==========

  /// 验证聚合根的业务不变量。
  ///
  /// @throws IllegalStateException 如果不变量被违反
  @Override
  protected void assertInvariants() {
    // 姓名不能为空
    if (name == null) {
      throw new IllegalStateException("作者姓名不能为空");
    }

    // 邮箱格式验证
    if (StrUtil.isNotBlank(email)) {
      if (!email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
        throw new IllegalStateException("邮箱格式无效：" + email);
      }
    }

    // ORCID 校验位验证
    if (hasOrcid() && !orcid.isChecksumValid()) {
      throw new IllegalStateException("ORCID 校验位无效：" + orcid.value());
    }
  }

  @Override
  public String toString() {
    return String.format(
        "AuthorAggregate[id=%s, name=%s, orcid=%s, valid=%b]",
        getId(), getDisplayName(), hasOrcid() ? orcid.value() : "null", valid);
  }
}
