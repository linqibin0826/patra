package com.patra.catalog.infra.batch;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.infra.persistence.converter.MeshDescriptorConverter;
import com.patra.catalog.infra.persistence.entity.MeshConceptDO;
import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;
import com.patra.catalog.infra.persistence.entity.MeshEntryTermDO;
import com.patra.catalog.infra.persistence.entity.MeshTreeNumberDO;
import com.patra.catalog.infra.persistence.mapper.MeshConceptMapper;
import com.patra.catalog.infra.persistence.mapper.MeshDescriptorMapper;
import com.patra.catalog.infra.persistence.mapper.MeshEntryTermMapper;
import com.patra.catalog.infra.persistence.mapper.MeshTreeNumberMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/// MeSH 主题词批量写入器。
///
/// **职责**：
///
/// - 批量写入 Descriptor 主表及所有关联实体
///   - 转换聚合根为 DO
///   - 建立主表与子表的外键关联
///
/// **写入顺序**：
///
/// 1. 批量 INSERT Descriptor 主表（预先生成雪花 ID）
/// 2. 批量 INSERT TreeNumber 子表
/// 3. 批量 INSERT Concept 子表
/// 4. 批量 INSERT EntryTerm 子表
///
/// **性能优化**：
///
/// - 使用单条 SQL 批量插入（`INSERT INTO ... VALUES (...), (...), ...`）
/// - 避免逐条 INSERT 产生的网络开销
/// - 典型场景：chunk size 100，每次写入约 100 主表 + 500 子表记录
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshDescriptorItemWriter implements ItemWriter<MeshDescriptorAggregate> {

  private final MeshDescriptorMapper descriptorMapper;
  private final MeshTreeNumberMapper treeNumberMapper;
  private final MeshConceptMapper conceptMapper;
  private final MeshEntryTermMapper entryTermMapper;
  private final MeshDescriptorConverter converter;

  @Override
  public void write(Chunk<? extends MeshDescriptorAggregate> chunk) throws Exception {
    List<? extends MeshDescriptorAggregate> items = chunk.getItems();
    if (items.isEmpty()) {
      return;
    }

    log.debug("开始写入 {} 条 Descriptor 记录", items.size());

    // 1. 转换 Descriptor 并预先生成 ID
    List<MeshDescriptorDO> descriptorDOs = new ArrayList<>(items.size());
    for (MeshDescriptorAggregate aggregate : items) {
      MeshDescriptorDO dataObject = converter.toDescriptorDO(aggregate);
      dataObject.setId(IdWorker.getId());
      descriptorDOs.add(dataObject);
    }

    // 2. 收集子表数据并设置外键和 ID
    List<MeshTreeNumberDO> treeNumberDOs = new ArrayList<>();
    List<MeshConceptDO> conceptDOs = new ArrayList<>();
    List<MeshEntryTermDO> entryTermDOs = new ArrayList<>();

    for (int i = 0; i < items.size(); i++) {
      MeshDescriptorAggregate aggregate = items.get(i);
      Long descriptorId = descriptorDOs.get(i).getId();

      // TreeNumber
      aggregate.getTreeNumbers().forEach(tn -> {
        MeshTreeNumberDO treeNumberDO = converter.toTreeNumberDO(tn, descriptorId);
        treeNumberDO.setId(IdWorker.getId());
        treeNumberDOs.add(treeNumberDO);
      });

      // Concept
      aggregate.getConcepts().forEach(c -> {
        MeshConceptDO conceptDO = converter.toConceptDO(c, descriptorId);
        conceptDO.setId(IdWorker.getId());
        conceptDOs.add(conceptDO);
      });

      // EntryTerm
      aggregate.getEntryTerms().forEach(et -> {
        MeshEntryTermDO entryTermDO = converter.toEntryTermDO(et, descriptorId);
        entryTermDO.setId(IdWorker.getId());
        entryTermDOs.add(entryTermDO);
      });
    }

    // 3. 批量 INSERT（单条 SQL 语句）
    descriptorMapper.insertBatch(descriptorDOs);

    if (!treeNumberDOs.isEmpty()) {
      treeNumberMapper.insertBatch(treeNumberDOs);
    }
    if (!conceptDOs.isEmpty()) {
      conceptMapper.insertBatch(conceptDOs);
    }
    if (!entryTermDOs.isEmpty()) {
      entryTermMapper.insertBatch(entryTermDOs);
    }

    log.debug(
        "写入完成：Descriptor={}, TreeNumber={}, Concept={}, EntryTerm={}",
        descriptorDOs.size(),
        treeNumberDOs.size(),
        conceptDOs.size(),
        entryTermDOs.size());
  }
}
