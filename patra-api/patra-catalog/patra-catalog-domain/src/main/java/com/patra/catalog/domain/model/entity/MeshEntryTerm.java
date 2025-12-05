package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.enums.LexicalTag;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Getter;

/// MeSH 入口术语实体(Aggregate内实体,不是聚合根)。
///
/// 入口术语说明：
///
/// - **同义词**：主题词的同义词(如 "Aspirin" → "Acetylsalicylic Acid")
///   - **缩写**：主题词的缩写形式(如 "AIDS" → "Acquired Immunodeficiency Syndrome")
///   - **化学名称**：化学物质的不同名称(如 "A-23187" → "Calcimycin")
///   - **多语言**：同一主题词的不同语言表达
///
/// 业务规则：
///
/// - 一个主题词平均有 7-8 个入口术语
///   - 词法标记(LexicalTag)区分不同类型的术语
///   - record_preferred 标记该术语是否为记录首选
///   - is_print_flag 控制术语是否打印在索引中
///
/// 使用示例：
///
/// ```java
/// // 创建首选术语
/// MeshEntryTerm preferred = MeshEntryTerm.create(
///     "Acetylsalicylic Acid",
///     LexicalTag.PEF,
///     true,
///     true
/// );
///
/// // 创建缩写术语
/// MeshEntryTerm abbreviation = MeshEntryTerm.create(
///     "ASA",
///     LexicalTag.ABB,
///     false,
///     true
/// );
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class MeshEntryTerm implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 标识符 ==========

  /// 主键ID(由Repository在持久化时分配)
  private Long id;

  /// 关联的主题词UI(格式:D000001)
  private final MeshUI descriptorUi;

  /// 术语唯一标识符(格式：T000001-T999999)
  private final MeshUI termUi;

  /// 所属概念UI(格式：M000001-M999999)
  private MeshUI conceptUi;

  // ========== 业务字段 ==========

  /// 入口术语/同义词
  private final String term;

  /// 词法标记(NON/PEF/LAB/ABB/ACR/NAM)
  private final LexicalTag lexicalTag;

  /// 是否打印(控制术语是否打印在索引中)
  private final boolean isPrintFlag;

  /// 是否记录首选(该术语是否为记录首选)
  private final boolean isRecordPreferred;

  /// 是否排列术语(用于索引排列)
  private final boolean isPermutedTerm;

  /// 是否概念首选术语(区别于记录首选术语)
  private final boolean isConceptPreferred;

  /// 术语创建日期
  private LocalDate dateCreated;

  /// 来源词库列表(如 ["FDA SRS (2014)", "NLM (1975)"])
  private final List<String> thesaurusIds;

  /// 入口版本(可选)
  private String entryVersion;

  /// 术语缩写(可选)
  private String abbreviation;

  /// 排序版本(可选)
  private String sortVersion;

  /// 术语说明(可选)
  private String termNote;

  /// 私有构造函数。
  ///
  /// @param id 主键ID(新建时为null)
  /// @param descriptorUi 主题词UI
  /// @param termUi 术语UI
  /// @param term 入口术语
  /// @param lexicalTag 词法标记
  /// @param isPrintFlag 是否打印
  /// @param isRecordPreferred 是否记录首选
  /// @param isPermutedTerm 是否排列术语
  /// @param isConceptPreferred 是否概念首选术语
  private MeshEntryTerm(
      Long id,
      MeshUI descriptorUi,
      MeshUI termUi,
      String term,
      LexicalTag lexicalTag,
      boolean isPrintFlag,
      boolean isRecordPreferred,
      boolean isPermutedTerm,
      boolean isConceptPreferred) {
    // 必填字段验证（descriptorUi 在解析阶段可以为 null，后续由聚合根设置）
    Assert.notBlank(term, "入口术语不能为空");

    // 术语UI验证（如果提供）
    if (termUi != null) {
      Assert.isTrue(termUi.isTerm(), "术语UI必须以T开头：%s", termUi.ui());
    }

    // 术语长度验证
    Assert.isTrue(term.length() <= 255, "入口术语长度不能超过255个字符：%s", term);

    // 赋值
    this.id = id;
    this.descriptorUi = descriptorUi;
    this.termUi = termUi;
    this.term = term;
    this.lexicalTag = lexicalTag;
    this.isPrintFlag = isPrintFlag;
    this.isRecordPreferred = isRecordPreferred;
    this.isPermutedTerm = isPermutedTerm;
    this.isConceptPreferred = isConceptPreferred;

    // 初始化集合
    this.thesaurusIds = new ArrayList<>();
  }

  // ========== 工厂方法 ==========

  /// 创建入口术语。
  ///
  /// @param termUi 术语UI
  /// @param term 入口术语
  /// @param lexicalTag 词法标记
  /// @param isRecordPreferred 是否记录首选
  /// @param isPrintFlag 是否打印
  /// @param isConceptPreferred 是否概念首选
  /// @param isPermutedTerm 是否排列术语
  /// @return 入口术语实体
  public static MeshEntryTerm create(
      MeshUI termUi,
      String term,
      LexicalTag lexicalTag,
      boolean isRecordPreferred,
      boolean isPrintFlag,
      boolean isConceptPreferred,
      boolean isPermutedTerm) {
    return new MeshEntryTerm(
        null,
        null,
        termUi,
        term,
        lexicalTag,
        isPrintFlag,
        isRecordPreferred,
        isPermutedTerm,
        isConceptPreferred);
  }

  /// 创建入口术语(指定主题词UI)。
  ///
  /// @param descriptorUi 主题词UI
  /// @param termUi 术语UI
  /// @param term 入口术语
  /// @param lexicalTag 词法标记
  /// @param isRecordPreferred 是否记录首选
  /// @param isPrintFlag 是否打印
  /// @param isConceptPreferred 是否概念首选
  /// @param isPermutedTerm 是否排列术语
  /// @return 入口术语实体
  public static MeshEntryTerm create(
      MeshUI descriptorUi,
      MeshUI termUi,
      String term,
      LexicalTag lexicalTag,
      boolean isRecordPreferred,
      boolean isPrintFlag,
      boolean isConceptPreferred,
      boolean isPermutedTerm) {
    return new MeshEntryTerm(
        null,
        descriptorUi,
        termUi,
        term,
        lexicalTag,
        isPrintFlag,
        isRecordPreferred,
        isPermutedTerm,
        isConceptPreferred);
  }

  /// 从持久化状态重建实体(由Repository使用)。
  ///
  /// @param id 主键ID
  /// @param descriptorUi 主题词UI
  /// @param termUi 术语UI
  /// @param term 入口术语
  /// @param lexicalTag 词法标记
  /// @param isPrintFlag 是否打印
  /// @param isRecordPreferred 是否记录首选
  /// @param isPermutedTerm 是否排列术语
  /// @param isConceptPreferred 是否概念首选
  /// @return 重建的实体
  public static MeshEntryTerm restore(
      Long id,
      MeshUI descriptorUi,
      MeshUI termUi,
      String term,
      LexicalTag lexicalTag,
      boolean isPrintFlag,
      boolean isRecordPreferred,
      boolean isPermutedTerm,
      boolean isConceptPreferred) {
    return new MeshEntryTerm(
        id,
        descriptorUi,
        termUi,
        term,
        lexicalTag,
        isPrintFlag,
        isRecordPreferred,
        isPermutedTerm,
        isConceptPreferred);
  }

  // ========== 业务方法 ==========

  /// 设置ID(由Repository在持久化后回写)。
  ///
  /// @param id 主键ID
  public void assignId(Long id) {
    this.id = id;
  }

  /// 判断是否为首选术语。
  ///
  /// @return true 如果为首选术语
  public boolean isPreferred() {
    return lexicalTag != null && lexicalTag.isPreferred();
  }

  /// 判断是否为缩写术语。
  ///
  /// @return true 如果为缩写或首字母缩写
  public boolean isAbbreviation() {
    return lexicalTag != null && lexicalTag.isAbbreviation();
  }

  /// 设置术语创建日期。
  ///
  /// @param dateCreated 术语创建日期
  /// @return 当前对象(支持链式调用)
  public MeshEntryTerm withDateCreated(LocalDate dateCreated) {
    this.dateCreated = dateCreated;
    return this;
  }

  /// 设置入口版本。
  ///
  /// @param entryVersion 入口版本
  /// @return 当前对象(支持链式调用)
  public MeshEntryTerm withEntryVersion(String entryVersion) {
    this.entryVersion = entryVersion;
    return this;
  }

  /// 设置所属概念UI。
  ///
  /// @param conceptUi 概念UI
  /// @return 当前对象(支持链式调用)
  public MeshEntryTerm withConceptUi(MeshUI conceptUi) {
    if (conceptUi != null) {
      Assert.isTrue(conceptUi.isConcept(), "概念UI必须以M开头：%s", conceptUi.ui());
    }
    this.conceptUi = conceptUi;
    return this;
  }

  /// 设置术语缩写。
  ///
  /// @param abbreviation 术语缩写
  /// @return 当前对象(支持链式调用)
  public MeshEntryTerm withAbbreviation(String abbreviation) {
    this.abbreviation = abbreviation;
    return this;
  }

  /// 设置排序版本。
  ///
  /// @param sortVersion 排序版本
  /// @return 当前对象(支持链式调用)
  public MeshEntryTerm withSortVersion(String sortVersion) {
    this.sortVersion = sortVersion;
    return this;
  }

  /// 设置术语说明。
  ///
  /// @param termNote 术语说明
  /// @return 当前对象(支持链式调用)
  public MeshEntryTerm withTermNote(String termNote) {
    this.termNote = termNote;
    return this;
  }

  /// 添加来源词库。
  ///
  /// @param thesaurusId 来源词库
  /// @return 当前对象(支持链式调用)
  public MeshEntryTerm addThesaurusId(String thesaurusId) {
    if (thesaurusId != null && !thesaurusIds.contains(thesaurusId)) {
      thesaurusIds.add(thesaurusId);
    }
    return this;
  }

  /// 批量添加来源词库。
  ///
  /// @param thesaurusIdList 来源词库列表
  /// @return 当前对象(支持链式调用)
  public MeshEntryTerm addThesaurusIds(List<String> thesaurusIdList) {
    if (thesaurusIdList != null && !thesaurusIdList.isEmpty()) {
      thesaurusIdList.forEach(this::addThesaurusId);
    }
    return this;
  }

  /// 获取来源词库列表(不可变视图)。
  ///
  /// @return 来源词库列表
  public List<String> getThesaurusIds() {
    return Collections.unmodifiableList(thesaurusIds);
  }

  @Override
  public String toString() {
    return String.format(
        "MeshEntryTerm[term=%s, lexicalTag=%s, preferred=%b]",
        term, lexicalTag != null ? lexicalTag.getCode() : "null", isRecordPreferred);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MeshEntryTerm that)) {
      return false;
    }
    return term.equals(that.term) && Objects.equals(descriptorUi, that.descriptorUi);
  }

  @Override
  public int hashCode() {
    int result = descriptorUi != null ? descriptorUi.hashCode() : 0;
    result = 31 * result + term.hashCode();
    return result;
  }
}
