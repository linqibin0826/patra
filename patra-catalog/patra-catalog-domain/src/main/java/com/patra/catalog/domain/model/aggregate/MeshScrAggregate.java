package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.enums.ScrClass;
import com.patra.catalog.domain.model.vo.mesh.HeadingMappedTo;
import com.patra.catalog.domain.model.vo.mesh.IndexingInfo;
import com.patra.catalog.domain.model.vo.mesh.MeshScrId;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.domain.model.vo.mesh.PharmacologicalAction;
import com.patra.catalog.domain.model.vo.mesh.ScrSource;
import com.patra.common.domain.AggregateRoot;
import java.io.Serial;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

/// MeSH 补充概念记录（SCR）聚合根。
///
/// SCR（Supplementary Concept Record）是 MeSH 词汇表的扩展，用于：
///
/// - 化学物质和药物（Class 1）
/// - 化疗方案（Class 2）
/// - 疾病（Class 3）
/// - 生物体（Class 4，2018年新增）
/// - 人群组（Class 5，2023年新增）
/// - 解剖结构（Class 6）
///
/// 与 Descriptor 的区别：
///
/// - SCR 没有树形结构（TreeNumber）
/// - SCR 通过 HeadingMappedTo 映射到 Descriptor
/// - SCR 有额外的来源信息（SourceList）
/// - SCR 有索引信息（IndexingInformationList）
///
/// 聚合边界：
///
/// - SCR 是聚合根
/// - 包含 MeshConcept 列表（内部实体）
/// - 包含 HeadingMappedTo、ScrSource、IndexingInfo（值对象列表）
/// - 包含 PharmacologicalAction（指向 Descriptor UI）
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://www.nlm.nih.gov/mesh/intro_record_types.html">MeSH Record Types</a>
@Getter
public class MeshScrAggregate extends AggregateRoot<MeshScrId> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 标识符 ==========

  /// SCR 唯一标识符（格式：C000001-C999999）
  private final MeshUI ui;

  // ========== 业务字段 ==========

  /// 补充概念名称
  private final String name;

  /// SCR 类别（1-6）
  private final ScrClass scrClass;

  /// 说明（用法说明和注释）
  private String note;

  /// 频率（文献中出现的频率描述）
  private String frequency;

  /// 之前的索引方式（历史参考）
  private String previousIndexing;

  /// 创建日期
  private LocalDate dateCreated;

  /// 修订日期（用于增量更新）
  private LocalDate dateRevised;

  /// 是否有效（false=已废弃，true=有效）
  private boolean activeStatus;

  /// MeSH 版本年份（如 "2025"）
  private String meshVersion;

  /// 其他元数据（JSON 扩展字段）
  private String metadataJson;

  // ========== 关联集合 ==========

  /// 映射关系列表（SCR → Descriptor）
  private final List<HeadingMappedTo> headingMappedTos;

  /// 概念列表
  private final List<MeshConcept> concepts;

  /// 来源列表
  private final List<ScrSource> sources;

  /// 索引信息列表
  private final List<IndexingInfo> indexingInfos;

  /// 药理作用列表（指向 Descriptor）
  private final List<PharmacologicalAction> pharmacologicalActions;

  /// 私有构造函数。
  private MeshScrAggregate(MeshUI ui, String name, ScrClass scrClass) {
    super(); // 调用 AggregateRoot 构造函数

    // 验证必填字段
    Assert.notNull(ui, "SCR UI不能为空");
    Assert.isTrue(StrUtil.isNotBlank(name), "SCR 名称不能为空");
    Assert.isTrue(ui.isScr(), "SCR UI必须以C开头：%s", ui.ui());

    this.ui = ui;
    this.name = name;
    this.scrClass = scrClass != null ? scrClass : ScrClass.CHEMICAL;
    this.activeStatus = true;

    // 初始化集合
    this.headingMappedTos = new ArrayList<>();
    this.concepts = new ArrayList<>();
    this.sources = new ArrayList<>();
    this.indexingInfos = new ArrayList<>();
    this.pharmacologicalActions = new ArrayList<>();
  }

  // ========== 工厂方法 ==========

  /// 创建 SCR 聚合根（使用默认类别 CHEMICAL）。
  ///
  /// @param ui SCR UI
  /// @param name 名称
  /// @return SCR 聚合根
  public static MeshScrAggregate create(MeshUI ui, String name) {
    return new MeshScrAggregate(ui, name, ScrClass.CHEMICAL);
  }

  /// 创建 SCR 聚合根（指定类别）。
  ///
  /// @param ui SCR UI
  /// @param name 名称
  /// @param scrClass SCR 类别
  /// @return SCR 聚合根
  public static MeshScrAggregate create(MeshUI ui, String name, ScrClass scrClass) {
    return new MeshScrAggregate(ui, name, scrClass);
  }

  /// 从持久化状态恢复 SCR 聚合根。
  ///
  /// @param ui SCR UI
  /// @param name 名称
  /// @param scrClass SCR 类别
  /// @param note 说明
  /// @param frequency 频率
  /// @param previousIndexing 之前的索引方式
  /// @param dateCreated 创建日期
  /// @param dateRevised 修订日期
  /// @param activeStatus 是否有效
  /// @param meshVersion MeSH 版本
  /// @param metadataJson 元数据 JSON
  /// @return SCR 聚合根
  public static MeshScrAggregate restore(
      MeshUI ui,
      String name,
      ScrClass scrClass,
      String note,
      String frequency,
      String previousIndexing,
      LocalDate dateCreated,
      LocalDate dateRevised,
      boolean activeStatus,
      String meshVersion,
      String metadataJson) {
    MeshScrAggregate scr = new MeshScrAggregate(ui, name, scrClass);
    scr.note = note;
    scr.frequency = frequency;
    scr.previousIndexing = previousIndexing;
    scr.dateCreated = dateCreated;
    scr.dateRevised = dateRevised;
    scr.activeStatus = activeStatus;
    scr.meshVersion = meshVersion;
    scr.metadataJson = metadataJson;
    return scr;
  }

  // ========== HeadingMappedTo 管理 ==========

  /// 添加映射关系。
  ///
  /// @param mapping 映射关系
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate addHeadingMappedTo(HeadingMappedTo mapping) {
    Assert.notNull(mapping, "映射关系不能为空");
    if (!headingMappedTos.contains(mapping)) {
      headingMappedTos.add(mapping);
      trackChildAdded(HeadingMappedTo.class, mapping);
    }
    return this;
  }

  /// 批量添加映射关系。
  ///
  /// @param mappings 映射关系列表
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate addHeadingMappedTos(List<HeadingMappedTo> mappings) {
    if (mappings != null && !mappings.isEmpty()) {
      mappings.forEach(this::addHeadingMappedTo);
    }
    return this;
  }

  /// 获取映射关系列表（不可变视图）。
  public List<HeadingMappedTo> getHeadingMappedTos() {
    return Collections.unmodifiableList(headingMappedTos);
  }

  // ========== Concept 管理 ==========

  /// 添加概念。
  ///
  /// @param concept 概念
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate addConcept(MeshConcept concept) {
    Assert.notNull(concept, "概念不能为空");
    if (!concepts.contains(concept)) {
      concepts.add(concept);
      trackChildAdded(MeshConcept.class, concept);
    }
    return this;
  }

  /// 批量添加概念。
  ///
  /// @param conceptList 概念列表
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate addConcepts(List<MeshConcept> conceptList) {
    if (conceptList != null && !conceptList.isEmpty()) {
      conceptList.forEach(this::addConcept);
    }
    return this;
  }

  /// 获取概念列表（不可变视图）。
  public List<MeshConcept> getConcepts() {
    return Collections.unmodifiableList(concepts);
  }

  /// 获取首选概念。
  public Optional<MeshConcept> getPreferredConcept() {
    return concepts.stream().filter(MeshConcept::isPreferred).findFirst();
  }

  // ========== Source 管理 ==========

  /// 添加来源。
  ///
  /// @param source 来源
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate addSource(ScrSource source) {
    Assert.notNull(source, "来源不能为空");
    if (!sources.contains(source)) {
      sources.add(source);
      trackChildAdded(ScrSource.class, source);
    }
    return this;
  }

  /// 批量添加来源。
  ///
  /// @param sourceList 来源列表
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate addSources(List<ScrSource> sourceList) {
    if (sourceList != null && !sourceList.isEmpty()) {
      sourceList.forEach(this::addSource);
    }
    return this;
  }

  /// 获取来源列表（不可变视图）。
  public List<ScrSource> getSources() {
    return Collections.unmodifiableList(sources);
  }

  // ========== IndexingInfo 管理 ==========

  /// 添加索引信息。
  ///
  /// @param info 索引信息
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate addIndexingInfo(IndexingInfo info) {
    Assert.notNull(info, "索引信息不能为空");
    if (!indexingInfos.contains(info)) {
      indexingInfos.add(info);
      trackChildAdded(IndexingInfo.class, info);
    }
    return this;
  }

  /// 批量添加索引信息。
  ///
  /// @param infoList 索引信息列表
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate addIndexingInfos(List<IndexingInfo> infoList) {
    if (infoList != null && !infoList.isEmpty()) {
      infoList.forEach(this::addIndexingInfo);
    }
    return this;
  }

  /// 获取索引信息列表（不可变视图）。
  public List<IndexingInfo> getIndexingInfos() {
    return Collections.unmodifiableList(indexingInfos);
  }

  // ========== PharmacologicalAction 管理 ==========

  /// 添加药理作用。
  ///
  /// @param action 药理作用
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate addPharmacologicalAction(PharmacologicalAction action) {
    Assert.notNull(action, "药理作用不能为空");

    // 检查是否已存在相同的药理作用（基于 descriptorUi 去重）
    boolean exists =
        pharmacologicalActions.stream()
            .anyMatch(pa -> pa.descriptorUi().equals(action.descriptorUi()));

    if (!exists) {
      pharmacologicalActions.add(action);
      trackChildAdded(PharmacologicalAction.class, action);
    }
    return this;
  }

  /// 批量添加药理作用。
  ///
  /// @param actions 药理作用列表
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate addPharmacologicalActions(List<PharmacologicalAction> actions) {
    if (actions != null && !actions.isEmpty()) {
      actions.forEach(this::addPharmacologicalAction);
    }
    return this;
  }

  /// 获取药理作用列表（不可变视图）。
  public List<PharmacologicalAction> getPharmacologicalActions() {
    return Collections.unmodifiableList(pharmacologicalActions);
  }

  // ========== 状态管理 ==========

  /// 设置 MeSH 版本。
  ///
  /// @param meshVersion MeSH 版本
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate withMeshVersion(String meshVersion) {
    this.meshVersion = meshVersion;
    markDirty();
    return this;
  }

  /// 设置说明。
  ///
  /// @param note 说明
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate withNote(String note) {
    this.note = note;
    markDirty();
    return this;
  }

  /// 设置频率。
  ///
  /// @param frequency 频率
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate withFrequency(String frequency) {
    this.frequency = frequency;
    markDirty();
    return this;
  }

  /// 设置之前的索引方式。
  ///
  /// @param previousIndexing 之前的索引方式
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate withPreviousIndexing(String previousIndexing) {
    this.previousIndexing = previousIndexing;
    markDirty();
    return this;
  }

  /// 设置创建日期。
  ///
  /// @param dateCreated 创建日期
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate withDateCreated(LocalDate dateCreated) {
    this.dateCreated = dateCreated;
    markDirty();
    return this;
  }

  /// 设置修订日期。
  ///
  /// @param dateRevised 修订日期
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate withDateRevised(LocalDate dateRevised) {
    this.dateRevised = dateRevised;
    markDirty();
    return this;
  }

  /// 设置元数据 JSON。
  ///
  /// @param metadataJson 元数据 JSON
  /// @return 当前对象（支持链式调用）
  public MeshScrAggregate withMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
    markDirty();
    return this;
  }

  /// 废弃 SCR。
  public void deprecate() {
    this.activeStatus = false;
    markDirty();
  }

  /// 激活 SCR。
  public void activate() {
    this.activeStatus = true;
    markDirty();
  }

  /// 判断 SCR 是否有效。
  public boolean isActive() {
    return activeStatus;
  }

  // ========== 业务查询方法 ==========

  /// 判断是否为化学物质类 SCR。
  public boolean isChemical() {
    return scrClass == ScrClass.CHEMICAL;
  }

  /// 判断是否为疾病类 SCR。
  public boolean isDisease() {
    return scrClass == ScrClass.DISEASE;
  }

  /// 判断是否有说明。
  public boolean hasNote() {
    return StrUtil.isNotBlank(note);
  }

  // ========== 不变量验证 ==========

  /// 验证聚合不变量。
  ///
  /// 业务规则：
  /// - UI 不能为空
  /// - 名称不能为空
  /// - SCR 类别不能为空
  @Override
  protected void assertInvariants() {
    if (ui == null) {
      throw new IllegalStateException("SCR UI 不能为空");
    }
    if (StrUtil.isBlank(name)) {
      throw new IllegalStateException("SCR 名称不能为空");
    }
    if (scrClass == null) {
      throw new IllegalStateException("SCR 类别不能为空");
    }
  }

  @Override
  public String toString() {
    return String.format(
        "MeshScrAggregate[id=%s, ui=%s, name=%s, class=%s, active=%b, version=%d]",
        getId(), ui.ui(), name, scrClass.getDisplayName(), activeStatus, getVersion());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MeshScrAggregate that)) {
      return false;
    }
    return ui.equals(that.ui);
  }

  @Override
  public int hashCode() {
    return ui.hashCode();
  }
}
