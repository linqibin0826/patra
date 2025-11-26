package com.patra.catalog.infra.batch;

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
/// 1. 批量 INSERT Descriptor 主表（获取自动生成的 ID）
/// 2. 批量 INSERT TreeNumber 子表
/// 3. 批量 INSERT Concept 子表
/// 4. 批量 INSERT EntryTerm 子表
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

    // 1. 转换并批量 INSERT Descriptor 主表
    List<MeshDescriptorDO> descriptorDOs = new ArrayList<>();
    for (MeshDescriptorAggregate aggregate : items) {
      MeshDescriptorDO dataObject = converter.toDescriptorDO(aggregate);
      descriptorMapper.insert(dataObject);
      descriptorDOs.add(dataObject);
    }

    // 2. 收集并批量 INSERT 子表
    List<MeshTreeNumberDO> treeNumberDOs = new ArrayList<>();
    List<MeshConceptDO> conceptDOs = new ArrayList<>();
    List<MeshEntryTermDO> entryTermDOs = new ArrayList<>();

    for (int i = 0; i < items.size(); i++) {
      MeshDescriptorAggregate aggregate = items.get(i);
      Long descriptorId = descriptorDOs.get(i).getId();

      // TreeNumber
      aggregate
          .getTreeNumbers()
          .forEach(tn -> treeNumberDOs.add(converter.toTreeNumberDO(tn, descriptorId)));

      // Concept
      aggregate
          .getConcepts()
          .forEach(c -> conceptDOs.add(converter.toConceptDO(c, descriptorId)));

      // EntryTerm
      aggregate
          .getEntryTerms()
          .forEach(et -> entryTermDOs.add(converter.toEntryTermDO(et, descriptorId)));
    }

    // 批量 INSERT 子表
    treeNumberDOs.forEach(treeNumberMapper::insert);
    conceptDOs.forEach(conceptMapper::insert);
    entryTermDOs.forEach(entryTermMapper::insert);

    log.debug(
        "写入完成：Descriptor={}, TreeNumber={}, Concept={}, EntryTerm={}",
        descriptorDOs.size(),
        treeNumberDOs.size(),
        conceptDOs.size(),
        entryTermDOs.size());
  }
}
