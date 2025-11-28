package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.exception.MeshPersistenceException;
import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.port.MeshDescriptorRepository;
import com.patra.catalog.infra.persistence.converter.MeshDescriptorConverter;
import com.patra.catalog.infra.persistence.entity.MeshConceptDO;
import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;
import com.patra.catalog.infra.persistence.entity.MeshEntryTermDO;
import com.patra.catalog.infra.persistence.entity.MeshTreeNumberDO;
import com.patra.catalog.infra.persistence.mapper.MeshConceptMapper;
import com.patra.catalog.infra.persistence.mapper.MeshConceptRelationMapper;
import com.patra.catalog.infra.persistence.mapper.MeshDescriptorMapper;
import com.patra.catalog.infra.persistence.mapper.MeshEntryCombinationMapper;
import com.patra.catalog.infra.persistence.mapper.MeshEntryTermMapper;
import com.patra.catalog.infra.persistence.mapper.MeshTreeNumberMapper;
import com.patra.starter.mybatis.batch.BatchInsertHelper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// MeSH 主题词聚合根仓储实现。
///
/// **职责**：
///
/// - 管理 MeSH 主题词聚合根的持久化
///   - 协调 Descriptor、TreeNumber、EntryTerm、Concept 的存储
///   - 处理树形编号层次查询
///   - 支持入口术语全文检索
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class MeshDescriptorRepositoryAdapter implements MeshDescriptorRepository {

  private final MeshDescriptorMapper meshDescriptorMapper;
  private final MeshTreeNumberMapper meshTreeNumberMapper;
  private final MeshEntryTermMapper meshEntryTermMapper;
  private final MeshConceptMapper meshConceptMapper;
  private final MeshConceptRelationMapper meshConceptRelationMapper;
  private final MeshEntryCombinationMapper meshEntryCombinationMapper;
  private final MeshDescriptorConverter meshDescriptorConverter;

  @Override
  public void saveBatch(List<MeshDescriptorAggregate> descriptors) {
    if (descriptors == null || descriptors.isEmpty()) {
      log.warn("主题词列表为空，跳过保存");
      return;
    }
    log.info("批量保存主题词，数量：{}", descriptors.size());

    List<MeshDescriptorDO> dataObjects =
        descriptors.stream().map(meshDescriptorConverter::toDescriptorDO).toList();

    var result =
        BatchInsertHelper.batchInsert(dataObjects, meshDescriptorMapper::insertBatchSomeColumn);

    if (result.hasErrors()) {
      log.error("主题词批量保存部分失败：成功 {} / 总计 {}", result.successCount(), result.totalCount());
      throw new MeshPersistenceException("主题词批量插入部分失败，失败批次数: " + result.errors().size());
    }
    log.info("主题词批量保存完成，共 {} 条", result.successCount());
  }

  @Override
  public void saveTreeNumbersBatch(List<MeshTreeNumber> treeNumbers) {
    if (treeNumbers == null || treeNumbers.isEmpty()) {
      log.warn("树形编号列表为空，跳过保存");
      return;
    }
    log.info("批量保存树形编号，数量：{}", treeNumbers.size());

    List<MeshTreeNumberDO> dataObjects =
        treeNumbers.stream()
            .map(tn -> meshDescriptorConverter.toTreeNumberDO(tn, tn.getDescriptorUi().ui()))
            .toList();

    var result =
        BatchInsertHelper.batchInsert(dataObjects, meshTreeNumberMapper::insertBatchSomeColumn);

    if (result.hasErrors()) {
      log.error("树形编号批量保存部分失败：成功 {} / 总计 {}", result.successCount(), result.totalCount());
      throw new MeshPersistenceException("树形编号批量插入部分失败，失败批次数: " + result.errors().size());
    }
    log.info("树形编号批量保存完成，共 {} 条", result.successCount());
  }

  @Override
  public void saveEntryTermsBatch(List<MeshEntryTerm> entryTerms) {
    if (entryTerms == null || entryTerms.isEmpty()) {
      log.warn("入口术语列表为空，跳过保存");
      return;
    }
    log.info("批量保存入口术语，数量：{}", entryTerms.size());

    List<MeshEntryTermDO> dataObjects =
        entryTerms.stream()
            .map(et -> meshDescriptorConverter.toEntryTermDO(et, et.getDescriptorUi().ui()))
            .toList();

    var result =
        BatchInsertHelper.batchInsert(dataObjects, meshEntryTermMapper::insertBatchSomeColumn);

    if (result.hasErrors()) {
      log.error("入口术语批量保存部分失败：成功 {} / 总计 {}", result.successCount(), result.totalCount());
      throw new MeshPersistenceException("入口术语批量插入部分失败，失败批次数: " + result.errors().size());
    }
    log.info("入口术语批量保存完成，共 {} 条", result.successCount());
  }

  @Override
  public void saveConceptsBatch(List<MeshConcept> concepts) {
    if (concepts == null || concepts.isEmpty()) {
      log.warn("概念列表为空，跳过保存");
      return;
    }
    log.info("批量保存概念，数量：{}", concepts.size());

    List<MeshConceptDO> dataObjects =
        concepts.stream()
            .map(c -> meshDescriptorConverter.toConceptDO(c, c.getDescriptorUi().ui()))
            .toList();

    var result =
        BatchInsertHelper.batchInsert(dataObjects, meshConceptMapper::insertBatchSomeColumn);

    if (result.hasErrors()) {
      log.error("概念批量保存部分失败：成功 {} / 总计 {}", result.successCount(), result.totalCount());
      throw new MeshPersistenceException("概念批量插入部分失败，失败批次数: " + result.errors().size());
    }
    log.info("概念批量保存完成，共 {} 条", result.successCount());
  }

  @Override
  public void truncateAll() {
    log.info("开始清空所有 MeSH 主题词数据");

    // 先清空子表（避免外键约束问题）
    meshTreeNumberMapper.truncateTable();
    log.debug("已清空 cat_mesh_tree_number 表");

    meshConceptRelationMapper.truncateTable();
    log.debug("已清空 cat_mesh_concept_relation 表");

    meshConceptMapper.truncateTable();
    log.debug("已清空 cat_mesh_concept 表");

    meshEntryTermMapper.truncateTable();
    log.debug("已清空 cat_mesh_entry_term 表");

    meshEntryCombinationMapper.truncateTable();
    log.debug("已清空 cat_mesh_entry_combination 表");

    // 最后清空主表
    meshDescriptorMapper.truncateTable();
    log.debug("已清空 cat_mesh_descriptor 表");

    log.info("MeSH 主题词数据清空完成");
  }
}
