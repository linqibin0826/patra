package com.patra.catalog.app.usecase.meshimport.strategy;

import com.patra.catalog.app.config.MeshImportConfig;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.port.MeshImportRepository;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// MeSH 数据批量导入抽象基类（模板方法模式）。
///
/// **设计目的**：
///
/// - 消除代码重复：4 个策略类（Descriptor、Concept、TreeNumber、EntryTerm）的导入逻辑 95% 相同
///   - 使用模板方法模式提取公共流程
///   - 子类只需实现差异部分（解析方法、保存方法、日志标签）
///
/// **核心流程**（模板方法 `importData`）：
///
/// 1. 获取批次大小和预期总数
/// 2. 流式消费 XML 解析结果
/// 3. 分批保存到数据库
/// 4. 每批次更新进度（**独立事务，REQUIRES_NEW**）
/// 5. 标记表为已完成
///
/// **事务管理**：
///
/// - 批次保存方法 `saveBatchWithProgress` 使用 `@Transactional(propagation = REQUIRES_NEW)`
///   - 每个批次独立事务，失败不影响其他批次
///   - 避免大事务导致的锁等待和回滚成本
///   - 支持断点续传（已完成批次不会回滚）
///
/// **子类职责**：
///
/// - 实现 `parseStream(FileInputStream)` - 解析 XML 文件为领域对象流
/// - 实现 `saveBatch(List<T>)` - 批量保存领域对象
/// - 实现 `getLogTag()` - 返回日志标签（如 "[Descriptor-Import]"）
/// - 实现 `getDataType()` - 返回数据类型枚举
///
/// **使用示例**：
///
/// ```java
/// @Component
/// public class DescriptorImporter extends AbstractBatchImporter<MeshDescriptorAggregate> {
///
///     private final XmlParserPort xmlParserPort;
///     private final MeshDescriptorRepository meshDescriptorRepository;
///
///     @Override
///     protected Stream<MeshDescriptorAggregate> parseStream(FileInputStream fis) {
///         return xmlParserPort.parseDescriptors(fis);
///     }
///
///     @Override
///     protected void saveBatch(List<MeshDescriptorAggregate> batch) {
///         meshDescriptorRepository.saveBatch(batch);
///     }
///
///     @Override
///     protected String getLogTag() {
///         return "[Descriptor-Import]";
///     }
///
///     @Override
///     public MeshDataType getDataType() {
///         return DESCRIPTOR;
///     }
/// }
/// ```
///
/// @param <T> 领域对象类型（如 MeshDescriptorAggregate、MeshConcept 等）
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractBatchImporter<T> implements MeshDataImporter {

  protected final MeshImportRepository meshImportRepository;
  protected final MeshImportConfig meshImportConfig;

  /// 导入数据（模板方法）。
  ///
  /// 流程：
  ///
  /// 1. 获取批次大小和预期总数
  /// 2. 流式消费 XML 解析结果
  /// 3. 分批保存（每批次独立事务）
  /// 4. 更新进度
  /// 5. 标记表为已完成
  ///
  /// @param xmlFile XML 文件
  /// @param aggregate 导入任务聚合根
  /// @return 实际导入的记录数
  /// @throws Exception 导入失败时抛出异常
  @Override
  public final int importData(File xmlFile, MeshImportAggregate aggregate) throws Exception {
    // 1. 获取批次大小配置和预期总数
    int batchSize = getBatchSize();
    Integer expectedTotal = getExpectedTotal();
    log.info("{} 开始导入 | 预期数量: {} 条 | 批次大小: {}", getLogTag(), expectedTotal, batchSize);

    int totalProcessed = 0;
    int batchNum = 1;

    try (FileInputStream fis = new FileInputStream(xmlFile);
        Stream<T> stream = parseStream(fis)) {

      // 2. 准备批次容器
      List<T> batch = new ArrayList<>(batchSize);

      // 3. 流式消费
      Iterator<T> iterator = stream.iterator();
      while (iterator.hasNext()) {
        batch.add(iterator.next());

        // 4. 达到批次大小或已是最后一条
        if (batch.size() >= batchSize || !iterator.hasNext()) {
          // 5. 批量保存（独立事务）
          int processedInBatch = saveBatchWithProgress(batch, aggregate, batchNum, totalProcessed);
          totalProcessed += processedInBatch;

          // 6. 记录成功日志
          logBatchSuccess(batchNum, processedInBatch, totalProcessed, expectedTotal);

          batchNum++;
          batch.clear();
        }
      }

      // 7. 标记表为已完成（设置实际总数）
      aggregate.markTableAsCompleted(getDataType().getCode(), totalProcessed);
      meshImportRepository.save(aggregate);

      log.info("{} 导入完成 | 总数量: {} 条 | 批次数: {}", getLogTag(), totalProcessed, batchNum - 1);
      return totalProcessed;
    }
  }

  /// 批量保存并更新进度（独立事务）。
  ///
  /// **事务管理**：
  ///
  /// - 使用 `REQUIRES_NEW` 传播级别，每个批次独立事务
  ///   - 失败不影响其他批次
  ///   - 支持断点续传
  ///   - 避免大事务导致的锁等待
  ///
  /// @param batch 当前批次的领域对象列表
  /// @param aggregate 导入任务聚合根
  /// @param batchNum 批次号（从 1 开始）
  /// @param totalProcessedBefore 本批次之前已处理的总数
  /// @return 本批次成功保存的记录数
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  protected int saveBatchWithProgress(
      List<T> batch, MeshImportAggregate aggregate, int batchNum, int totalProcessedBefore) {
    try {
      int currentBatchSize = batch.size();

      // 1. 批量保存到数据库（子类实现）
      saveBatch(batch);

      // 2. 更新进度
      int newTotalProcessed = totalProcessedBefore + currentBatchSize;
      aggregate.updateTableProgress(getDataType().getCode(), newTotalProcessed, batchNum);
      meshImportRepository.save(aggregate);

      return currentBatchSize;

    } catch (Exception e) {
      log.error(
          "{} 批次 {} 保存失败 | 本批记录数: {} | 错误: {}",
          getLogTag(),
          batchNum,
          batch.size(),
          e.getMessage(),
          e);
      throw new RuntimeException("批次保存失败：" + e.getMessage(), e);
    }
  }

  /// 记录批次成功日志。
  ///
  /// @param batchNum 批次号
  /// @param currentBatchSize 本批次处理的记录数
  /// @param totalProcessed 累计处理的总记录数
  /// @param expectedTotal 预期总数
  protected void logBatchSuccess(
      int batchNum, int currentBatchSize, int totalProcessed, Integer expectedTotal) {
    double progress = expectedTotal != null ? (totalProcessed * 100.0 / expectedTotal) : 0;
    String progressStr = String.format("%.1f%%", progress);

    log.info(
        "{} 批次 {} 保存成功 | 本批: {} 条 | 累计: {}/{} 条 ({})",
        getLogTag(),
        batchNum,
        currentBatchSize,
        totalProcessed,
        expectedTotal != null ? expectedTotal : "?",
        progressStr);
  }

  /// 获取批次大小。
  ///
  /// @return 批次大小
  protected int getBatchSize() {
    return meshImportConfig.getBatchSizeForTable(getDataType().getCode());
  }

  /// 获取预期总数。
  ///
  /// @return 预期总数（可能为 null）
  protected Integer getExpectedTotal() {
    return meshImportConfig.getExpectedCountForTable(getDataType().getCode());
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 抽象方法 - 子类必须实现
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  /// 解析 XML 文件为领域对象流。
  ///
  /// @param fis XML 文件输入流
  /// @return 领域对象流
  /// @throws Exception 解析失败时抛出异常
  protected abstract Stream<T> parseStream(FileInputStream fis) throws Exception;

  /// 批量保存领域对象到数据库。
  ///
  /// @param batch 领域对象列表
  protected abstract void saveBatch(List<T> batch);

  /// 获取日志标签（用于区分不同的导入策略）。
  ///
  /// @return 日志标签（如 "[Descriptor-Import]"）
  protected abstract String getLogTag();
}
