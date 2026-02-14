package com.patra.catalog.infra.persistence.converter.mapper;

import com.patra.catalog.domain.model.aggregate.MeshScrAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.enums.LexicalTag;
import com.patra.catalog.domain.model.enums.MeshRecordType;
import com.patra.catalog.domain.model.enums.ScrClass;
import com.patra.catalog.domain.model.vo.mesh.HeadingMappedTo;
import com.patra.catalog.domain.model.vo.mesh.IndexingInfo;
import com.patra.catalog.domain.model.vo.mesh.MeshScrId;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.domain.model.vo.mesh.PharmacologicalAction;
import com.patra.catalog.domain.model.vo.mesh.ScrSource;
import com.patra.catalog.infra.persistence.entity.MeshConceptEntity;
import com.patra.catalog.infra.persistence.entity.MeshEntryTermEntity;
import com.patra.catalog.infra.persistence.entity.MeshScrEntity;
import com.patra.catalog.infra.persistence.entity.MeshScrHeadingMappedToEntity;
import com.patra.catalog.infra.persistence.entity.MeshScrIndexingInfoEntity;
import com.patra.catalog.infra.persistence.entity.MeshScrPharmacologicalActionEntity;
import com.patra.catalog.infra.persistence.entity.MeshScrSourceEntity;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/// MeSH SCR JPA 实体转换器。
///
/// **职责**：
///
/// - `MeshScrAggregate` ↔ `MeshScrEntity` 双向转换
/// - 子实体（HeadingMappedTo、Concept、Source、IndexingInfo、PharmacologicalAction）的转换
/// - MeshUI 值对象 ↔ String 的映射
/// - ScrClass 枚举的映射
///
/// **特殊处理**：
///
/// - 子集合转换需手动调用 `addXxxToAggregate` 方法
/// - Concept 复用 MeshConceptEntity，添加 recordType=SCR
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring")
public abstract class MeshScrJpaMapper {

  // ========== 主表转换 ==========

  /// 将聚合根转换为 JPA 实体。
  ///
  /// @param aggregate SCR 聚合根
  /// @return JPA 实体
  @Mapping(target = "id", source = "id", qualifiedByName = "scrIdToLong")
  @Mapping(target = "ui", source = "ui.ui")
  @Mapping(target = "scrClass", source = "scrClass", qualifiedByName = "scrClassToInt")
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
  public abstract MeshScrEntity toEntity(MeshScrAggregate aggregate);

  /// 将 JPA 实体转换为聚合根。
  ///
  /// 使用 `MeshScrAggregate.restore()` 工厂方法重建聚合根。
  /// **注意**：转换后需手动设置子集合。
  ///
  /// @param entity JPA 实体
  /// @return SCR 聚合根
  public MeshScrAggregate toAggregate(MeshScrEntity entity) {
    if (entity == null) {
      return null;
    }

    MeshScrAggregate aggregate =
        MeshScrAggregate.restore(
            MeshUI.of(entity.getUi()),
            entity.getName(),
            intToScrClass(entity.getScrClass()),
            entity.getNote(),
            entity.getFrequency(),
            entity.getPreviousIndexing(),
            entity.getDateCreated(),
            entity.getDateRevised(),
            entity.isActiveStatus(),
            entity.getMeshVersion(),
            entity.getMetadata());

    // 设置 ID
    if (entity.getId() != null) {
      aggregate.assignId(MeshScrId.of(entity.getId()));
    }

    return aggregate;
  }

  // ========== HeadingMappedTo 转换 ==========

  /// 将映射关系值对象转换为 JPA 实体。
  ///
  /// @param mapping 映射关系值对象
  /// @param scrUi SCR UI
  /// @return JPA 实体
  public MeshScrHeadingMappedToEntity toHeadingMappedToEntity(
      HeadingMappedTo mapping, String scrUi) {
    if (mapping == null) {
      return null;
    }
    MeshScrHeadingMappedToEntity entity = new MeshScrHeadingMappedToEntity();
    entity.setScrUi(scrUi);
    entity.setDescriptorUi(mapping.descriptorUi().ui());
    entity.setQualifierUi(mapping.qualifierUi() != null ? mapping.qualifierUi().ui() : null);
    entity.setMajorTopic(mapping.majorTopic());
    return entity;
  }

  /// 将 JPA 实体转换为映射关系值对象。
  ///
  /// @param entity JPA 实体
  /// @return 映射关系值对象
  public HeadingMappedTo toHeadingMappedTo(MeshScrHeadingMappedToEntity entity) {
    if (entity == null) {
      return null;
    }
    return HeadingMappedTo.of(
        MeshUI.of(entity.getDescriptorUi()),
        entity.getQualifierUi() != null ? MeshUI.of(entity.getQualifierUi()) : null,
        entity.isMajorTopic());
  }

  /// 为聚合根添加映射关系。
  ///
  /// @param aggregate 聚合根
  /// @param entities 映射关系实体列表
  public void addHeadingMappedTosToAggregate(
      MeshScrAggregate aggregate, List<MeshScrHeadingMappedToEntity> entities) {
    if (aggregate == null || entities == null) {
      return;
    }
    for (MeshScrHeadingMappedToEntity entity : entities) {
      HeadingMappedTo mapping = toHeadingMappedTo(entity);
      if (mapping != null) {
        aggregate.addHeadingMappedTo(mapping);
      }
    }
  }

  // ========== Concept 转换（复用 MeshConceptEntity）==========

  /// 将概念实体转换为 JPA 实体。
  ///
  /// @param concept 概念领域实体
  /// @param scrUi SCR UI
  /// @return JPA 实体
  public MeshConceptEntity toConceptEntity(MeshConcept concept, String scrUi) {
    if (concept == null) {
      return null;
    }
    MeshConceptEntity entity = new MeshConceptEntity();
    entity.setId(concept.getId());
    entity.setRecordType(MeshRecordType.SCR);
    entity.setOwnerUi(scrUi);
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
        entity.getOwnerUi() != null ? MeshUI.of(entity.getOwnerUi()) : null,
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
  public void addConceptsToAggregate(MeshScrAggregate aggregate, List<MeshConceptEntity> entities) {
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

  // ========== Source 转换 ==========

  /// 将来源值对象转换为 JPA 实体。
  ///
  /// @param source 来源值对象
  /// @param scrUi SCR UI
  /// @param orderNum 排序号
  /// @return JPA 实体
  public MeshScrSourceEntity toSourceEntity(ScrSource source, String scrUi, int orderNum) {
    if (source == null) {
      return null;
    }
    MeshScrSourceEntity entity = new MeshScrSourceEntity();
    entity.setScrUi(scrUi);
    entity.setSource(source.source());
    entity.setOrderNum(orderNum);
    return entity;
  }

  /// 将 JPA 实体转换为来源值对象。
  ///
  /// @param entity JPA 实体
  /// @return 来源值对象
  public ScrSource toSource(MeshScrSourceEntity entity) {
    if (entity == null) {
      return null;
    }
    return ScrSource.of(entity.getSource(), entity.getOrderNum());
  }

  /// 为聚合根添加来源。
  ///
  /// @param aggregate 聚合根
  /// @param entities 来源实体列表
  public void addSourcesToAggregate(
      MeshScrAggregate aggregate, List<MeshScrSourceEntity> entities) {
    if (aggregate == null || entities == null) {
      return;
    }
    for (MeshScrSourceEntity entity : entities) {
      ScrSource source = toSource(entity);
      if (source != null) {
        aggregate.addSource(source);
      }
    }
  }

  // ========== IndexingInfo 转换 ==========

  /// 将索引信息值对象转换为 JPA 实体。
  ///
  /// @param info 索引信息值对象
  /// @param scrUi SCR UI
  /// @return JPA 实体
  public MeshScrIndexingInfoEntity toIndexingInfoEntity(IndexingInfo info, String scrUi) {
    if (info == null) {
      return null;
    }
    MeshScrIndexingInfoEntity entity = new MeshScrIndexingInfoEntity();
    entity.setScrUi(scrUi);
    entity.setDescriptorUi(info.descriptorUi() != null ? info.descriptorUi().ui() : null);
    entity.setQualifierUi(info.qualifierUi() != null ? info.qualifierUi().ui() : null);
    entity.setChemicalUi(info.chemicalUi() != null ? info.chemicalUi().ui() : null);
    return entity;
  }

  /// 将 JPA 实体转换为索引信息值对象。
  ///
  /// @param entity JPA 实体
  /// @return 索引信息值对象
  public IndexingInfo toIndexingInfo(MeshScrIndexingInfoEntity entity) {
    if (entity == null) {
      return null;
    }
    return IndexingInfo.of(
        entity.getDescriptorUi() != null ? MeshUI.of(entity.getDescriptorUi()) : null,
        entity.getQualifierUi() != null ? MeshUI.of(entity.getQualifierUi()) : null,
        entity.getChemicalUi() != null ? MeshUI.of(entity.getChemicalUi()) : null);
  }

  /// 为聚合根添加索引信息。
  ///
  /// @param aggregate 聚合根
  /// @param entities 索引信息实体列表
  public void addIndexingInfosToAggregate(
      MeshScrAggregate aggregate, List<MeshScrIndexingInfoEntity> entities) {
    if (aggregate == null || entities == null) {
      return;
    }
    for (MeshScrIndexingInfoEntity entity : entities) {
      IndexingInfo info = toIndexingInfo(entity);
      if (info != null) {
        aggregate.addIndexingInfo(info);
      }
    }
  }

  // ========== PharmacologicalAction 转换 ==========

  /// 将药理作用值对象转换为 JPA 实体。
  ///
  /// @param action 药理作用值对象
  /// @param scrUi SCR UI
  /// @return JPA 实体
  public MeshScrPharmacologicalActionEntity toPharmacologicalActionEntity(
      PharmacologicalAction action, String scrUi) {
    if (action == null) {
      return null;
    }
    MeshScrPharmacologicalActionEntity entity = new MeshScrPharmacologicalActionEntity();
    entity.setScrUi(scrUi);
    entity.setDescriptorUi(action.descriptorUi().ui());
    entity.setDescriptorName(action.descriptorName());
    return entity;
  }

  /// 将 JPA 实体转换为药理作用值对象。
  ///
  /// @param entity JPA 实体
  /// @return 药理作用值对象
  public PharmacologicalAction toPharmacologicalAction(MeshScrPharmacologicalActionEntity entity) {
    if (entity == null) {
      return null;
    }
    return PharmacologicalAction.of(
        MeshUI.of(entity.getDescriptorUi()), entity.getDescriptorName());
  }

  /// 为聚合根添加药理作用。
  ///
  /// @param aggregate 聚合根
  /// @param entities 药理作用实体列表
  public void addPharmacologicalActionsToAggregate(
      MeshScrAggregate aggregate, List<MeshScrPharmacologicalActionEntity> entities) {
    if (aggregate == null || entities == null) {
      return;
    }
    for (MeshScrPharmacologicalActionEntity entity : entities) {
      PharmacologicalAction action = toPharmacologicalAction(entity);
      if (action != null) {
        aggregate.addPharmacologicalAction(action);
      }
    }
  }

  // ========== EntryTerm 转换（复用 MeshEntryTermEntity）==========

  /// 将入口术语领域实体转换为 JPA 实体。
  ///
  /// @param entryTerm 入口术语领域实体
  /// @param scrUi SCR UI
  /// @return JPA 实体
  public MeshEntryTermEntity toEntryTermEntity(MeshEntryTerm entryTerm, String scrUi) {
    if (entryTerm == null) {
      return null;
    }
    MeshEntryTermEntity entity = new MeshEntryTermEntity();
    entity.setId(entryTerm.getId());
    entity.setRecordType(MeshRecordType.SCR);
    entity.setOwnerUi(scrUi);
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
            entity.getOwnerUi() != null ? MeshUI.of(entity.getOwnerUi()) : null,
            entity.getTermUi() != null ? MeshUI.of(entity.getTermUi()) : null,
            entity.getTerm(),
            entity.getLexicalTag() != null ? LexicalTag.fromCode(entity.getLexicalTag()) : null,
            entity.getIsPrintFlag() != null && entity.getIsPrintFlag(),
            "Y".equals(entity.getRecordPreferred()),
            entity.getIsPermutedTerm() != null && entity.getIsPermutedTerm(),
            entity.getIsConceptPreferred() != null && entity.getIsConceptPreferred());

    // 设置可选字段
    if (entity.getConceptUi() != null) {
      entryTerm.withConceptUi(MeshUI.of(entity.getConceptUi()));
    }
    if (entity.getAbbreviation() != null) {
      entryTerm.withAbbreviation(entity.getAbbreviation());
    }
    if (entity.getSortVersion() != null) {
      entryTerm.withSortVersion(entity.getSortVersion());
    }
    if (entity.getEntryVersion() != null) {
      entryTerm.withEntryVersion(entity.getEntryVersion());
    }
    if (entity.getTermNote() != null) {
      entryTerm.withTermNote(entity.getTermNote());
    }
    if (entity.getDateCreated() != null) {
      entryTerm.withDateCreated(entity.getDateCreated());
    }
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
      MeshScrAggregate aggregate, List<MeshEntryTermEntity> entities) {
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

  // ========== ID 转换方法 ==========

  /// 将 MeshScrId 转换为 Long。
  @Named("scrIdToLong")
  Long scrIdToLong(MeshScrId id) {
    return id != null ? id.value() : null;
  }

  // ========== 枚举转换方法 ==========

  /// 将 ScrClass 枚举转换为 Integer。
  @Named("scrClassToInt")
  Integer scrClassToInt(ScrClass scrClass) {
    return scrClass != null ? scrClass.getCode() : null;
  }

  /// 将 Integer 转换为 ScrClass 枚举。
  ScrClass intToScrClass(Integer code) {
    return code != null ? ScrClass.fromCode(code) : null;
  }
}
