package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;

/**
 * MeSH 概念实体(Aggregate内实体,不是聚合根)。
 *
 * <p>概念说明：
 *
 * <ul>
 *   <li>一个主题词可以包含多个概念(平均 5-6 个概念)
 *   <li>概念级别的关联支持更细粒度的语义检索
 *   <li>首选概念(is_preferred=true)是该主题词的主要含义
 *   <li>化学物质概念包含 CAS 号等注册号信息
 * </ul>
 *
 * <p>业务规则：
 *
 * <ul>
 *   <li>每个主题词必须有且仅有一个首选概念
 *   <li>概念 UI 以 M 开头(如 M000001)
 *   <li>registry_number 用于化学物质的注册号(如 CAS 号)
 *   <li>scope_note 提供概念的范围说明
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 创建首选概念
 * MeshConcept preferredConcept = MeshConcept.create(
 *     MeshUI.conceptOf(123),
 *     "Aspirin",
 *     true
 * ).withRegistryNumber("50-78-2");
 *
 * // 创建非首选概念
 * MeshConcept concept = MeshConcept.create(
 *     MeshUI.conceptOf(456),
 *     "Acetylsalicylic Acid",
 *     false
 * ).withScopeNote("A common pain reliever and anti-inflammatory drug");
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public class MeshConcept implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 标识符 ==========

  /** 主键ID(由Repository在持久化时分配) */
  private Long id;

  /** 关联的主题词ID(外键) */
  private final Long descriptorId;

  /** 概念唯一标识符(格式：M000001-M999999) */
  private final MeshUI conceptUi;

  // ========== 业务字段 ==========

  /** 概念名称 */
  private final String conceptName;

  /** 是否首选概念(每个主题词有且仅有一个首选概念) */
  private final boolean isPreferred;

  /** CAS 类型 1 名称(化学物质专用) */
  private String casn1Name;

  /** 注册号(如 CAS 号,EC 号) */
  private String registryNumber;

  /** 范围说明 */
  private String scopeNote;

  /** 概念状态 */
  private String conceptStatus;

  /**
   * 私有构造函数。
   *
   * @param id 主键ID(新建时为null)
   * @param descriptorId 主题词ID
   * @param conceptUi 概念UI
   * @param conceptName 概念名称
   * @param isPreferred 是否首选概念
   */
  private MeshConcept(
      Long id,
      Long descriptorId,
      MeshUI conceptUi,
      String conceptName,
      boolean isPreferred) {
    // 必填字段验证
    Assert.notNull(descriptorId, "主题词ID不能为空");
    Assert.notNull(conceptUi, "概念UI不能为空");
    Assert.notBlank(conceptName, "概念名称不能为空");

    // 概念UI类型验证
    Assert.isTrue(
        conceptUi.isConcept(),
        "概念UI必须以M开头：%s",
        conceptUi.ui());

    // 概念名称长度验证
    Assert.isTrue(
        conceptName.length() <= 255,
        "概念名称长度不能超过255个字符：%s",
        conceptName);

    // 赋值
    this.id = id;
    this.descriptorId = descriptorId;
    this.conceptUi = conceptUi;
    this.conceptName = conceptName;
    this.isPreferred = isPreferred;
  }

  // ========== 工厂方法 ==========

  /**
   * 创建概念。
   *
   * @param conceptUi 概念UI
   * @param conceptName 概念名称
   * @param isPreferred 是否首选概念
   * @return 概念实体
   */
  public static MeshConcept create(
      MeshUI conceptUi,
      String conceptName,
      boolean isPreferred) {
    return new MeshConcept(null, null, conceptUi, conceptName, isPreferred);
  }

  /**
   * 创建概念(指定主题词ID)。
   *
   * @param descriptorId 主题词ID
   * @param conceptUi 概念UI
   * @param conceptName 概念名称
   * @param isPreferred 是否首选概念
   * @return 概念实体
   */
  public static MeshConcept create(
      Long descriptorId,
      MeshUI conceptUi,
      String conceptName,
      boolean isPreferred) {
    return new MeshConcept(null, descriptorId, conceptUi, conceptName, isPreferred);
  }

  /**
   * 从持久化状态重建实体(由Repository使用)。
   *
   * @param id 主键ID
   * @param descriptorId 主题词ID
   * @param conceptUi 概念UI
   * @param conceptName 概念名称
   * @param isPreferred 是否首选概念
   * @param casn1Name CAS类型1名称
   * @param registryNumber 注册号
   * @param scopeNote 范围说明
   * @param conceptStatus 概念状态
   * @return 重建的实体
   */
  public static MeshConcept restore(
      Long id,
      Long descriptorId,
      MeshUI conceptUi,
      String conceptName,
      boolean isPreferred,
      String casn1Name,
      String registryNumber,
      String scopeNote,
      String conceptStatus) {
    MeshConcept concept = new MeshConcept(id, descriptorId, conceptUi, conceptName, isPreferred);
    concept.casn1Name = casn1Name;
    concept.registryNumber = registryNumber;
    concept.scopeNote = scopeNote;
    concept.conceptStatus = conceptStatus;
    return concept;
  }

  // ========== 业务方法 ==========

  /**
   * 设置ID(由Repository在持久化后回写)。
   *
   * @param id 主键ID
   */
  public void assignId(Long id) {
    this.id = id;
  }

  /**
   * 设置CAS类型1名称。
   *
   * @param casn1Name CAS类型1名称
   * @return 当前对象(支持链式调用)
   */
  public MeshConcept withCasn1Name(String casn1Name) {
    this.casn1Name = casn1Name;
    return this;
  }

  /**
   * 设置注册号。
   *
   * @param registryNumber 注册号
   * @return 当前对象(支持链式调用)
   */
  public MeshConcept withRegistryNumber(String registryNumber) {
    if (StrUtil.isNotBlank(registryNumber)) {
      Assert.isTrue(
          registryNumber.length() <= 50,
          "注册号长度不能超过50个字符：%s",
          registryNumber);
    }
    this.registryNumber = registryNumber;
    return this;
  }

  /**
   * 设置范围说明。
   *
   * @param scopeNote 范围说明
   * @return 当前对象(支持链式调用)
   */
  public MeshConcept withScopeNote(String scopeNote) {
    this.scopeNote = scopeNote;
    return this;
  }

  /**
   * 设置概念状态。
   *
   * @param conceptStatus 概念状态
   * @return 当前对象(支持链式调用)
   */
  public MeshConcept withConceptStatus(String conceptStatus) {
    this.conceptStatus = conceptStatus;
    return this;
  }

  /**
   * 判断是否为化学物质概念。
   *
   * @return true 如果有注册号
   */
  public boolean isChemicalConcept() {
    return StrUtil.isNotBlank(registryNumber);
  }

  /**
   * 判断是否有范围说明。
   *
   * @return true 如果有范围说明
   */
  public boolean hasScopeNote() {
    return StrUtil.isNotBlank(scopeNote);
  }

  @Override
  public String toString() {
    return String.format(
        "MeshConcept[ui=%s, name=%s, preferred=%b]",
        conceptUi.ui(),
        conceptName,
        isPreferred);
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
