package com.patra.catalog.infra.adapter.persistence;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.port.repository.MeshDescriptorRepository;
import com.patra.catalog.infra.persistence.converter.MeshDescriptorConverter;
import com.patra.catalog.infra.persistence.entity.MeshConceptDO;
import com.patra.catalog.infra.persistence.entity.MeshConceptRelationDO;
import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;
import com.patra.catalog.infra.persistence.entity.MeshEntryCombinationDO;
import com.patra.catalog.infra.persistence.entity.MeshEntryTermDO;
import com.patra.catalog.infra.persistence.entity.MeshTreeNumberDO;
import com.patra.catalog.infra.persistence.mapper.MeshConceptMapper;
import com.patra.catalog.infra.persistence.mapper.MeshConceptRelationMapper;
import com.patra.catalog.infra.persistence.mapper.MeshDescriptorMapper;
import com.patra.catalog.infra.persistence.mapper.MeshEntryCombinationMapper;
import com.patra.catalog.infra.persistence.mapper.MeshEntryTermMapper;
import com.patra.catalog.infra.persistence.mapper.MeshTreeNumberMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// MeSH 主题词聚合根仓储实现。
///
/// **职责**：
///
/// - 管理 MeshDescriptorAggregate（主题词聚合根）的持久化
/// - 支持 NLM MeSH XML 批量数据导入
/// - 以聚合根为单位保证数据一致性
///
/// **性能优化**：
///
/// 批量操作使用 `insertBatchSomeColumn` 提升写入效率
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class MeshDescriptorRepositoryAdapter implements MeshDescriptorRepository {

  private final MeshDescriptorMapper descriptorMapper;
  private final MeshTreeNumberMapper treeNumberMapper;
  private final MeshConceptMapper conceptMapper;
  private final MeshConceptRelationMapper conceptRelationMapper;
  private final MeshEntryTermMapper entryTermMapper;
  private final MeshEntryCombinationMapper entryCombinationMapper;
  private final MeshDescriptorConverter converter;

  @Override
  public boolean hasAnyData() {
    return descriptorMapper.selectCount(null) > 0;
  }

  @Override
  public void insertAll(List<MeshDescriptorAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    // 1. 转换 Descriptor 并预先生成 ID
    List<MeshDescriptorDO> descriptorDOs = new ArrayList<>(aggregates.size());
    for (MeshDescriptorAggregate aggregate : aggregates) {
      MeshDescriptorDO dataObject = converter.toDescriptorDO(aggregate);
      dataObject.setId(IdWorker.getId());
      descriptorDOs.add(dataObject);
    }

    // 2. 收集子表数据
    List<MeshTreeNumberDO> treeNumberDOs = new ArrayList<>();
    List<MeshConceptDO> conceptDOs = new ArrayList<>();
    List<MeshConceptRelationDO> conceptRelationDOs = new ArrayList<>();
    List<MeshEntryTermDO> entryTermDOs = new ArrayList<>();
    List<MeshEntryCombinationDO> entryCombinationDOs = new ArrayList<>();

    for (MeshDescriptorAggregate aggregate : aggregates) {
      String descriptorUi = aggregate.getUi().ui();
      collectChildData(
          aggregate,
          descriptorUi,
          treeNumberDOs,
          conceptDOs,
          conceptRelationDOs,
          entryTermDOs,
          entryCombinationDOs);
    }

    // 3. 批量插入主表
    descriptorMapper.insertBatchSomeColumn(descriptorDOs);
    log.debug("批量插入主题词 {} 条", descriptorDOs.size());

    // 4. 批量插入子表
    if (!treeNumberDOs.isEmpty()) {
      treeNumberMapper.insertBatchSomeColumn(treeNumberDOs);
      log.debug("批量插入树形编号 {} 条", treeNumberDOs.size());
    }
    if (!conceptDOs.isEmpty()) {
      conceptMapper.insertBatchSomeColumn(conceptDOs);
      log.debug("批量插入概念 {} 条", conceptDOs.size());
    }
    if (!conceptRelationDOs.isEmpty()) {
      conceptRelationMapper.insertBatchSomeColumn(conceptRelationDOs);
      log.debug("批量插入概念关系 {} 条", conceptRelationDOs.size());
    }
    if (!entryTermDOs.isEmpty()) {
      entryTermMapper.insertBatchSomeColumn(entryTermDOs);
      log.debug("批量插入入口术语 {} 条", entryTermDOs.size());
    }
    if (!entryCombinationDOs.isEmpty()) {
      entryCombinationMapper.insertBatchSomeColumn(entryCombinationDOs);
      log.debug("批量插入入口组合 {} 条", entryCombinationDOs.size());
    }
  }

  /// 收集聚合根的子表数据。
  ///
  /// @param aggregate 聚合根
  /// @param descriptorUi 主题词 UI（用于子表外键关联）
  /// @param treeNumberDOs 树形编号 DO 列表（输出参数）
  /// @param conceptDOs 概念 DO 列表（输出参数）
  /// @param conceptRelationDOs 概念关系 DO 列表（输出参数）
  /// @param entryTermDOs 入口术语 DO 列表（输出参数）
  /// @param entryCombinationDOs 入口组合 DO 列表（输出参数）
  private void collectChildData(
      MeshDescriptorAggregate aggregate,
      String descriptorUi,
      List<MeshTreeNumberDO> treeNumberDOs,
      List<MeshConceptDO> conceptDOs,
      List<MeshConceptRelationDO> conceptRelationDOs,
      List<MeshEntryTermDO> entryTermDOs,
      List<MeshEntryCombinationDO> entryCombinationDOs) {

    // 收集 TreeNumber
    aggregate
        .getTreeNumbers()
        .forEach(
            tn -> {
              MeshTreeNumberDO treeNumberDO = converter.toTreeNumberDO(tn, descriptorUi);
              treeNumberDO.setId(IdWorker.getId());
              treeNumberDOs.add(treeNumberDO);
            });

    // 收集 Concept 和 ConceptRelation
    aggregate
        .getConcepts()
        .forEach(
            concept -> {
              MeshConceptDO conceptDO = converter.toConceptDO(concept, descriptorUi);
              conceptDO.setId(IdWorker.getId());
              conceptDOs.add(conceptDO);

              // 收集概念的 ConceptRelation
              collectConceptRelations(concept, descriptorUi, conceptRelationDOs);
            });

    // 收集 EntryTerm
    aggregate
        .getEntryTerms()
        .forEach(
            et -> {
              MeshEntryTermDO entryTermDO = converter.toEntryTermDO(et, descriptorUi);
              entryTermDO.setId(IdWorker.getId());
              entryTermDOs.add(entryTermDO);
            });

    // 收集 EntryCombination
    aggregate
        .getEntryCombinations()
        .forEach(
            ec -> {
              MeshEntryCombinationDO entryCombinationDO =
                  converter.toEntryCombinationDO(ec, descriptorUi);
              entryCombinationDO.setId(IdWorker.getId());
              entryCombinationDOs.add(entryCombinationDO);
            });
  }

  /// 收集概念的关系数据。
  ///
  /// @param concept 概念实体
  /// @param descriptorUi 主题词 UI
  /// @param conceptRelationDOs 概念关系 DO 列表（输出参数）
  private void collectConceptRelations(
      MeshConcept concept, String descriptorUi, List<MeshConceptRelationDO> conceptRelationDOs) {
    concept
        .getConceptRelations()
        .forEach(
            cr -> {
              MeshConceptRelationDO conceptRelationDO =
                  converter.toConceptRelationDO(
                      cr, concept.getConceptUi(), concept.isPreferred(), descriptorUi);
              conceptRelationDO.setId(IdWorker.getId());
              conceptRelationDOs.add(conceptRelationDO);
            });
  }
}
