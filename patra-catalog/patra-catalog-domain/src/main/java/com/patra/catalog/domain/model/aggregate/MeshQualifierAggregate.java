package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.common.domain.AggregateRoot;
import java.io.Serial;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/// MeSH 限定词聚合根。管理 NLM MeSH 限定词主数据。
///
/// **一致性边界**：
///
/// - Qualifier UI 全局唯一
///   - 限定词名称和缩写不能为空
///   - 版本号格式必须为4位年份(如"2025")
///
/// **业务规则**：
///
/// - 限定词是主数据,必须先于主题词导入
///   - UI 格式：Q000001-Q999999
///   - 每个限定词有标准缩写(如 DI=诊断, GE=遗传学, IM=免疫学)
///   - activeStatus=false 表示限定词已废弃
///   - 约 80 个限定词，数量稳定
///   - meshVersion 每年更新一次
///
/// **设计说明**：
///
/// - 限定词用于修饰主题词,提供更精确的检索维度
///   - 示例："Antibodies/immunology" 表示"抗体的免疫学方面"
///   - 每个限定词可应用于多个主题词
///   - 限定词独立管理，不依赖其他聚合根
///
/// 使用示例：
///
/// ```java
/// // 创建限定词
/// MeshQualifierAggregate qualifier = MeshQualifierAggregate.create(
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
public class MeshQualifierAggregate extends AggregateRoot<Long> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 标识符 ==========

  /// 限定词唯一标识符(格式：Q000001-Q999999)
  private final MeshUI qualifierUi;

  // ========== 业务字段 ==========

  /// 限定词名称(英文)
  private final String name;

  /// 限定词缩写(如 DI, GE, IM)
  private final String abbreviation;

  /// 注释说明
  private String annotation;

  /// 创建日期
  private LocalDate dateCreated;

  /// 修订日期
  private LocalDate dateRevised;

  /// 确立日期
  private LocalDate dateEstablished;

  /// 是否有效(false=已废弃, true=有效)
  private Boolean activeStatus;

  /// MeSH 版本年份(如 "2025")
  private String meshVersion;

  /// 历史说明（记录限定词的历史使用规则）
  private String historyNote;

  /// 在线检索说明（检索策略指南）
  private String onlineNote;

  /// 树形编号列表（限定词在 MeSH 层级树中的位置）
  private List<String> treeNumbers = new ArrayList<>();

  /// 私有构造函数。
  ///
  /// @param id 主键ID(新建时为null)
  /// @param qualifierUi 限定词UI
  /// @param name 限定词名称
  /// @param abbreviation 限定词缩写
  private MeshQualifierAggregate(Long id, MeshUI qualifierUi, String name, String abbreviation) {
    super(id);

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
    this.qualifierUi = qualifierUi;
    this.name = name;
    this.abbreviation = abbreviation;
  }

  // ========== 工厂方法 ==========

  /// 创建限定词聚合根。
  ///
  /// 新创建的限定词默认为有效状态（activeStatus = true）。
  ///
  /// @param qualifierUi 限定词UI
  /// @param name 限定词名称
  /// @param abbreviation 限定词缩写
  /// @return 限定词聚合根
  public static MeshQualifierAggregate create(
      MeshUI qualifierUi, String name, String abbreviation) {
    return new MeshQualifierAggregate(null, qualifierUi, name, abbreviation)
        .withActiveStatus(true); // 默认有效
  }

  /// 从持久化状态重建聚合根(由Repository使用)。
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
  /// @param historyNote 历史说明
  /// @param onlineNote 在线检索说明
  /// @param treeNumbers 树形编号列表
  /// @return 重建的聚合根
  public static MeshQualifierAggregate restore(
      Long id,
      MeshUI qualifierUi,
      String name,
      String abbreviation,
      String annotation,
      LocalDate dateCreated,
      LocalDate dateRevised,
      LocalDate dateEstablished,
      Boolean activeStatus,
      String meshVersion,
      String historyNote,
      String onlineNote,
      List<String> treeNumbers) {
    MeshQualifierAggregate qualifier =
        new MeshQualifierAggregate(id, qualifierUi, name, abbreviation);
    qualifier.annotation = annotation;
    qualifier.dateCreated = dateCreated;
    qualifier.dateRevised = dateRevised;
    qualifier.dateEstablished = dateEstablished;
    qualifier.activeStatus = activeStatus;
    qualifier.meshVersion = meshVersion;
    qualifier.historyNote = historyNote;
    qualifier.onlineNote = onlineNote;
    qualifier.treeNumbers = treeNumbers != null ? new ArrayList<>(treeNumbers) : new ArrayList<>();
    return qualifier;
  }

  // ========== 业务方法 ==========

  /// 设置注释说明。
  ///
  /// @param annotation 注释说明
  /// @return 当前对象(支持链式调用)
  public MeshQualifierAggregate withAnnotation(String annotation) {
    this.annotation = annotation;
    return this;
  }

  /// 设置创建日期。
  ///
  /// @param dateCreated 创建日期
  /// @return 当前对象(支持链式调用)
  public MeshQualifierAggregate withDateCreated(LocalDate dateCreated) {
    this.dateCreated = dateCreated;
    return this;
  }

  /// 设置修订日期。
  ///
  /// @param dateRevised 修订日期
  /// @return 当前对象(支持链式调用)
  public MeshQualifierAggregate withDateRevised(LocalDate dateRevised) {
    this.dateRevised = dateRevised;
    return this;
  }

  /// 设置确立日期。
  ///
  /// @param dateEstablished 确立日期
  /// @return 当前对象(支持链式调用)
  public MeshQualifierAggregate withDateEstablished(LocalDate dateEstablished) {
    this.dateEstablished = dateEstablished;
    return this;
  }

  /// 设置有效状态。
  ///
  /// @param activeStatus 是否有效
  /// @return 当前对象(支持链式调用)
  public MeshQualifierAggregate withActiveStatus(Boolean activeStatus) {
    this.activeStatus = activeStatus;
    return this;
  }

  /// 设置MeSH版本。
  ///
  /// @param meshVersion MeSH版本年份
  /// @return 当前对象(支持链式调用)
  public MeshQualifierAggregate withMeshVersion(String meshVersion) {
    if (StrUtil.isNotBlank(meshVersion)) {
      Assert.isTrue(meshVersion.matches("^\\d{4}$"), "MeSH版本必须是4位年份：%s", meshVersion);
    }
    this.meshVersion = meshVersion;
    return this;
  }

  /// 设置历史说明。
  ///
  /// @param historyNote 历史说明
  /// @return 当前对象(支持链式调用)
  public MeshQualifierAggregate withHistoryNote(String historyNote) {
    this.historyNote = historyNote;
    return this;
  }

  /// 设置在线检索说明。
  ///
  /// @param onlineNote 在线检索说明
  /// @return 当前对象(支持链式调用)
  public MeshQualifierAggregate withOnlineNote(String onlineNote) {
    this.onlineNote = onlineNote;
    return this;
  }

  /// 设置树形编号列表。
  ///
  /// @param treeNumbers 树形编号列表
  /// @return 当前对象(支持链式调用)
  public MeshQualifierAggregate withTreeNumbers(List<String> treeNumbers) {
    this.treeNumbers = treeNumbers != null ? new ArrayList<>(treeNumbers) : new ArrayList<>();
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
        "MeshQualifierAggregate[ui=%s, name=%s, abbr=%s, active=%b]",
        qualifierUi.ui(), name, abbreviation, isActive());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MeshQualifierAggregate that)) {
      return false;
    }
    return qualifierUi.equals(that.qualifierUi);
  }

  @Override
  public int hashCode() {
    return qualifierUi.hashCode();
  }
}
