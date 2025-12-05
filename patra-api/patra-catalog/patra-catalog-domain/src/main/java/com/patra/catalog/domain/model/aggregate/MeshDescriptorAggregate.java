package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.enums.DescriptorClass;
import com.patra.catalog.domain.model.vo.mesh.AllowableQualifier;
import com.patra.catalog.domain.model.vo.mesh.EntryCombination;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.domain.model.vo.mesh.PharmacologicalAction;
import com.patra.catalog.domain.model.vo.mesh.SeeRelatedDescriptor;
import com.patra.common.domain.AggregateRoot;
import java.io.Serial;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

/// MeSH 主题词聚合根。管理 NLM MeSH(医学主题词表)的核心信息。
///
/// **一致性边界**：
///
/// - MeSH UI 全局唯一
///   - 每个主题词必须有至少一个树形位置
///   - 每个主题词必须有且仅有一个首选概念
///   - 主题词名称不能为空
///   - 版本号格式必须为4位年份(如"2025")
///
/// **业务规则**：
///
/// - 一个主题词平均有 2.3 个树形位置(支持多角度分类)
///   - 一个主题词平均有 7-8 个入口术语(同义词)
///   - 一个主题词平均有 5-6 个概念
///   - activeStatus=false 表示主题词已废弃
///   - meshVersion 每年更新一次
///
/// **设计说明**：
///
/// - TreeNumber、EntryTerm、Concept 作为聚合内实体管理
///   - 聚合内集合采用不可变视图对外暴露
///   - 通过业务方法管理聚合内实体的添加/删除
///   - MeshQualifier 是独立的聚合根,不在此聚合内
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
public class MeshDescriptorAggregate extends AggregateRoot<Long> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 基本信息 ==========

  /// MeSH 唯一标识符(格式：D000001-D999999)
  private final MeshUI ui;

  /// 主题词名称(首选术语,英文)
  private final String name;

  /// 主题词类型(1-Topical/2-PublicationType/3-Geographicals/4-CheckTag)
  private final DescriptorClass descriptorClass;

  /// 是否有效(false=已废弃,true=有效)
  private boolean activeStatus;

  /// MeSH 版本年份(如"2025")
  private String meshVersion;

  // ========== 说明文本 ==========

  /// 范围说明(定义和使用指南)
  private String scopeNote;

  /// 注释(索引员使用的说明)
  private String annotation;

  /// 之前的索引方式(历史参考)
  private String previousIndexing;

  /// 公共 MeSH 注释(面向用户)
  private String publicMeshNote;

  /// 另请参考(相关主题词建议)
  private String considerAlso;

  /// 历史记录(变更历史和使用指南)
  private String historyNote;

  /// 在线检索说明(如何在PubMed中使用此主题词)
  private String onlineNote;

  /// NLM分类号(用于图书馆分类)
  private String nlmClassificationNumber;

  // ========== 日期信息 ==========

  /// 创建日期
  private LocalDate dateCreated;

  /// 修订日期
  private LocalDate dateRevised;

  /// 确立日期
  private LocalDate dateEstablished;

  // ========== 聚合内实体集合 ==========

  /// 树形编号列表(平均 2.3 个)
  private final List<MeshTreeNumber> treeNumbers;

  /// 入口术语列表(平均 7-8 个)
  private final List<MeshEntryTerm> entryTerms;

  /// 概念列表(平均 5-6 个,必须有一个首选概念)
  private final List<MeshConcept> concepts;

  /// 允许的限定词列表(平均 23 个)
  private final List<AllowableQualifier> allowableQualifiers;

  /// 药理作用列表(较少,约 0.2 个/主题词)
  private final List<PharmacologicalAction> pharmacologicalActions;

  /// 之前索引列表(历史数据,约 0.2 个/主题词)
  private final List<String> previousIndexings;

  /// 相关主题词列表(See Related,约 0.2 个/主题词)
  private final List<SeeRelatedDescriptor> seeRelatedDescriptors;

  /// 组合条目列表(EntryCombinationList,约 0.1 个/主题词)
  private final List<EntryCombination> entryCombinations;

  // ========== 扩展字段 ==========

  /// 元数据(JSON,灵活扩展)
  private String metadataJson;

  /// 私有构造函数。
  ///
  /// @param id 主键ID(新建时为null)
  /// @param ui MeSH 唯一标识符
  /// @param name 主题词名称
  /// @param descriptorClass 主题词类型
  /// @param activeStatus 是否有效
  /// @param meshVersion MeSH 版本
  private MeshDescriptorAggregate(
      Long id,
      MeshUI ui,
      String name,
      DescriptorClass descriptorClass,
      boolean activeStatus,
      String meshVersion) {
    super(id);

    // 必填字段验证
    Assert.notNull(ui, "MeSH UI不能为空");
    Assert.isTrue(ui.isDescriptor(), "UI必须是Descriptor类型(D开头)：%s", ui.ui());
    Assert.notBlank(name, "主题词名称不能为空");
    Assert.notNull(descriptorClass, "主题词类型不能为空");

    // 版本格式验证(如果提供)
    if (StrUtil.isNotBlank(meshVersion)) {
      Assert.isTrue(meshVersion.matches("^\\d{4}$"), "MeSH版本格式无效,必须为4位年份：%s", meshVersion);
    }

    // 赋值
    this.ui = ui;
    this.name = name;
    this.descriptorClass = descriptorClass;
    this.activeStatus = activeStatus;
    this.meshVersion = meshVersion;

    // 初始化集合
    this.treeNumbers = new ArrayList<>();
    this.entryTerms = new ArrayList<>();
    this.concepts = new ArrayList<>();
    this.allowableQualifiers = new ArrayList<>();
    this.pharmacologicalActions = new ArrayList<>();
    this.previousIndexings = new ArrayList<>();
    this.seeRelatedDescriptors = new ArrayList<>();
    this.entryCombinations = new ArrayList<>();
  }

  // ========== 工厂方法 ==========

  /// 创建主题词聚合根。
  ///
  /// @param ui MeSH 唯一标识符
  /// @param name 主题词名称
  /// @param descriptorClass 主题词类型
  /// @param meshVersion MeSH 版本
  /// @return 主题词聚合根
  public static MeshDescriptorAggregate create(
      MeshUI ui, String name, DescriptorClass descriptorClass, String meshVersion) {
    return new MeshDescriptorAggregate(
        null, // 新建时ID为null
        ui,
        name,
        descriptorClass,
        true, // 新建时默认有效
        meshVersion);
  }

  /// 从持久化状态重建聚合根(由Repository使用)。
  ///
  /// @param id 主键ID
  /// @param ui MeSH 唯一标识符
  /// @param name 主题词名称
  /// @param descriptorClass 主题词类型
  /// @param activeStatus 是否有效
  /// @param meshVersion MeSH 版本
  /// @param scopeNote 范围说明
  /// @param annotation 注释
  /// @param previousIndexing 之前的索引方式
  /// @param publicMeshNote 公共注释
  /// @param considerAlso 另请参考
  /// @param dateCreated 创建日期
  /// @param dateRevised 修订日期
  /// @param dateEstablished 确立日期
  /// @param version 乐观锁版本
  /// @return 重建的聚合根
  public static MeshDescriptorAggregate restore(
      Long id,
      MeshUI ui,
      String name,
      DescriptorClass descriptorClass,
      boolean activeStatus,
      String meshVersion,
      String scopeNote,
      String annotation,
      String previousIndexing,
      String publicMeshNote,
      String considerAlso,
      LocalDate dateCreated,
      LocalDate dateRevised,
      LocalDate dateEstablished,
      Long version) {
    MeshDescriptorAggregate aggregate =
        new MeshDescriptorAggregate(id, ui, name, descriptorClass, activeStatus, meshVersion);

    // 设置可选字段
    aggregate.scopeNote = scopeNote;
    aggregate.annotation = annotation;
    aggregate.previousIndexing = previousIndexing;
    aggregate.publicMeshNote = publicMeshNote;
    aggregate.considerAlso = considerAlso;
    aggregate.dateCreated = dateCreated;
    aggregate.dateRevised = dateRevised;
    aggregate.dateEstablished = dateEstablished;

    // 注意：新增的字段（historyNote, onlineNote, nlmClassificationNumber）
    // 以及新增的集合（allowableQualifiers, pharmacologicalActions, etc.）
    // 在 restore 后通过 setter 方法设置

    aggregate.assignVersion(version);
    return aggregate;
  }

  // ========== 树形编号管理 ==========

  /// 添加树形编号。
  ///
  /// @param treeNumber 树形编号
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addTreeNumber(MeshTreeNumber treeNumber) {
    Assert.notNull(treeNumber, "树形编号不能为空");

    // 检查是否已存在相同的树形编号
    boolean exists =
        treeNumbers.stream().anyMatch(tn -> tn.getTreeNumber().equals(treeNumber.getTreeNumber()));

    if (!exists) {
      treeNumbers.add(treeNumber);
    }

    return this;
  }

  /// 批量添加树形编号。
  ///
  /// @param treeNumberList 树形编号列表
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addTreeNumbers(List<MeshTreeNumber> treeNumberList) {
    if (treeNumberList != null && !treeNumberList.isEmpty()) {
      treeNumberList.forEach(this::addTreeNumber);
    }
    return this;
  }

  /// 移除树形编号。
  ///
  /// @param treeNumberValue 树形编号值
  public void removeTreeNumber(String treeNumberValue) {
    treeNumbers.removeIf(tn -> tn.getTreeNumber().equals(treeNumberValue));
  }

  /// 获取主要树形编号。
  ///
  /// @return 主要树形编号,如果不存在则返回空
  public Optional<MeshTreeNumber> getPrimaryTreeNumber() {
    return treeNumbers.stream().filter(MeshTreeNumber::isPrimary).findFirst();
  }

  /// 获取树形编号列表(不可变视图)。
  ///
  /// @return 树形编号列表
  public List<MeshTreeNumber> getTreeNumbers() {
    return Collections.unmodifiableList(treeNumbers);
  }

  // ========== 入口术语管理 ==========

  /// 添加入口术语。
  ///
  /// @param entryTerm 入口术语
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addEntryTerm(MeshEntryTerm entryTerm) {
    Assert.notNull(entryTerm, "入口术语不能为空");

    // 检查是否已存在相同的术语
    boolean exists = entryTerms.stream().anyMatch(et -> et.getTerm().equals(entryTerm.getTerm()));

    if (!exists) {
      entryTerms.add(entryTerm);
    }

    return this;
  }

  /// 批量添加入口术语。
  ///
  /// @param entryTermList 入口术语列表
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addEntryTerms(List<MeshEntryTerm> entryTermList) {
    if (entryTermList != null && !entryTermList.isEmpty()) {
      entryTermList.forEach(this::addEntryTerm);
    }
    return this;
  }

  /// 移除入口术语。
  ///
  /// @param term 术语文本
  public void removeEntryTerm(String term) {
    entryTerms.removeIf(et -> et.getTerm().equals(term));
  }

  /// 获取首选入口术语。
  ///
  /// @return 首选入口术语,如果不存在则返回空
  public Optional<MeshEntryTerm> getPreferredEntryTerm() {
    return entryTerms.stream().filter(MeshEntryTerm::isPreferred).findFirst();
  }

  /// 获取入口术语列表(不可变视图)。
  ///
  /// @return 入口术语列表
  public List<MeshEntryTerm> getEntryTerms() {
    return Collections.unmodifiableList(entryTerms);
  }

  // ========== 概念管理 ==========

  /// 添加概念。
  ///
  /// @param concept 概念
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addConcept(MeshConcept concept) {
    Assert.notNull(concept, "概念不能为空");

    // 如果是首选概念,确保只有一个首选概念
    if (concept.isPreferred()) {
      long preferredCount = concepts.stream().filter(MeshConcept::isPreferred).count();

      Assert.isTrue(preferredCount == 0, "已存在首选概念,一个主题词只能有一个首选概念");
    }

    // 检查是否已存在相同UI的概念
    boolean exists =
        concepts.stream().anyMatch(c -> c.getConceptUi().equals(concept.getConceptUi()));

    if (!exists) {
      concepts.add(concept);
    }

    return this;
  }

  /// 批量添加概念。
  ///
  /// @param conceptList 概念列表
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addConcepts(List<MeshConcept> conceptList) {
    if (conceptList != null && !conceptList.isEmpty()) {
      conceptList.forEach(this::addConcept);
    }
    return this;
  }

  /// 移除概念。
  ///
  /// @param conceptUi 概念UI
  public void removeConcept(MeshUI conceptUi) {
    MeshConcept toRemove =
        concepts.stream().filter(c -> c.getConceptUi().equals(conceptUi)).findFirst().orElse(null);

    if (toRemove != null) {
      Assert.isTrue(!toRemove.isPreferred(), "不能删除首选概念,每个主题词必须有一个首选概念");
      concepts.remove(toRemove);
    }
  }

  /// 获取首选概念。
  ///
  /// @return 首选概念,如果不存在则返回空
  public Optional<MeshConcept> getPreferredConcept() {
    return concepts.stream().filter(MeshConcept::isPreferred).findFirst();
  }

  /// 获取概念列表(不可变视图)。
  ///
  /// @return 概念列表
  public List<MeshConcept> getConcepts() {
    return Collections.unmodifiableList(concepts);
  }

  // ========== 业务方法 ==========

  /// 废弃主题词。
  public void deprecate() {
    this.activeStatus = false;
  }

  /// 激活主题词。
  public void activate() {
    this.activeStatus = true;
  }

  /// 更新版本。
  ///
  /// @param newVersion 新版本(如"2026")
  public void updateMeshVersion(String newVersion) {
    Assert.notBlank(newVersion, "版本不能为空");
    Assert.isTrue(newVersion.matches("^\\d{4}$"), "版本格式无效,必须为4位年份：%s", newVersion);
    this.meshVersion = newVersion;
  }

  // ========== 允许的限定词管理 ==========

  /// 添加允许的限定词。
  ///
  /// @param qualifier 允许的限定词
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addAllowableQualifier(AllowableQualifier qualifier) {
    Assert.notNull(qualifier, "允许的限定词不能为空");

    // 检查是否已存在相同的限定词
    boolean exists =
        allowableQualifiers.stream().anyMatch(q -> q.qualifierUi().equals(qualifier.qualifierUi()));

    if (!exists) {
      allowableQualifiers.add(qualifier);
    }

    return this;
  }

  /// 批量添加允许的限定词。
  ///
  /// @param qualifierList 允许的限定词列表
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addAllowableQualifiers(List<AllowableQualifier> qualifierList) {
    if (qualifierList != null && !qualifierList.isEmpty()) {
      qualifierList.forEach(this::addAllowableQualifier);
    }
    return this;
  }

  /// 获取允许的限定词列表(不可变视图)。
  ///
  /// @return 允许的限定词列表
  public List<AllowableQualifier> getAllowableQualifiers() {
    return Collections.unmodifiableList(allowableQualifiers);
  }

  // ========== 药理作用管理 ==========

  /// 添加药理作用。
  ///
  /// @param action 药理作用
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addPharmacologicalAction(PharmacologicalAction action) {
    Assert.notNull(action, "药理作用不能为空");

    // 检查是否已存在相同的药理作用
    boolean exists =
        pharmacologicalActions.stream()
            .anyMatch(pa -> pa.descriptorUi().equals(action.descriptorUi()));

    if (!exists) {
      pharmacologicalActions.add(action);
    }

    return this;
  }

  /// 批量添加药理作用。
  ///
  /// @param actionList 药理作用列表
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addPharmacologicalActions(List<PharmacologicalAction> actionList) {
    if (actionList != null && !actionList.isEmpty()) {
      actionList.forEach(this::addPharmacologicalAction);
    }
    return this;
  }

  /// 获取药理作用列表(不可变视图)。
  ///
  /// @return 药理作用列表
  public List<PharmacologicalAction> getPharmacologicalActions() {
    return Collections.unmodifiableList(pharmacologicalActions);
  }

  // ========== 之前索引管理 ==========

  /// 添加之前索引。
  ///
  /// @param previousIndexing 之前索引
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addPreviousIndexing(String previousIndexing) {
    Assert.notBlank(previousIndexing, "之前索引不能为空");

    if (!previousIndexings.contains(previousIndexing)) {
      previousIndexings.add(previousIndexing);
    }

    return this;
  }

  /// 批量添加之前索引。
  ///
  /// @param indexingList 之前索引列表
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addPreviousIndexings(List<String> indexingList) {
    if (indexingList != null && !indexingList.isEmpty()) {
      indexingList.forEach(this::addPreviousIndexing);
    }
    return this;
  }

  /// 获取之前索引列表(不可变视图)。
  ///
  /// @return 之前索引列表
  public List<String> getPreviousIndexings() {
    return Collections.unmodifiableList(previousIndexings);
  }

  // ========== 相关主题词管理 ==========

  /// 添加相关主题词。
  ///
  /// @param descriptor 相关主题词
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addSeeRelatedDescriptor(SeeRelatedDescriptor descriptor) {
    Assert.notNull(descriptor, "相关主题词不能为空");

    // 检查是否已存在相同的相关主题词
    boolean exists =
        seeRelatedDescriptors.stream()
            .anyMatch(srd -> srd.descriptorUi().equals(descriptor.descriptorUi()));

    if (!exists) {
      seeRelatedDescriptors.add(descriptor);
    }

    return this;
  }

  /// 批量添加相关主题词。
  ///
  /// @param descriptorList 相关主题词列表
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addSeeRelatedDescriptors(
      List<SeeRelatedDescriptor> descriptorList) {
    if (descriptorList != null && !descriptorList.isEmpty()) {
      descriptorList.forEach(this::addSeeRelatedDescriptor);
    }
    return this;
  }

  /// 获取相关主题词列表(不可变视图)。
  ///
  /// @return 相关主题词列表
  public List<SeeRelatedDescriptor> getSeeRelatedDescriptors() {
    return Collections.unmodifiableList(seeRelatedDescriptors);
  }

  // ========== 组合条目管理 ==========

  /// 添加组合条目。
  ///
  /// @param entryCombination 组合条目
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addEntryCombination(EntryCombination entryCombination) {
    Assert.notNull(entryCombination, "组合条目不能为空");

    // 检查是否已存在相同的组合条目
    boolean exists =
        entryCombinations.stream()
            .anyMatch(
                ec ->
                    ec.ecinDescriptorUi().equals(entryCombination.ecinDescriptorUi())
                        && ec.ecinQualifierUi().equals(entryCombination.ecinQualifierUi()));

    if (!exists) {
      entryCombinations.add(entryCombination);
    }

    return this;
  }

  /// 批量添加组合条目。
  ///
  /// @param entryCombinationList 组合条目列表
  /// @return 当前聚合根(支持链式调用)
  public MeshDescriptorAggregate addEntryCombinations(List<EntryCombination> entryCombinationList) {
    if (entryCombinationList != null && !entryCombinationList.isEmpty()) {
      entryCombinationList.forEach(this::addEntryCombination);
    }
    return this;
  }

  /// 获取组合条目列表(不可变视图)。
  ///
  /// @return 组合条目列表
  public List<EntryCombination> getEntryCombinations() {
    return Collections.unmodifiableList(entryCombinations);
  }

  // ========== 便捷判断方法 ==========

  /// 判断是否为主题词类型。
  ///
  /// @return true 如果为主题词类型
  public boolean isTopical() {
    return descriptorClass.isTopical();
  }

  /// 判断是否为出版类型。
  ///
  /// @return true 如果为出版类型
  public boolean isPublicationType() {
    return descriptorClass.isPublicationType();
  }

  /// 判断是否有效。
  ///
  /// @return true 如果有效
  public boolean isActive() {
    return activeStatus;
  }

  /// 判断是否有范围说明。
  ///
  /// @return true 如果有范围说明
  public boolean hasScopeNote() {
    return StrUtil.isNotBlank(scopeNote);
  }

  // ========== 不变量验证 ==========

  /// 验证聚合根的业务不变量。
  ///
  /// @throws IllegalStateException 如果不变量被违反
  @Override
  protected void assertInvariants() {
    // UI 不能为空
    if (ui == null) {
      throw new IllegalStateException("MeSH UI不能为空");
    }

    // 名称不能为空
    if (StrUtil.isBlank(name)) {
      throw new IllegalStateException("主题词名称不能为空");
    }

    // 主题词类型不能为空
    if (descriptorClass == null) {
      throw new IllegalStateException("主题词类型不能为空");
    }

    // 必须至少有一个树形编号
    if (treeNumbers.isEmpty()) {
      throw new IllegalStateException("主题词必须至少有一个树形编号");
    }

    // 必须有且仅有一个首选概念
    long preferredCount = concepts.stream().filter(MeshConcept::isPreferred).count();

    if (!concepts.isEmpty() && preferredCount != 1) {
      throw new IllegalStateException("主题词必须有且仅有一个首选概念,当前数量：" + preferredCount);
    }
  }

  @Override
  public String toString() {
    return String.format(
        "MeshDescriptorAggregate[id=%d, ui=%s, name=%s, type=%s, active=%b, version=%s]",
        getId(), ui.ui(), name, descriptorClass.getCode(), activeStatus, meshVersion);
  }
}
