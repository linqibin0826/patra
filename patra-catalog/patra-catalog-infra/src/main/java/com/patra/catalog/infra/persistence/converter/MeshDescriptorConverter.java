package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.vo.mesh.ConceptRelation;
import com.patra.catalog.domain.model.vo.mesh.EntryCombination;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.persistence.entity.MeshConceptDO;
import com.patra.catalog.infra.persistence.entity.MeshConceptRelationDO;
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
  /// @param descriptorUi 主题词 UI
  /// @return 树形编号 DO
  public MeshTreeNumberDO toTreeNumberDO(MeshTreeNumber treeNumber, String descriptorUi) {
    if (treeNumber == null) {
      return null;
    }

    MeshTreeNumberDO dataObject = new MeshTreeNumberDO();
    dataObject.setDescriptorUi(descriptorUi);
    dataObject.setTreeNumber(treeNumber.getTreeNumber());
    dataObject.setTreeLevel(treeNumber.getTreeLevel());
    dataObject.setIsPrimary(treeNumber.isPrimary());

    return dataObject;
  }

  /// 将概念实体转换为 DO。
  ///
  /// @param concept 概念实体
  /// @param descriptorUi 主题词 UI
  /// @return 概念 DO
  public MeshConceptDO toConceptDO(MeshConcept concept, String descriptorUi) {
    if (concept == null) {
      return null;
    }

    MeshConceptDO dataObject = new MeshConceptDO();
    dataObject.setDescriptorUi(descriptorUi);
    dataObject.setConceptUi(concept.getConceptUi().ui());
    dataObject.setConceptName(concept.getConceptName());
    dataObject.setIsPreferred(concept.isPreferred());
    dataObject.setCasn1Name(concept.getCasn1Name());
    dataObject.setRegistryNumbers(concept.getRegistryNumbers());
    dataObject.setRelatedRegistryNumbers(concept.getRelatedRegistryNumbers());
    dataObject.setScopeNote(concept.getScopeNote());
    dataObject.setTranslatorsEnglishScopeNote(concept.getTranslatorsEnglishScopeNote());
    dataObject.setTranslatorsScopeNote(concept.getTranslatorsScopeNote());
    dataObject.setConceptStatus(concept.getConceptStatus());

    return dataObject;
  }

  /// 将入口术语实体转换为 DO。
  ///
  /// @param entryTerm 入口术语实体
  /// @param descriptorUi 主题词 UI
  /// @return 入口术语 DO
  public MeshEntryTermDO toEntryTermDO(MeshEntryTerm entryTerm, String descriptorUi) {
    if (entryTerm == null) {
      return null;
    }

    MeshEntryTermDO dataObject = new MeshEntryTermDO();
    dataObject.setDescriptorUi(descriptorUi);
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
  /// @param descriptorUi 主题词 UI
  /// @return 组合条目 DO
  public MeshEntryCombinationDO toEntryCombinationDO(
      EntryCombination entryCombination, String descriptorUi) {
    if (entryCombination == null) {
      return null;
    }

    MeshEntryCombinationDO dataObject = new MeshEntryCombinationDO();
    dataObject.setDescriptorUi(descriptorUi);
    dataObject.setEcinDescriptorUi(entryCombination.ecinDescriptorUi().ui());
    dataObject.setEcinQualifierUi(entryCombination.ecinQualifierUi().ui());
    dataObject.setEcoutDescriptorUi(entryCombination.ecoutDescriptorUi().ui());
    dataObject.setEcoutQualifierUi(
        entryCombination.ecoutQualifierUi() != null
            ? entryCombination.ecoutQualifierUi().ui()
            : null);

    return dataObject;
  }

  /// 将概念关系值对象转换为 DO。
  ///
  /// @param conceptRelation 概念关系值对象
  /// @param conceptUi 所属概念 UI（拥有此关系列表的概念）
  /// @param isPreferred 所属概念是否为首选概念
  /// @param descriptorUi 主题词 UI
  /// @return 概念关系 DO
  public MeshConceptRelationDO toConceptRelationDO(
      ConceptRelation conceptRelation, MeshUI conceptUi, boolean isPreferred, String descriptorUi) {
    if (conceptRelation == null) {
      return null;
    }

    MeshConceptRelationDO dataObject = new MeshConceptRelationDO();
    dataObject.setDescriptorUi(descriptorUi);
    dataObject.setConceptUi(conceptUi.ui());
    dataObject.setIsPreferred(isPreferred);
    dataObject.setRelationName(conceptRelation.relationName());
    dataObject.setConcept1Ui(conceptRelation.concept1Ui().ui());
    dataObject.setConcept2Ui(conceptRelation.concept2Ui().ui());

    return dataObject;
  }
}
