package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.enums.MeshRecordType;
import com.patra.catalog.domain.model.vo.mesh.ConceptRelation;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/// MeSH 概念实体(Aggregate内实体,不是聚合根)。
///
/// 概念说明：
///
/// - 一个主题词可以包含多个概念(平均 5-6 个概念)
///   - 概念级别的关联支持更细粒度的语义检索
///   - 首选概念(is_preferred=true)是该主题词的主要含义
///   - 化学物质概念包含 CAS 号等注册号信息
///
/// 业务规则：
///
/// - 每个主题词必须有且仅有一个首选概念
///   - 概念 UI 以 M 开头(如 M000001)
///   - registry_number 用于化学物质的注册号(如 CAS 号)
///   - scope_note 提供概念的范围说明
///
/// 使用示例：
///
/// ```java
/// // 创建首选概念
/// MeshConcept preferredConcept = MeshConcept.create(
///     MeshUI.conceptOf(123),
///     "Aspirin",
///     true
/// ).withRegistryNumber("50-78-2");
///
/// // 创建非首选概念
/// MeshConcept concept = MeshConcept.create(
///     MeshUI.conceptOf(456),
///     "Acetylsalicylic Acid",
///     false
/// ).withScopeNote("A common pain reliever and anti-inflammatory drug");
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class MeshConcept implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 标识符 ==========

  /// 主键ID(由Repository在持久化时分配)
  private Long id;

  /// 所属记录的UI(Descriptor: D000001, SCR: C000001)。
  ///
  /// 概念可以属于 Descriptor 或 SCR，通过 ownerUi 关联。
  private final MeshUI ownerUi;

  /// 记录类型（DESCRIPTOR 或 SCR）。
  ///
  /// 根据 ownerUi 前缀自动推断：D→DESCRIPTOR，C→SCR。
  /// 如果 ownerUi 为 null，则 recordType 也为 null。
  private final MeshRecordType recordType;

  /// 概念唯一标识符(格式：M000001-M999999)
  private final MeshUI conceptUi;

  // ========== 业务字段 ==========

  /// 概念名称
  private final String conceptName;

  /// 是否首选概念(每个主题词有且仅有一个首选概念)
  private final boolean isPreferred;

  /// CAS 类型 1 名称(化学物质专用)
  private String casn1Name;

  /// 注册号列表(如 CAS 号,EC 号,2025 DTD 支持多个)
  private final List<String> registryNumbers;

  /// 范围说明
  private String scopeNote;

  /// 翻译者英文范围说明
  private String translatorsEnglishScopeNote;

  /// 翻译者范围说明
  private String translatorsScopeNote;

  /// 概念状态
  private String conceptStatus;

  /// 相关注册号列表(如 "52665-69-7 (Calcimycin)")
  private final List<String> relatedRegistryNumbers;

  /// 概念关系列表(概念间的语义关系)
  private final List<ConceptRelation> conceptRelations;

  /// 私有构造函数。
  ///
  /// @param id 主键ID(新建时为null)
  /// @param ownerUi 所属记录UI（Descriptor 或 SCR）
  /// @param conceptUi 概念UI
  /// @param conceptName 概念名称
  /// @param isPreferred 是否首选概念
  private MeshConcept(
      Long id, MeshUI ownerUi, MeshUI conceptUi, String conceptName, boolean isPreferred) {
    // 必填字段验证（ownerUi 在解析阶段可以为 null，后续由聚合根设置）
    Assert.notNull(conceptUi, "概念UI不能为空");
    Assert.notBlank(conceptName, "概念名称不能为空");

    // 概念UI类型验证
    Assert.isTrue(conceptUi.isConcept(), "概念UI必须以M开头：%s", conceptUi.ui());

    // 概念名称长度验证
    Assert.isTrue(conceptName.length() <= 255, "概念名称长度不能超过255个字符：%s", conceptName);

    // 赋值
    this.id = id;
    this.ownerUi = ownerUi;
    this.conceptUi = conceptUi;
    this.conceptName = conceptName;
    this.isPreferred = isPreferred;

    // 根据 ownerUi 推断 recordType
    if (ownerUi != null) {
      if (ownerUi.isDescriptor()) {
        this.recordType = MeshRecordType.DESCRIPTOR;
      } else if (ownerUi.isScr()) {
        this.recordType = MeshRecordType.SCR;
      } else {
        this.recordType = null;
      }
    } else {
      this.recordType = null;
    }

    // 初始化集合
    this.registryNumbers = new ArrayList<>();
    this.relatedRegistryNumbers = new ArrayList<>();
    this.conceptRelations = new ArrayList<>();
  }

  // ========== 工厂方法 ==========

  /// 创建概念。
  ///
  /// @param conceptUi 概念UI
  /// @param conceptName 概念名称
  /// @param isPreferred 是否首选概念
  /// @return 概念实体
  public static MeshConcept create(MeshUI conceptUi, String conceptName, boolean isPreferred) {
    return new MeshConcept(null, null, conceptUi, conceptName, isPreferred);
  }

  /// 创建概念(指定所属记录UI)。
  ///
  /// @param ownerUi 所属记录UI（Descriptor 或 SCR）
  /// @param conceptUi 概念UI
  /// @param conceptName 概念名称
  /// @param isPreferred 是否首选概念
  /// @return 概念实体
  public static MeshConcept create(
      MeshUI ownerUi, MeshUI conceptUi, String conceptName, boolean isPreferred) {
    return new MeshConcept(null, ownerUi, conceptUi, conceptName, isPreferred);
  }

  /// 从持久化状态重建实体(由Repository使用)。
  ///
  /// @param id 主键ID
  /// @param ownerUi 所属记录UI（Descriptor 或 SCR）
  /// @param conceptUi 概念UI
  /// @param conceptName 概念名称
  /// @param isPreferred 是否首选概念
  /// @param casn1Name CAS类型1名称
  /// @param registryNumbers 注册号列表
  /// @param scopeNote 范围说明
  /// @param translatorsEnglishScopeNote 翻译者英文范围说明
  /// @param translatorsScopeNote 翻译者范围说明
  /// @param conceptStatus 概念状态
  /// @return 重建的实体
  public static MeshConcept restore(
      Long id,
      MeshUI ownerUi,
      MeshUI conceptUi,
      String conceptName,
      boolean isPreferred,
      String casn1Name,
      List<String> registryNumbers,
      String scopeNote,
      String translatorsEnglishScopeNote,
      String translatorsScopeNote,
      String conceptStatus) {
    MeshConcept concept = new MeshConcept(id, ownerUi, conceptUi, conceptName, isPreferred);
    concept.casn1Name = casn1Name;
    if (registryNumbers != null) {
      concept.registryNumbers.addAll(registryNumbers);
    }
    concept.scopeNote = scopeNote;
    concept.translatorsEnglishScopeNote = translatorsEnglishScopeNote;
    concept.translatorsScopeNote = translatorsScopeNote;
    concept.conceptStatus = conceptStatus;
    return concept;
  }

  // ========== 业务方法 ==========

  /// 获取所属主题词UI（向后兼容别名）。
  ///
  /// @return 所属记录UI
  /// @deprecated 请使用 {@link #getOwnerUi()} 代替
  public MeshUI getDescriptorUi() {
    return ownerUi;
  }

  /// 设置ID(由Repository在持久化后回写)。
  ///
  /// @param id 主键ID
  public void assignId(Long id) {
    this.id = id;
  }

  /// 设置CAS类型1名称。
  ///
  /// @param casn1Name CAS类型1名称
  /// @return 当前对象(支持链式调用)
  public MeshConcept withCasn1Name(String casn1Name) {
    this.casn1Name = casn1Name;
    return this;
  }

  /// 添加注册号。
  ///
  /// @param registryNumber 注册号
  /// @return 当前对象(支持链式调用)
  public MeshConcept addRegistryNumber(String registryNumber) {
    if (StrUtil.isNotBlank(registryNumber) && !registryNumbers.contains(registryNumber)) {
      Assert.isTrue(registryNumber.length() <= 50, "注册号长度不能超过50个字符：%s", registryNumber);
      registryNumbers.add(registryNumber);
    }
    return this;
  }

  /// 批量添加注册号。
  ///
  /// @param registryNumberList 注册号列表
  /// @return 当前对象(支持链式调用)
  public MeshConcept addRegistryNumbers(List<String> registryNumberList) {
    if (registryNumberList != null && !registryNumberList.isEmpty()) {
      registryNumberList.forEach(this::addRegistryNumber);
    }
    return this;
  }

  /// 获取注册号列表(不可变视图)。
  ///
  /// @return 注册号列表
  public List<String> getRegistryNumbers() {
    return Collections.unmodifiableList(registryNumbers);
  }

  /// 获取第一个注册号(向后兼容)。
  ///
  /// @return 第一个注册号,如果不存在则返回null
  public String getFirstRegistryNumber() {
    return registryNumbers.isEmpty() ? null : registryNumbers.get(0);
  }

  /// 设置范围说明。
  ///
  /// @param scopeNote 范围说明
  /// @return 当前对象(支持链式调用)
  public MeshConcept withScopeNote(String scopeNote) {
    this.scopeNote = scopeNote;
    return this;
  }

  /// 设置概念状态。
  ///
  /// @param conceptStatus 概念状态
  /// @return 当前对象(支持链式调用)
  public MeshConcept withConceptStatus(String conceptStatus) {
    this.conceptStatus = conceptStatus;
    return this;
  }

  /// 设置翻译者英文范围说明。
  ///
  /// @param translatorsEnglishScopeNote 翻译者英文范围说明
  /// @return 当前对象(支持链式调用)
  public MeshConcept withTranslatorsEnglishScopeNote(String translatorsEnglishScopeNote) {
    this.translatorsEnglishScopeNote = translatorsEnglishScopeNote;
    return this;
  }

  /// 设置翻译者范围说明。
  ///
  /// @param translatorsScopeNote 翻译者范围说明
  /// @return 当前对象(支持链式调用)
  public MeshConcept withTranslatorsScopeNote(String translatorsScopeNote) {
    this.translatorsScopeNote = translatorsScopeNote;
    return this;
  }

  /// 判断是否为化学物质概念。
  ///
  /// @return true 如果有注册号
  public boolean isChemicalConcept() {
    return !registryNumbers.isEmpty();
  }

  /// 判断是否有范围说明。
  ///
  /// @return true 如果有范围说明
  public boolean hasScopeNote() {
    return StrUtil.isNotBlank(scopeNote);
  }

  /// 添加相关注册号。
  ///
  /// @param relatedRegistryNumber 相关注册号
  /// @return 当前对象(支持链式调用)
  public MeshConcept addRelatedRegistryNumber(String relatedRegistryNumber) {
    if (StrUtil.isNotBlank(relatedRegistryNumber)
        && !relatedRegistryNumbers.contains(relatedRegistryNumber)) {
      relatedRegistryNumbers.add(relatedRegistryNumber);
    }
    return this;
  }

  /// 批量添加相关注册号。
  ///
  /// @param registryNumbers 相关注册号列表
  /// @return 当前对象(支持链式调用)
  public MeshConcept addRelatedRegistryNumbers(List<String> registryNumbers) {
    if (registryNumbers != null && !registryNumbers.isEmpty()) {
      registryNumbers.forEach(this::addRelatedRegistryNumber);
    }
    return this;
  }

  /// 获取相关注册号列表(不可变视图)。
  ///
  /// @return 相关注册号列表
  public List<String> getRelatedRegistryNumbers() {
    return Collections.unmodifiableList(relatedRegistryNumbers);
  }

  /// 添加概念关系。
  ///
  /// @param relation 概念关系
  /// @return 当前对象(支持链式调用)
  public MeshConcept addConceptRelation(ConceptRelation relation) {
    Assert.notNull(relation, "概念关系不能为空");

    // 检查是否已存在相同的概念关系
    boolean exists =
        conceptRelations.stream()
            .anyMatch(
                cr ->
                    cr.relationName().equals(relation.relationName())
                        && cr.concept1Ui().equals(relation.concept1Ui())
                        && cr.concept2Ui().equals(relation.concept2Ui()));

    if (!exists) {
      conceptRelations.add(relation);
    }

    return this;
  }

  /// 批量添加概念关系。
  ///
  /// @param relations 概念关系列表
  /// @return 当前对象(支持链式调用)
  public MeshConcept addConceptRelations(List<ConceptRelation> relations) {
    if (relations != null && !relations.isEmpty()) {
      relations.forEach(this::addConceptRelation);
    }
    return this;
  }

  /// 获取概念关系列表(不可变视图)。
  ///
  /// @return 概念关系列表
  public List<ConceptRelation> getConceptRelations() {
    return Collections.unmodifiableList(conceptRelations);
  }

  @Override
  public String toString() {
    return String.format(
        "MeshConcept[ui=%s, name=%s, preferred=%b]", conceptUi.ui(), conceptName, isPreferred);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MeshConcept that)) {
      return false;
    }
    return conceptUi.equals(that.conceptUi);
  }

  @Override
  public int hashCode() {
    return conceptUi.hashCode();
  }
}
