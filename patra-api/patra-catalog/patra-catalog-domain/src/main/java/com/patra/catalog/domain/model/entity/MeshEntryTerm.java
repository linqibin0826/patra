package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.enums.LexicalTag;
import java.io.Serial;
import java.io.Serializable;
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

  /// 关联的主题词ID(外键)
  private final Long descriptorId;

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

  /// 私有构造函数。
/// 
/// @param id 主键ID(新建时为null)
/// @param descriptorId 主题词ID
/// @param term 入口术语
/// @param lexicalTag 词法标记
/// @param isPrintFlag 是否打印
/// @param isRecordPreferred 是否记录首选
/// @param isPermutedTerm 是否排列术语
  private MeshEntryTerm(
      Long id,
      Long descriptorId,
      String term,
      LexicalTag lexicalTag,
      boolean isPrintFlag,
      boolean isRecordPreferred,
      boolean isPermutedTerm) {
    // 必填字段验证
    Assert.notNull(descriptorId, "主题词ID不能为空");
    Assert.notBlank(term, "入口术语不能为空");

    // 术语长度验证
    Assert.isTrue(
        term.length() <= 255,
        "入口术语长度不能超过255个字符：%s",
        term);

    // 赋值
    this.id = id;
    this.descriptorId = descriptorId;
    this.term = term;
    this.lexicalTag = lexicalTag;
    this.isPrintFlag = isPrintFlag;
    this.isRecordPreferred = isRecordPreferred;
    this.isPermutedTerm = isPermutedTerm;
  }

  // ========== 工厂方法 ==========

  /// 创建入口术语。
/// 
/// @param term 入口术语
/// @param lexicalTag 词法标记
/// @param isRecordPreferred 是否记录首选
/// @param isPrintFlag 是否打印
/// @return 入口术语实体
  public static MeshEntryTerm create(
      String term,
      LexicalTag lexicalTag,
      boolean isRecordPreferred,
      boolean isPrintFlag) {
    return new MeshEntryTerm(
        null,
        null,
        term,
        lexicalTag,
        isPrintFlag,
        isRecordPreferred,
        false);
  }

  /// 创建入口术语(指定主题词ID)。
/// 
/// @param descriptorId 主题词ID
/// @param term 入口术语
/// @param lexicalTag 词法标记
/// @param isRecordPreferred 是否记录首选
/// @param isPrintFlag 是否打印
/// @return 入口术语实体
  public static MeshEntryTerm create(
      Long descriptorId,
      String term,
      LexicalTag lexicalTag,
      boolean isRecordPreferred,
      boolean isPrintFlag) {
    return new MeshEntryTerm(
        null,
        descriptorId,
        term,
        lexicalTag,
        isPrintFlag,
        isRecordPreferred,
        false);
  }

  /// 从持久化状态重建实体(由Repository使用)。
/// 
/// @param id 主键ID
/// @param descriptorId 主题词ID
/// @param term 入口术语
/// @param lexicalTag 词法标记
/// @param isPrintFlag 是否打印
/// @param isRecordPreferred 是否记录首选
/// @param isPermutedTerm 是否排列术语
/// @return 重建的实体
  public static MeshEntryTerm restore(
      Long id,
      Long descriptorId,
      String term,
      LexicalTag lexicalTag,
      boolean isPrintFlag,
      boolean isRecordPreferred,
      boolean isPermutedTerm) {
    return new MeshEntryTerm(
        id,
        descriptorId,
        term,
        lexicalTag,
        isPrintFlag,
        isRecordPreferred,
        isPermutedTerm);
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

  @Override
  public String toString() {
    return String.format(
        "MeshEntryTerm[term=%s, lexicalTag=%s, preferred=%b]",
        term,
        lexicalTag != null ? lexicalTag.getCode() : "null",
        isRecordPreferred);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MeshEntryTerm that)) {
      return false;
    }
    return term.equals(that.term) && descriptorId.equals(that.descriptorId);
  }

  @Override
  public int hashCode() {
    int result = descriptorId != null ? descriptorId.hashCode() : 0;
    result = 31 * result + term.hashCode();
    return result;
  }
}
