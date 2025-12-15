package com.patra.catalog.infra.persistence.jpa.converter;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.enums.DescriptorClass;
import com.patra.catalog.domain.model.enums.LexicalTag;
import com.patra.catalog.domain.model.vo.mesh.ConceptRelation;
import com.patra.catalog.domain.model.vo.mesh.EntryCombination;
import com.patra.catalog.domain.model.vo.mesh.MeshDescriptorId;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.persistence.jpa.entity.MeshConceptEntity;
import com.patra.catalog.infra.persistence.jpa.entity.MeshConceptRelationEntity;
import com.patra.catalog.infra.persistence.jpa.entity.MeshDescriptorEntity;
import com.patra.catalog.infra.persistence.jpa.entity.MeshEntryCombinationEntity;
import com.patra.catalog.infra.persistence.jpa.entity.MeshEntryTermEntity;
import com.patra.catalog.infra.persistence.jpa.entity.MeshTreeNumberEntity;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/// MeSH 主题词 JPA 实体转换器。
///
/// **职责**：
///
/// - `MeshDescriptorAggregate` ↔ `MeshDescriptorEntity` 双向转换
/// - 子实体（TreeNumber、Concept、EntryTerm、EntryCombination）的双向转换
/// - MeshUI 值对象 ↔ String 的映射
/// - DescriptorClass、LexicalTag 枚举的映射
///
/// **特殊处理**：
///
/// - 子集合转换需手动调用 `addXxxToAggregate` 方法
/// - ConceptRelation 从独立表加载，需调用 `addConceptRelationsToAggregate`
/// - 某些子集合（allowableQualifiers 等）存储在 metadataJson，暂不支持
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring")
public abstract class MeshDescriptorJpaConverter {

  // ========== 主表转换 ==========

  /// 将聚合根转换为 JPA 实体。
  ///
  /// @param aggregate 主题词聚合根
  /// @return JPA 实体
  @Mapping(target = "id", source = "id", qualifiedByName = "descriptorIdToLong")
  @Mapping(target = "ui", source = "ui.ui")
  @Mapping(
      target = "descriptorClass",
      source = "descriptorClass",
      qualifiedByName = "descriptorClassToString")
  @Mapping(target = "metadata", source = "metadataJson")
  // 审计字段由 JPA 管理
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  public abstract MeshDescriptorEntity toEntity(MeshDescriptorAggregate aggregate);

  /// 将 JPA 实体转换为聚合根。
  ///
  /// 使用 `MeshDescriptorAggregate.restore()` 工厂方法重建聚合根。
  /// **注意**：转换后需手动设置子集合（treeNumbers、concepts、entryTerms 等）。
  ///
  /// @param entity JPA 实体
  /// @return 主题词聚合根
  public MeshDescriptorAggregate toAggregate(MeshDescriptorEntity entity) {
    if (entity == null) {
      return null;
    }

    MeshDescriptorAggregate aggregate =
        MeshDescriptorAggregate.restore(
            entity.getId() != null ? MeshDescriptorId.of(entity.getId()) : null,
            MeshUI.of(entity.getUi()),
            entity.getName(),
            stringToDescriptorClass(entity.getDescriptorClass()),
            entity.getActiveStatus(),
            entity.getMeshVersion(),
            entity.getScopeNote(),
            entity.getAnnotation(),
            entity.getPreviousIndexing(),
            entity.getPublicMeshNote(),
            entity.getConsiderAlso(),
            entity.getDateCreated(),
            entity.getDateRevised(),
            entity.getDateEstablished(),
            entity.getVersion());

    // 设置额外字段
    aggregate.setHistoryNote(entity.getHistoryNote());
    aggregate.setOnlineNote(entity.getOnlineNote());
    aggregate.setNlmClassificationNumber(entity.getNlmClassificationNumber());
    aggregate.setMetadataJson(entity.getMetadata());

    return aggregate;
  }

  // ========== TreeNumber 转换 ==========

  /// 将树形编号实体转换为 JPA 实体。
  ///
  /// @param treeNumber 树形编号领域实体
  /// @param descriptorUi 主题词 UI
  /// @return JPA 实体
  public MeshTreeNumberEntity toTreeNumberEntity(MeshTreeNumber treeNumber, String descriptorUi) {
    if (treeNumber == null) {
      return null;
    }
    MeshTreeNumberEntity entity = new MeshTreeNumberEntity();
    entity.setId(treeNumber.getId());
    entity.setDescriptorUi(descriptorUi);
    entity.setTreeNumber(treeNumber.getTreeNumber());
    entity.setTreeLevel(treeNumber.getTreeLevel());
    entity.setIsPrimary(treeNumber.isPrimary());
    return entity;
  }

  /// 将 JPA 实体转换为树形编号领域实体。
  ///
  /// @param entity JPA 实体
  /// @return 树形编号领域实体
  public MeshTreeNumber toTreeNumber(MeshTreeNumberEntity entity) {
    if (entity == null) {
      return null;
    }
    return MeshTreeNumber.restore(
        entity.getId(),
        entity.getDescriptorUi() != null ? MeshUI.of(entity.getDescriptorUi()) : null,
        entity.getTreeNumber(),
        entity.getTreeLevel(),
        entity.getIsPrimary());
  }

  /// 为聚合根添加树形编号。
  ///
  /// @param aggregate 聚合根
  /// @param entities 树形编号实体列表
  public void addTreeNumbersToAggregate(
      MeshDescriptorAggregate aggregate, List<MeshTreeNumberEntity> entities) {
    if (aggregate == null || entities == null) {
      return;
    }
    for (MeshTreeNumberEntity entity : entities) {
      MeshTreeNumber treeNumber = toTreeNumber(entity);
      if (treeNumber != null) {
        aggregate.addTreeNumber(treeNumber);
      }
    }
  }

  // ========== Concept 转换 ==========

  /// 将概念实体转换为 JPA 实体。
  ///
  /// @param concept 概念领域实体
  /// @param descriptorUi 主题词 UI
  /// @return JPA 实体
  public MeshConceptEntity toConceptEntity(MeshConcept concept, String descriptorUi) {
    if (concept == null) {
      return null;
    }
    MeshConceptEntity entity = new MeshConceptEntity();
    entity.setId(concept.getId());
    entity.setDescriptorUi(descriptorUi);
    entity.setConceptUi(concept.getConceptUi().ui());
    entity.setConceptName(concept.getConceptName());
    entity.setIsPreferred(concept.isPreferred());
    entity.setCasn1Name(concept.getCasn1Name());
    entity.setRegistryNumbers(new ArrayList<>(concept.getRegistryNumbers()));
    entity.setScopeNote(concept.getScopeNote());
    entity.setTranslatorsEnglishScopeNote(concept.getTranslatorsEnglishScopeNote());
    entity.setTranslatorsScopeNote(concept.getTranslatorsScopeNote());
    entity.setConceptStatus(concept.getConceptStatus());
    entity.setRelatedRegistryNumbers(new ArrayList<>(concept.getRelatedRegistryNumbers()));
    return entity;
  }

  /// 将 JPA 实体转换为概念领域实体。
  ///
  /// @param entity JPA 实体
  /// @return 概念领域实体
  public MeshConcept toConcept(MeshConceptEntity entity) {
    if (entity == null) {
      return null;
    }
    return MeshConcept.restore(
        entity.getId(),
        entity.getDescriptorUi() != null ? MeshUI.of(entity.getDescriptorUi()) : null,
        MeshUI.of(entity.getConceptUi()),
        entity.getConceptName(),
        entity.getIsPreferred(),
        entity.getCasn1Name(),
        entity.getRegistryNumbers(),
        entity.getScopeNote(),
        entity.getTranslatorsEnglishScopeNote(),
        entity.getTranslatorsScopeNote(),
        entity.getConceptStatus());
  }

  /// 为聚合根添加概念。
  ///
  /// @param aggregate 聚合根
  /// @param entities 概念实体列表
  public void addConceptsToAggregate(
      MeshDescriptorAggregate aggregate, List<MeshConceptEntity> entities) {
    if (aggregate == null || entities == null) {
      return;
    }
    for (MeshConceptEntity entity : entities) {
      MeshConcept concept = toConcept(entity);
      if (concept != null) {
        aggregate.addConcept(concept);
      }
    }
  }

  // ========== ConceptRelation 转换 ==========

  /// 将概念关系值对象转换为 JPA 实体。
  ///
  /// @param relation 概念关系值对象
  /// @param descriptorUi 主题词 UI
  /// @param conceptUi 所属概念 UI
  /// @return JPA 实体
  public MeshConceptRelationEntity toConceptRelationEntity(
      ConceptRelation relation, String descriptorUi, String conceptUi) {
    if (relation == null) {
      return null;
    }
    MeshConceptRelationEntity entity = new MeshConceptRelationEntity();
    entity.setDescriptorUi(descriptorUi);
    entity.setConceptUi(conceptUi);
    entity.setRelationName(relation.relationName());
    entity.setConcept1Ui(relation.concept1Ui().ui());
    entity.setConcept2Ui(relation.concept2Ui().ui());
    return entity;
  }

  /// 将 JPA 实体转换为概念关系值对象。
  ///
  /// @param entity JPA 实体
  /// @return 概念关系值对象
  public ConceptRelation toConceptRelation(MeshConceptRelationEntity entity) {
    if (entity == null) {
      return null;
    }
    return ConceptRelation.ofNullable(
        MeshUI.of(entity.getConcept1Ui()),
        MeshUI.of(entity.getConcept2Ui()),
        entity.getRelationName());
  }

  /// 为聚合根中的概念添加概念关系。
  ///
  /// @param aggregate 聚合根
  /// @param relationEntities 概念关系实体列表
  public void addConceptRelationsToAggregate(
      MeshDescriptorAggregate aggregate, List<MeshConceptRelationEntity> relationEntities) {
    if (aggregate == null || relationEntities == null || relationEntities.isEmpty()) {
      return;
    }

    for (MeshConceptRelationEntity entity : relationEntities) {
      // 找到对应的概念
      String conceptUi = entity.getConceptUi();
      aggregate.getConcepts().stream()
          .filter(c -> c.getConceptUi().ui().equals(conceptUi))
          .findFirst()
          .ifPresent(concept -> concept.addConceptRelation(toConceptRelation(entity)));
    }
  }

  // ========== EntryTerm 转换 ==========

  /// 将入口术语实体转换为 JPA 实体。
  ///
  /// @param entryTerm 入口术语领域实体
  /// @param descriptorUi 主题词 UI
  /// @return JPA 实体
  public MeshEntryTermEntity toEntryTermEntity(MeshEntryTerm entryTerm, String descriptorUi) {
    if (entryTerm == null) {
      return null;
    }
    MeshEntryTermEntity entity = new MeshEntryTermEntity();
    entity.setId(entryTerm.getId());
    entity.setDescriptorUi(descriptorUi);
    entity.setTermUi(entryTerm.getTermUi() != null ? entryTerm.getTermUi().ui() : null);
    entity.setConceptUi(entryTerm.getConceptUi() != null ? entryTerm.getConceptUi().ui() : null);
    entity.setTerm(entryTerm.getTerm());
    entity.setLexicalTag(
        entryTerm.getLexicalTag() != null ? entryTerm.getLexicalTag().getCode() : null);
    entity.setIsPrintFlag(entryTerm.isPrintFlag());
    entity.setRecordPreferred(entryTerm.isRecordPreferred() ? "Y" : "N");
    entity.setIsPermutedTerm(entryTerm.isPermutedTerm());
    entity.setIsConceptPreferred(entryTerm.isConceptPreferred());
    entity.setAbbreviation(entryTerm.getAbbreviation());
    entity.setSortVersion(entryTerm.getSortVersion());
    entity.setEntryVersion(entryTerm.getEntryVersion());
    entity.setTermNote(entryTerm.getTermNote());
    entity.setDateCreated(entryTerm.getDateCreated());
    entity.setThesaurusIds(new ArrayList<>(entryTerm.getThesaurusIds()));
    return entity;
  }

  /// 将 JPA 实体转换为入口术语领域实体。
  ///
  /// @param entity JPA 实体
  /// @return 入口术语领域实体
  public MeshEntryTerm toEntryTerm(MeshEntryTermEntity entity) {
    if (entity == null) {
      return null;
    }

    MeshEntryTerm entryTerm =
        MeshEntryTerm.restore(
            entity.getId(),
            entity.getDescriptorUi() != null ? MeshUI.of(entity.getDescriptorUi()) : null,
            entity.getTermUi() != null ? MeshUI.of(entity.getTermUi()) : null,
            entity.getTerm(),
            stringToLexicalTag(entity.getLexicalTag()),
            entity.getIsPrintFlag(),
            "Y".equals(entity.getRecordPreferred()),
            entity.getIsPermutedTerm(),
            entity.getIsConceptPreferred());

    // 设置可选字段
    if (entity.getConceptUi() != null) {
      entryTerm.withConceptUi(MeshUI.of(entity.getConceptUi()));
    }
    entryTerm.withAbbreviation(entity.getAbbreviation());
    entryTerm.withSortVersion(entity.getSortVersion());
    entryTerm.withEntryVersion(entity.getEntryVersion());
    entryTerm.withTermNote(entity.getTermNote());
    entryTerm.withDateCreated(entity.getDateCreated());
    if (entity.getThesaurusIds() != null) {
      entryTerm.addThesaurusIds(entity.getThesaurusIds());
    }

    return entryTerm;
  }

  /// 为聚合根添加入口术语。
  ///
  /// @param aggregate 聚合根
  /// @param entities 入口术语实体列表
  public void addEntryTermsToAggregate(
      MeshDescriptorAggregate aggregate, List<MeshEntryTermEntity> entities) {
    if (aggregate == null || entities == null) {
      return;
    }
    for (MeshEntryTermEntity entity : entities) {
      MeshEntryTerm entryTerm = toEntryTerm(entity);
      if (entryTerm != null) {
        aggregate.addEntryTerm(entryTerm);
      }
    }
  }

  // ========== EntryCombination 转换 ==========

  /// 将组合条目值对象转换为 JPA 实体。
  ///
  /// @param entryCombination 组合条目值对象
  /// @param descriptorUi 主题词 UI
  /// @return JPA 实体
  public MeshEntryCombinationEntity toEntryCombinationEntity(
      EntryCombination entryCombination, String descriptorUi) {
    if (entryCombination == null) {
      return null;
    }
    MeshEntryCombinationEntity entity = new MeshEntryCombinationEntity();
    entity.setDescriptorUi(descriptorUi);
    entity.setEcinDescriptorUi(entryCombination.ecinDescriptorUi().ui());
    entity.setEcinQualifierUi(entryCombination.ecinQualifierUi().ui());
    entity.setEcoutDescriptorUi(entryCombination.ecoutDescriptorUi().ui());
    entity.setEcoutQualifierUi(
        entryCombination.ecoutQualifierUi() != null
            ? entryCombination.ecoutQualifierUi().ui()
            : null);
    return entity;
  }

  /// 将 JPA 实体转换为组合条目值对象。
  ///
  /// @param entity JPA 实体
  /// @return 组合条目值对象
  public EntryCombination toEntryCombination(MeshEntryCombinationEntity entity) {
    if (entity == null) {
      return null;
    }
    return EntryCombination.of(
        MeshUI.of(entity.getEcinDescriptorUi()),
        MeshUI.of(entity.getEcinQualifierUi()),
        MeshUI.of(entity.getEcoutDescriptorUi()),
        entity.getEcoutQualifierUi() != null ? MeshUI.of(entity.getEcoutQualifierUi()) : null);
  }

  /// 为聚合根添加组合条目。
  ///
  /// @param aggregate 聚合根
  /// @param entities 组合条目实体列表
  public void addEntryCombinationsToAggregate(
      MeshDescriptorAggregate aggregate, List<MeshEntryCombinationEntity> entities) {
    if (aggregate == null || entities == null) {
      return;
    }
    for (MeshEntryCombinationEntity entity : entities) {
      EntryCombination entryCombination = toEntryCombination(entity);
      if (entryCombination != null) {
        aggregate.addEntryCombination(entryCombination);
      }
    }
  }

  // ========== ID 转换方法 ==========

  /// 将 MeshDescriptorId 转换为 Long。
  @Named("descriptorIdToLong")
  Long descriptorIdToLong(MeshDescriptorId id) {
    return id != null ? id.value() : null;
  }

  // ========== 枚举转换方法 ==========

  /// 将 DescriptorClass 枚举转换为 String。
  @Named("descriptorClassToString")
  String descriptorClassToString(DescriptorClass descriptorClass) {
    return descriptorClass != null ? descriptorClass.getCode() : null;
  }

  /// 将 String 转换为 DescriptorClass 枚举。
  DescriptorClass stringToDescriptorClass(String code) {
    return code != null ? DescriptorClass.fromCode(code) : null;
  }

  /// 将 String 转换为 LexicalTag 枚举。
  LexicalTag stringToLexicalTag(String code) {
    return code != null ? LexicalTag.fromCode(code) : null;
  }
}
