package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.exception.MeshPersistenceException;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.port.MeshQualifierRepository;
import com.patra.catalog.infra.persistence.converter.MeshQualifierConverter;
import com.patra.catalog.infra.persistence.entity.MeshQualifierDO;
import com.patra.catalog.infra.persistence.mapper.MeshQualifierMapper;
import com.patra.starter.mybatis.batch.BatchInsertHelper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// MeSH 限定词聚合根仓储实现。
///
/// **职责**：
///
/// - 管理 MeSH 限定词聚合根的持久化
///   - 批量保存限定词到数据库
///   - 聚合根与DO实体的转换
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class MeshQualifierRepositoryAdapter implements MeshQualifierRepository {

  private final MeshQualifierMapper meshQualifierMapper;
  private final MeshQualifierConverter meshQualifierConverter;

  @Override
  public void saveBatch(List<MeshQualifierAggregate> qualifiers) {
    if (qualifiers == null || qualifiers.isEmpty()) {
      log.warn("限定词列表为空，跳过保存");
      return;
    }

    log.info("批量保存限定词，数量：{}", qualifiers.size());

    // 转换为 DO 列表
    List<MeshQualifierDO> dataObjects =
        qualifiers.stream().map(meshQualifierConverter::toDataObject).toList();

    // 使用 BatchInsertHelper 批量插入（自动分片）
    var result = BatchInsertHelper.batchInsert(dataObjects, meshQualifierMapper::insertBatchSomeColumn);

    if (result.hasErrors()) {
      log.error("限定词批量保存部分失败：成功 {} / 总计 {}", result.successCount(), result.totalCount());
      throw new MeshPersistenceException("限定词批量插入部分失败，失败批次数: " + result.errors().size());
    }

    log.info("限定词批量保存完成，共 {} 条", result.successCount());
  }

  @Override
  public void truncateAll() {
    log.info("开始清空限定词表");
    meshQualifierMapper.truncateTable();
    log.info("限定词表已清空");
  }
}
