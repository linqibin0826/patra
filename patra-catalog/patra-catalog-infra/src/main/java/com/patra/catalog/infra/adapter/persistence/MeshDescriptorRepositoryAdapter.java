package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.port.MeshDescriptorRepository;
import com.patra.catalog.infra.persistence.converter.MeshDescriptorConverter;
import com.patra.catalog.infra.persistence.mapper.MeshConceptMapper;
import com.patra.catalog.infra.persistence.mapper.MeshDescriptorMapper;
import com.patra.catalog.infra.persistence.mapper.MeshEntryTermMapper;
import com.patra.catalog.infra.persistence.mapper.MeshTreeNumberMapper;
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
  private final MeshDescriptorConverter meshDescriptorConverter;

  @Override
  public void saveBatch(
      java.util.List<com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate>
          descriptors) {
    // TODO: 待实现
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void saveTreeNumbersBatch(
      java.util.List<com.patra.catalog.domain.model.entity.MeshTreeNumber> treeNumbers) {
    // TODO: 待实现
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void saveEntryTermsBatch(
      java.util.List<com.patra.catalog.domain.model.entity.MeshEntryTerm> entryTerms) {
    // TODO: 待实现
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void saveConceptsBatch(
      java.util.List<com.patra.catalog.domain.model.entity.MeshConcept> concepts) {
    // TODO: 待实现
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void truncateAll() {
    log.info("开始清空所有 MeSH 主题词数据");

    // 先清空子表（避免外键约束问题）
    meshTreeNumberMapper.truncateTable();
    log.debug("已清空 cat_mesh_tree_number 表");

    meshConceptMapper.truncateTable();
    log.debug("已清空 cat_mesh_concept 表");

    meshEntryTermMapper.truncateTable();
    log.debug("已清空 cat_mesh_entry_term 表");

    // 最后清空主表
    meshDescriptorMapper.truncateTable();
    log.debug("已清空 cat_mesh_descriptor 表");

    log.info("MeSH 主题词数据清空完成");
  }
}
