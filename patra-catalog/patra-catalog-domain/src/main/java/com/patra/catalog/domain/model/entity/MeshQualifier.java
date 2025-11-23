package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;

/// MeSH 限定词实体(独立主数据,不属于任何聚合根)。
///
/// 限定词说明：
///
/// - 限定词用于修饰主题词,提供更精确的检索维度
///   - 示例："Antibodies/immunology" 表示"抗体的免疫学方面"
///   - 限定词数量：约 80 个
///   - 每个限定词可应用于多个主题词
///
/// 业务规则：
///
/// - 限定词是主数据,必须先于主题词导入
///   - UI 格式：Q000001-Q999999
///   - 每个限定词有标准缩写(如 DI=诊断, GE=遗传学, IM=免疫学)
///   - active_status 标记限定词是否有效(0=已废弃, 1=有效)
///
/// 使用示例：
///
/// ```java
/// // 创建限定词
/// MeshQualifier qualifier = MeshQualifier.create(
///     MeshUI.qualifierOf(1),
///     "immunology",
///     "IM"
/// ).withAnnotation("Used with organs, animals...")
///  .withActiveStatus(true)
///  .withMeshVersion("2025");
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class MeshQualifier implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 标识符 ==========

  /// 主键ID(由Repository在持久化时分配)
  private Long id;

  /// 限定词唯一标识符(格式：Q000001-Q999999)
  private final MeshUI qualifierUi;

  // ========== 业务字段 ==========

  /// 限定词名称(英文)
  private final String name;

  /// 限定词缩写(如 DI, GE, IM)
  private final String abbreviation;

  /// 注释说明
  private String annotation;

  /// 创建日期(格式：YYYYMMDD)
  private String dateCreated;

  /// 修订日期(格式：YYYYMMDD)
  private String dateRevised;

  /// 确立日期(格式：YYYYMMDD)
  private String dateEstablished;

  /// 是否有效(false=已废弃, true=有效)
  private Boolean activeStatus;

  /// MeSH 版本年份(如 "2025")
  private String meshVersion;

  /// 私有构造函数。
  ///
  /// @param id 主键ID(新建时为null)
  /// @param qualifierUi 限定词UI
  /// @param name 限定词名称
  /// @param abbreviation 限定词缩写
  private MeshQualifier(Long id, MeshUI qualifierUi, String name, String abbreviation) {
    // 必填字段验证
    Assert.notNull(qualifierUi, "限定词UI不能为空");
    Assert.notBlank(name, "限定词名称不能为空");
    Assert.notBlank(abbreviation, "限定词缩写不能为空");

    // 限定词UI类型验证
    Assert.isTrue(qualifierUi.isQualifier(), "限定词UI必须以Q开头：%s", qualifierUi.ui());

    // 名称长度验证
    Assert.isTrue(name.length() <= 100, "限定词名称长度不能超过100个字符：%s", name);

    // 缩写长度验证
    Assert.isTrue(abbreviation.length() <= 10, "限定词缩写长度不能超过10个字符：%s", abbreviation);

    // 赋值
    this.id = id;
    this.qualifierUi = qualifierUi;
    this.name = name;
    this.abbreviation = abbreviation;
  }

  // ========== 工厂方法 ==========

  /// 创建限定词。
  ///
  /// @param qualifierUi 限定词UI
  /// @param name 限定词名称
  /// @param abbreviation 限定词缩写
  /// @return 限定词实体
  public static MeshQualifier create(MeshUI qualifierUi, String name, String abbreviation) {
    return new MeshQualifier(null, qualifierUi, name, abbreviation);
  }

  /// 从持久化状态重建实体(由Repository使用)。
  ///
  /// @param id 主键ID
  /// @param qualifierUi 限定词UI
  /// @param name 限定词名称
  /// @param abbreviation 限定词缩写
  /// @param annotation 注释说明
  /// @param dateCreated 创建日期
  /// @param dateRevised 修订日期
  /// @param dateEstablished 确立日期
  /// @param activeStatus 是否有效
  /// @param meshVersion MeSH版本
  /// @return 重建的实体
  public static MeshQualifier restore(
      Long id,
      MeshUI qualifierUi,
      String name,
      String abbreviation,
      String annotation,
      String dateCreated,
      String dateRevised,
      String dateEstablished,
      Boolean activeStatus,
      String meshVersion) {
    MeshQualifier qualifier = new MeshQualifier(id, qualifierUi, name, abbreviation);
    qualifier.annotation = annotation;
    qualifier.dateCreated = dateCreated;
    qualifier.dateRevised = dateRevised;
    qualifier.dateEstablished = dateEstablished;
    qualifier.activeStatus = activeStatus;
    qualifier.meshVersion = meshVersion;
    return qualifier;
  }

  // ========== 业务方法 ==========

  /// 设置ID(由Repository在持久化后回写)。
  ///
  /// @param id 主键ID
  public void assignId(Long id) {
    this.id = id;
  }

  /// 设置注释说明。
  ///
  /// @param annotation 注释说明
  /// @return 当前对象(支持链式调用)
  public MeshQualifier withAnnotation(String annotation) {
    this.annotation = annotation;
    return this;
  }

  /// 设置创建日期。
  ///
  /// @param dateCreated 创建日期(格式：YYYYMMDD)
  /// @return 当前对象(支持链式调用)
  public MeshQualifier withDateCreated(String dateCreated) {
    this.dateCreated = dateCreated;
    return this;
  }

  /// 设置修订日期。
  ///
  /// @param dateRevised 修订日期(格式：YYYYMMDD)
  /// @return 当前对象(支持链式调用)
  public MeshQualifier withDateRevised(String dateRevised) {
    this.dateRevised = dateRevised;
    return this;
  }

  /// 设置确立日期。
  ///
  /// @param dateEstablished 确立日期(格式：YYYYMMDD)
  /// @return 当前对象(支持链式调用)
  public MeshQualifier withDateEstablished(String dateEstablished) {
    this.dateEstablished = dateEstablished;
    return this;
  }

  /// 设置有效状态。
  ///
  /// @param activeStatus 是否有效
  /// @return 当前对象(支持链式调用)
  public MeshQualifier withActiveStatus(Boolean activeStatus) {
    this.activeStatus = activeStatus;
    return this;
  }

  /// 设置MeSH版本。
  ///
  /// @param meshVersion MeSH版本年份
  /// @return 当前对象(支持链式调用)
  public MeshQualifier withMeshVersion(String meshVersion) {
    if (StrUtil.isNotBlank(meshVersion)) {
      Assert.isTrue(meshVersion.matches("^\\d{4}$"), "MeSH版本必须是4位年份：%s", meshVersion);
    }
    this.meshVersion = meshVersion;
    return this;
  }

  /// 判断是否有效。
  ///
  /// @return true 如果限定词有效
  public boolean isActive() {
    return activeStatus != null && activeStatus;
  }

  /// 判断是否已废弃。
  ///
  /// @return true 如果限定词已废弃
  public boolean isDeprecated() {
    return activeStatus != null && !activeStatus;
  }

  /// 判断是否有注释。
  ///
  /// @return true 如果有注释
  public boolean hasAnnotation() {
    return StrUtil.isNotBlank(annotation);
  }

  @Override
  public String toString() {
    return String.format(
        "MeshQualifier[ui=%s, name=%s, abbr=%s, active=%b]",
        qualifierUi.ui(), name, abbreviation, isActive());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MeshQualifier that)) {
      return false;
    }
    return qualifierUi.equals(that.qualifierUi);
  }

  @Override
  public int hashCode() {
    return qualifierUi.hashCode();
  }
}
