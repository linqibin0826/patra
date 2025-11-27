package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.vo.mesh.EntryCombination;
import com.patra.catalog.infra.persistence.entity.MeshConceptDO;
import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;
import com.patra.catalog.infra.persistence.entity.MeshEntryCombinationDO;
import com.patra.catalog.infra.persistence.entity.MeshEntryTermDO;
import com.patra.catalog.infra.persistence.entity.MeshTreeNumberDO;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// MeSH 主题词聚合根转换器。
///
/// **职责**：
///
/// - MeshDescriptorAggregate ↔ MeshDescriptorDO 转换
///   - MeshTreeNumber、MeshEntryTerm、MeshConcept ↔ DO 转换
///   - 值对象(VO)与基本类型的转换
///
/// @author linqibin
/// @since 0.1.0
@Component
@RequiredArgsConstructor
public class MeshDescriptorConverter {

  /// 将聚合根转换为主表 DO。
  ///
  /// @param aggregate 主题词聚合根
  /// @return 主表 DO
  public MeshDescriptorDO toDescriptorDO(MeshDescriptorAggregate aggregate) {
    if (aggregate == null) {
      return null;
    }

    MeshDescriptorDO dataObject = new MeshDescriptorDO();
    dataObject.setId(aggregate.getId());
    dataObject.setUi(aggregate.getUi().ui());
    dataObject.setName(aggregate.getName());
    dataObject.setDescriptorClass(aggregate.getDescriptorClass().getCode());
    dataObject.setScopeNote(aggregate.getScopeNote());
    dataObject.setAnnotation(aggregate.getAnnotation());
    dataObject.setPreviousIndexing(aggregate.getPreviousIndexing());
    dataObject.setPublicMeshNote(aggregate.getPublicMeshNote());
    dataObject.setConsiderAlso(aggregate.getConsiderAlso());
    dataObject.setDateCreated(aggregate.getDateCreated());
    dataObject.setDateRevised(aggregate.getDateRevised());
    dataObject.setDateEstablished(aggregate.getDateEstablished());
    dataObject.setActiveStatus(aggregate.isActiveStatus());
    dataObject.setMeshVersion(aggregate.getMeshVersion());
    dataObject.setHistoryNote(aggregate.getHistoryNote());
    dataObject.setOnlineNote(aggregate.getOnlineNote());
    dataObject.setNlmClassificationNumber(aggregate.getNlmClassificationNumber());

    return dataObject;
  }

  /// 将树形编号实体转换为 DO。
  ///
  /// @param treeNumber 树形编号实体
  /// @param descriptorId 主题词 ID（外键）
  /// @return 树形编号 DO
  public MeshTreeNumberDO toTreeNumberDO(MeshTreeNumber treeNumber, Long descriptorId) {
    if (treeNumber == null) {
      return null;
    }

    MeshTreeNumberDO dataObject = new MeshTreeNumberDO();
    dataObject.setDescriptorId(descriptorId);
    dataObject.setTreeNumber(treeNumber.getTreeNumber());
    dataObject.setTreeLevel(treeNumber.getTreeLevel());
    dataObject.setIsPrimary(treeNumber.isPrimary());

    return dataObject;
  }

  /// 将概念实体转换为 DO。
  ///
  /// @param concept 概念实体
  /// @param descriptorId 主题词 ID（外键）
  /// @return 概念 DO
  public MeshConceptDO toConceptDO(MeshConcept concept, Long descriptorId) {
    if (concept == null) {
      return null;
    }

    MeshConceptDO dataObject = new MeshConceptDO();
    dataObject.setDescriptorId(descriptorId);
    dataObject.setConceptUi(concept.getConceptUi().ui());
    dataObject.setConceptName(concept.getConceptName());
    dataObject.setIsPreferred(concept.isPreferred());
    dataObject.setCasn1Name(concept.getCasn1Name());
    dataObject.setRegistryNumbers(concept.getRegistryNumbers());
    dataObject.setScopeNote(concept.getScopeNote());
    dataObject.setTranslatorsEnglishScopeNote(concept.getTranslatorsEnglishScopeNote());
    dataObject.setTranslatorsScopeNote(concept.getTranslatorsScopeNote());
    dataObject.setConceptStatus(concept.getConceptStatus());

    return dataObject;
  }

  /// 将入口术语实体转换为 DO。
  ///
  /// @param entryTerm 入口术语实体
  /// @param descriptorId 主题词 ID（外键）
  /// @return 入口术语 DO
  public MeshEntryTermDO toEntryTermDO(MeshEntryTerm entryTerm, Long descriptorId) {
    if (entryTerm == null) {
      return null;
    }

    MeshEntryTermDO dataObject = new MeshEntryTermDO();
    dataObject.setDescriptorId(descriptorId);
    dataObject.setTermUi(entryTerm.getTermUi() != null ? entryTerm.getTermUi().ui() : null);
    dataObject.setConceptUi(entryTerm.getConceptUi() != null ? entryTerm.getConceptUi().ui() : null);
    dataObject.setTerm(entryTerm.getTerm());
    dataObject.setLexicalTag(
        entryTerm.getLexicalTag() != null ? entryTerm.getLexicalTag().getCode() : null);
    dataObject.setIsPrintFlag(entryTerm.isPrintFlag());
    dataObject.setRecordPreferred(entryTerm.isRecordPreferred() ? "Y" : "N");
    dataObject.setIsPermutedTerm(entryTerm.isPermutedTerm());
    dataObject.setIsConceptPreferred(entryTerm.isConceptPreferred());
    dataObject.setAbbreviation(entryTerm.getAbbreviation());
    dataObject.setSortVersion(entryTerm.getSortVersion());
    dataObject.setEntryVersion(entryTerm.getEntryVersion());
    dataObject.setTermNote(entryTerm.getTermNote());
    dataObject.setDateCreated(entryTerm.getDateCreated());
    dataObject.setThesaurusIds(entryTerm.getThesaurusIds());

    return dataObject;
  }

  /// 将组合条目值对象转换为 DO。
  ///
  /// @param entryCombination 组合条目值对象
  /// @param descriptorId 主题词 ID（外键）
  /// @return 组合条目 DO
  public MeshEntryCombinationDO toEntryCombinationDO(
      EntryCombination entryCombination, Long descriptorId) {
    if (entryCombination == null) {
      return null;
    }

    MeshEntryCombinationDO dataObject = new MeshEntryCombinationDO();
    dataObject.setDescriptorId(descriptorId);
    dataObject.setEcinDescriptorUi(entryCombination.ecinDescriptorUi().ui());
    dataObject.setEcinQualifierUi(entryCombination.ecinQualifierUi().ui());
    dataObject.setEcoutDescriptorUi(entryCombination.ecoutDescriptorUi().ui());
    dataObject.setEcoutQualifierUi(
        entryCombination.ecoutQualifierUi() != null
            ? entryCombination.ecoutQualifierUi().ui()
            : null);

    return dataObject;
  }
}
