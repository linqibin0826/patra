package com.patra.catalog.app.usecase.meshimport.strategy;

import static com.patra.catalog.domain.model.enums.MeshDataType.TREE_NUMBER;

import com.patra.catalog.app.config.MeshImportConfig;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.enums.MeshDataType;
import com.patra.catalog.domain.port.MeshDescriptorRepository;
import com.patra.catalog.domain.port.MeshImportRepository;
import com.patra.catalog.domain.port.XmlParserPort;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// MeSH 树形编号导入策略。
///
/// 职责：
///
/// - 导入 TreeNumber（树形编号）数据
///   - 批次流式导入（数据量大，约 80000 条）
///   - 更新任务聚合根的表进度
///
/// **导入方式**：
///
/// - 流式消费 XML 解析结果
///   - 分批保存到数据库（避免内存溢出）
///   - 每批次更新进度（支持断点续传）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class TreeNumberImporter implements MeshDataImporter {

  private final XmlParserPort xmlParserPort;
  private final MeshDescriptorRepository meshDescriptorRepository;
  private final MeshImportRepository meshImportRepository;
  private final MeshImportConfig meshImportConfig;

  @Override
  public int importData(File xmlFile, MeshImportAggregate aggregate) throws Exception {
    // 1. 获取批次大小配置和预期总数
    int batchSize = meshImportConfig.getBatchSizeForTable(TREE_NUMBER.getCode());
    Integer expectedTotal = meshImportConfig.getExpectedCountForTable(TREE_NUMBER.getCode());
    log.info("[TreeNumber-Import] 开始导入 | 预期数量: {} 条 | 批次大小: {}", expectedTotal, batchSize);

    int totalProcessed = 0;
    int batchNum = 1;

    try (FileInputStream fis = new FileInputStream(xmlFile);
        Stream<MeshTreeNumber> stream = xmlParserPort.parseTreeNumbers(fis)) {

      // 2. 准备批次容器
      List<MeshTreeNumber> batch = new ArrayList<>(batchSize);

      // 3. 流式消费
      Iterator<MeshTreeNumber> iterator = stream.iterator();
      while (iterator.hasNext()) {
        batch.add(iterator.next());

        // 4. 达到批次大小或已是最后一条
        if (batch.size() >= batchSize || !iterator.hasNext()) {
          try {
            // 5. 批量保存到数据库
            int currentBatchSize = batch.size();
            meshDescriptorRepository.saveTreeNumbersBatch(batch);

            totalProcessed += currentBatchSize;
            double progress = expectedTotal != null ? (totalProcessed * 100.0 / expectedTotal) : 0;
            String progressStr = String.format("%.1f%%", progress);

            // 6. 更新进度
            aggregate.updateTableProgress(TREE_NUMBER.getCode(), totalProcessed, batchNum);
            meshImportRepository.save(aggregate);

            log.info(
                "[TreeNumber-Import] 批次 {} 保存成功 | 本批: {} 条 | 累计: {}/{} 条 ({})",
                batchNum,
                currentBatchSize,
                totalProcessed,
                expectedTotal != null ? expectedTotal : "?",
                progressStr);

            batchNum++;
            batch.clear();

          } catch (Exception e) {
            log.error(
                "[TreeNumber-Import] 批次 {} 保存失败 | 本批记录数: {} | 错误: {}",
                batchNum,
                batch.size(),
                e.getMessage(),
                e);
            throw new RuntimeException("批次保存失败：" + e.getMessage(), e);
          }
        }
      }

      // 标记表为已完成（设置实际总数）
      aggregate.markTableAsCompleted(TREE_NUMBER.getCode(), totalProcessed);
      meshImportRepository.save(aggregate);

      log.info("[TreeNumber-Import] 导入完成 | 总数量: {} 条 | 批次数: {}", totalProcessed, batchNum - 1);
      return totalProcessed;
    }
  }

  @Override
  public MeshDataType getDataType() {
    return TREE_NUMBER;
  }
}
