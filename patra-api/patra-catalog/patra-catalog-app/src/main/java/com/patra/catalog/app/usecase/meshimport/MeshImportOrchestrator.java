package com.patra.catalog.app.usecase.meshimport;

import static com.patra.catalog.domain.model.enums.MeshDataType.CONCEPT;
import static com.patra.catalog.domain.model.enums.MeshDataType.DESCRIPTOR;
import static com.patra.catalog.domain.model.enums.MeshDataType.ENTRY_TERM;
import static com.patra.catalog.domain.model.enums.MeshDataType.QUALIFIER;
import static com.patra.catalog.domain.model.enums.MeshDataType.TREE_NUMBER;

import com.patra.catalog.app.config.MeshImportConfig;
import com.patra.catalog.app.usecase.meshimport.dto.MeshImportResultDTO;
import com.patra.catalog.app.usecase.meshimport.validator.MeshDataValidator;
import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.enums.MeshDataType;
import com.patra.catalog.domain.model.enums.MeshImportTaskStatus;
import com.patra.catalog.domain.model.enums.MeshTableImportStatus;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.model.valueobject.TableProgress;
import com.patra.catalog.domain.port.MeshDescriptorRepository;
import com.patra.catalog.domain.port.MeshFileDownloadPort;
import com.patra.catalog.domain.port.MeshImportRepository;
import com.patra.catalog.domain.port.MeshQualifierRepository;
import com.patra.catalog.domain.port.XmlParserPort;
import java.io.File;
import java.io.FileInputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// MeSH 导入编排器。
///
/// 职责：
///
/// - 编排 MeSH 数据导入的完整流程
///   - 协调 Domain 层和 Infrastructure 层
///   - 管理事务边界（@Transactional）
///
/// **编排流程**（startImport 方法）：
///
/// **事务管理**：
///
/// - 主方法使用 `@Transactional`
///   - 每批次独立事务：`@Transactional(propagation = REQUIRES_NEW)`
///
/// **依赖注入**：
///
/// - {@link MeshImportRepository} - 任务仓储
///   - {@link XmlParserPort} - XML 解析器
///   - {@link MeshFileDownloadPort} - 文件下载器
///   - {@link MeshDescriptorRepository} - 主题词仓储
///   - {@link MeshDataValidator} - 数据验证器
///   - {@link MeshImportConfig} - 配置属性
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class MeshImportOrchestrator {

  /// 字节转 MB 的常量（1 MB = 1024 * 1024 字节）
  private static final long BYTES_PER_MB = 1024 * 1024;

  private final MeshImportRepository meshImportPort;
  private final XmlParserPort xmlParserPort;
  private final MeshFileDownloadPort meshFileDownloadPort;
  private final MeshDescriptorRepository meshDescriptorRepository;
  private final MeshQualifierRepository meshQualifierRepository;
  private final MeshDataValidator meshDataValidator;
  private final MeshImportConfig meshImportConfig;

  /// 开始导入 MeSH 数据。
  ///
  /// 完整流程编排：下载 → 校验 → 解析 → 批量导入 → 验证 → 完成
  ///
  /// 使用配置文件中的数据源 URL 和自动生成的任务名称执行导入。
  ///
  /// @return 导入结果
  /// @throws IllegalStateException 如果已有正在运行的任务或校验失败
  /// @throws RuntimeException 如果下载或导入过程失败
  @Transactional
  public MeshImportResultDTO startImport() {
    log.info("[MeshImport] 开始导入任务，使用配置文件默认值");

    // 1. 前置检查：确保没有正在运行的任务
    checkNoRunningTask();

    // 2. 从配置读取参数
    String descriptorSourceUrl = meshImportConfig.getDescriptorSourceUrl();
    String qualifierSourceUrl = meshImportConfig.getQualifierSourceUrl();
    String taskName = generateDefaultTaskName();
    log.info(
        "[MeshImport] 任务配置 | 任务名称: {} | Descriptor源: {} | Qualifier源: {}",
        taskName,
        descriptorSourceUrl,
        qualifierSourceUrl);

    // 3. 创建任务聚合根（PENDING 状态）
    MeshImportAggregate aggregate =
        createPendingTask(taskName, descriptorSourceUrl, qualifierSourceUrl);

    try {
      // 4. 下载 XML 文件（主题词和限定词）
      File descXmlFile = downloadXmlFile(descriptorSourceUrl, "Descriptor");
      File qualXmlFile = downloadXmlFile(qualifierSourceUrl, "Qualifier");

      // 5. 开始导入（状态变为 PROCESSING）
      aggregate.startImport();
      aggregate = meshImportPort.save(aggregate);

      // 6. 完成导入流程（导入 → 验证 → 标记完成 → 返回结果）
      return completeImportProcess(descXmlFile, qualXmlFile, aggregate);

    } catch (Exception ex) {
      log.error(
          "[MeshImport] 导入任务失败 | 任务ID: {} | 错误: {}",
          aggregate.getId().value(),
          ex.getMessage(),
          ex);
      aggregate.markAsFailed(ex.getMessage());
      meshImportPort.save(aggregate);
      throw new RuntimeException("MeSH 数据导入失败：" + ex.getMessage(), ex);
    }
  }

  /// 重试失败的任务。
  ///
  /// 重新执行失败任务的完整导入流程。
  ///
  /// 设计说明：
  ///
  /// - 不复用 startImport() 方法，因为会触发"已有任务运行"检查
  ///   - 直接从下载步骤重新开始
  ///   - 复用任务聚合根的 ID 和元数据
  ///
  /// @param taskId 任务 ID
  /// @return 导入结果
  /// @throws IllegalArgumentException 如果任务不存在
  /// @throws IllegalStateException 如果任务不是 FAILED 状态
  @Transactional
  public MeshImportResultDTO retryFailedTask(MeshImportId taskId) {
    log.info("[MeshImport] 重试失败任务 | 任务ID: {}", taskId.value());

    // 1. 查询任务
    MeshImportAggregate aggregate =
        meshImportPort
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在：" + taskId.value()));

    // 2. 检查任务状态（必须是 FAILED）
    if (aggregate.getStatus() != MeshImportTaskStatus.FAILED) {
      throw new IllegalStateException("只能重试失败的任务，当前状态：" + aggregate.getStatus().getCode());
    }

    // 3. 重试任务（状态变为 PROCESSING）
    aggregate.retry();
    aggregate = meshImportPort.save(aggregate);

    try {
      // 4. 从下载步骤重新开始（不调用 startImport，避免"已有任务运行"检查）
      //    重要：使用聚合根中保存的 URL，确保重试时使用与首次导入相同的数据源
      File descXmlFile = downloadXmlFile(aggregate.getDescriptorSourceUrl(), "Descriptor");
      File qualXmlFile = downloadXmlFile(aggregate.getQualifierSourceUrl(), "Qualifier");

      // 5. 完成导入流程（导入 → 验证 → 标记完成 → 返回结果）
      return completeImportProcess(descXmlFile, qualXmlFile, aggregate);

    } catch (Exception ex) {
      log.error(
          "[MeshImport] 重试任务失败 | 任务ID: {} | 错误: {}",
          aggregate.getId().value(),
          ex.getMessage(),
          ex);
      aggregate.markAsFailed(ex.getMessage());
      meshImportPort.save(aggregate);
      throw new RuntimeException("MeSH 数据重试导入失败：" + ex.getMessage(), ex);
    }
  }

  /// 清空进度并重新开始。
  ///
  /// 用于中断正在运行的任务并重置状态。
  @Transactional
  public void clearAndRestart() {
    log.info("[MeshImport] 清空进度并重新开始");

    // 查询正在运行的任务
    meshImportPort
        .findRunningTask()
        .ifPresent(
            aggregate -> {
              log.warn("[MeshImport] 发现正在运行的任务 | 任务ID: {} | 操作: 标记为失败", aggregate.getId().value());
              // 重置任务状态（通过创建新的聚合根，保留原任务记录）
              aggregate.markAsFailed("用户手动中断任务");
              meshImportPort.save(aggregate);
            });
  }

  // ========== 私有辅助方法 ==========

  /// 检查是否有正在运行的任务。
  ///
  /// @throws IllegalStateException 如果已有正在运行的任务
  private void checkNoRunningTask() {
    if (meshImportPort.existsRunningTask()) {
      throw new IllegalStateException("已有正在运行的 MeSH 导入任务，请等待其完成或手动中断");
    }
  }

  /// 生成默认任务名称（格式："MeSH 数据导入 - yyyy-MM-dd"）。
  ///
  /// @return 默认任务名称
  private String generateDefaultTaskName() {
    LocalDate today = LocalDate.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    return "MeSH 数据导入 - " + today.format(formatter);
  }

  /// 创建 PENDING 状态的任务聚合根。
  ///
  /// @param taskName 任务名称
  /// @param descriptorSourceUrl 主题词数据源 URL
  /// @param qualifierSourceUrl 限定词数据源 URL
  /// @return 保存后的聚合根
  private MeshImportAggregate createPendingTask(
      String taskName, String descriptorSourceUrl, String qualifierSourceUrl) {
    log.info(
        "[MeshImport] 创建导入任务 | 任务名称: {} | Descriptor源: {} | Qualifier源: {}",
        taskName,
        descriptorSourceUrl,
        qualifierSourceUrl);

    // 创建聚合根（使用全参构造函数）
    MeshImportAggregate aggregate =
        new MeshImportAggregate(
            null, // ID 由仓储生成
            taskName,
            MeshImportTaskStatus.PENDING,
            null, // startTime 在 startImport() 时设置
            null, // endTime 在 markAsCompleted() 时设置
            descriptorSourceUrl,
            qualifierSourceUrl,
            null, // xmlFileHash 在下载后设置
            null, // xmlFileSize 在下载后设置
            initializeTableProgressList(), // 初始化表进度
            0, // totalRecords
            0, // processedRecords
            0, // failedBatchCount
            null // lastErrorMessage
            );

    return meshImportPort.save(aggregate);
  }

  /// 初始化表进度列表（5 张表，初始状态为 NOT_STARTED）。
  ///
  /// 包含的表：descriptor、qualifier、tree-number、entry-term、concept
  ///
  /// **设计说明**：
  ///
  /// - 使用 {@link TableProgress#create(String, Integer)} 工厂方法创建初始进度
  ///   - `expectedCount` 用于进度估算和数据验证
  ///   - `actualTotalCount` 在导入完成后通过 {@link MeshImportAggregate#markTableAsCompleted} 设置
  ///
  /// @return 表进度列表
  private List<TableProgress> initializeTableProgressList() {
    List<String> tableNames = MeshDataType.getAllCodes();

    List<TableProgress> progressList = new ArrayList<>();
    for (String tableName : tableNames) {
      Integer expectedCount = meshImportConfig.getExpectedCountForTable(tableName);
      progressList.add(TableProgress.create(tableName, expectedCount));
    }
    return progressList;
  }

  /// 下载 XML 文件。
  ///
  /// 数据完整性验证策略：
  ///
  /// - 文件大小阈值检查（下载完成后立即验证）
  ///   - XML 结构解析验证（在 importAllData 中执行）
  ///   - 数据量阈值检查（在 MeshDataValidator 中执行）
  ///
  /// @param sourceUrl 数据源 URL
  /// @param fileType 文件类型（"Descriptor" 或 "Qualifier"）
  /// @return 下载后的文件
  /// @throws RuntimeException 如果文件大小超出阈值
  private File downloadXmlFile(String sourceUrl, String fileType) {
    log.info("[{}-Download] 开始下载文件 | URL: {}", fileType, sourceUrl);

    // 下载文件
    File xmlFile = meshFileDownloadPort.download(sourceUrl);
    long actualSize = xmlFile.length();
    log.info(
        "[{}-Download] 文件下载完成 | 大小: {} MB | 路径: {}",
        fileType,
        actualSize / BYTES_PER_MB,
        xmlFile.getAbsolutePath());

    // 验证文件大小（防止下载不完整或数据源异常）
    validateFileSize(actualSize, fileType);

    return xmlFile;
  }

  /// 验证文件大小是否在预期范围内。
  ///
  /// 防止下载不完整或数据源文件异常的情况。
  ///
  /// @param actualSize 实际文件大小（字节）
  /// @param fileType 文件类型（用于日志）
  /// @throws RuntimeException 如果文件大小超出阈值
  private void validateFileSize(long actualSize, String fileType) {
    Long expectedSize = meshImportConfig.getExpectedFileSize();
    Double threshold = meshImportConfig.getFileSizeDifferenceThreshold();

    if (expectedSize == null || threshold == null) {
      log.warn("[{}-Download] 未配置文件大小验证参数，跳过验证", fileType);
      return;
    }

    // 计算差异百分比
    double difference = Math.abs(actualSize - expectedSize) * 100.0 / expectedSize;

    if (difference > threshold) {
      String errorMsg =
          String.format(
              "[%s-Download] 文件大小异常 | 预期: %d MB | 实际: %d MB | 差异: %.1f%% (阈值: %.1f%%)",
              fileType,
              expectedSize / BYTES_PER_MB,
              actualSize / BYTES_PER_MB,
              difference,
              threshold);
      log.error(errorMsg);
      throw new RuntimeException(errorMsg);
    }

    log.info(
        "[{}-Download] 文件大小验证通过 | 预期: {} MB | 实际: {} MB | 差异: {}%",
        fileType,
        expectedSize / BYTES_PER_MB,
        actualSize / BYTES_PER_MB,
        String.format("%.1f", difference));
  }

  /// 导入所有数据（按依赖顺序）。
  ///
  /// 导入顺序：
  ///
  /// - 1. Qualifier（限定词） - 从限定词 XML
  ///   - 2. Descriptor（主题词） - 从主题词 XML
  ///   - 3. TreeNumber（树形编号） - 从主题词 XML
  ///   - 4. EntryTerm（入口术语） - 从主题词 XML
  ///   - 5. Concept（概念） - 从主题词 XML
  ///
  /// @param descXmlFile 主题词 XML 文件
  /// @param qualXmlFile 限定词 XML 文件
  /// @param aggregate 任务聚合根
  /// @return 实际导入数量映射
  private Map<String, Integer> importAllData(
      File descXmlFile, File qualXmlFile, MeshImportAggregate aggregate) throws Exception {
    log.info("[MeshImport] 开始解析并导入所有数据");
    log.info("[MeshImport] Descriptor文件: {}", descXmlFile.getAbsolutePath());
    log.info("[MeshImport] Qualifier文件: {}", qualXmlFile.getAbsolutePath());

    Map<String, Integer> importedCounts = new HashMap<>();

    // 1. 导入 Qualifier（限定词）- 必须先导入
    int qualifierCount = importQualifiers(qualXmlFile, aggregate);
    importedCounts.put(QUALIFIER.getCode(), qualifierCount);

    // 2. 导入 Descriptor（主题词）
    int descriptorCount = importDescriptors(descXmlFile, aggregate);
    importedCounts.put(DESCRIPTOR.getCode(), descriptorCount);

    // 3. 导入 TreeNumber（树形编号）
    int treeNumberCount = importTreeNumbers(descXmlFile, aggregate);
    importedCounts.put(TREE_NUMBER.getCode(), treeNumberCount);

    // 4. 导入 EntryTerm（入口术语）
    int entryTermCount = importEntryTerms(descXmlFile, aggregate);
    importedCounts.put(ENTRY_TERM.getCode(), entryTermCount);

    // 5. 导入 Concept（概念）
    int conceptCount = importConcepts(descXmlFile, aggregate);
    importedCounts.put(CONCEPT.getCode(), conceptCount);

    int totalRecords = importedCounts.values().stream().mapToInt(Integer::intValue).sum();
    log.info(
        "[MeshImport] 所有数据导入完成 | 总计: {} 条 | Descriptor: {} | Qualifier: {} | TreeNumber: {} | EntryTerm: {} | Concept: {}",
        totalRecords,
        importedCounts.get(DESCRIPTOR.getCode()),
        importedCounts.get(QUALIFIER.getCode()),
        importedCounts.get(TREE_NUMBER.getCode()),
        importedCounts.get(ENTRY_TERM.getCode()),
        importedCounts.get(CONCEPT.getCode()));

    return importedCounts;
  }

  /// 导入 Qualifier（限定词）- 一次性导入，无需分批。
  ///
  /// 设计说明：
  ///
  /// - Qualifier 数据量极小（约 80 条），一次性导入即可
  ///   - 无需流式处理和分批保存
  ///   - 简化逻辑，提高性能
  ///   - 内存占用可忽略（约几 KB）
  ///
  /// @param xmlFile 限定词 XML 文件
  /// @param aggregate 导入任务聚合根（用于更新进度）
  /// @return 实际导入数量
  private int importQualifiers(File xmlFile, MeshImportAggregate aggregate) throws Exception {
    Integer expectedCount = meshImportConfig.getExpectedCountForTable(QUALIFIER.getCode());
    log.info("[Qualifier-Import] 开始导入（一次性批量）| 预期数量: {} 条", expectedCount);

    try (FileInputStream fis = new FileInputStream(xmlFile);
        Stream<MeshQualifierAggregate> stream = xmlParserPort.parseQualifiers(fis)) {

      // 一次性收集所有记录（约 80 条，内存占用极小）
      List<MeshQualifierAggregate> qualifiers = stream.toList();

      // 一次性批量保存
      meshQualifierRepository.saveBatch(qualifiers);

      int totalCount = qualifiers.size();

      // 标记表为已完成（设置实际总数）
      aggregate.markTableAsCompleted(QUALIFIER.getCode(), totalCount);
      meshImportPort.save(aggregate);

      log.info("[Qualifier-Import] 导入完成 | 实际数量: {} 条", totalCount);
      return totalCount;
    }
  }

  /// 导入 Descriptor（主题词）。
  ///
  /// @param xmlFile XML 文件
  /// @param aggregate 导入任务聚合根（用于更新进度）
  /// @return 实际导入数量
  private int importDescriptors(File xmlFile, MeshImportAggregate aggregate) throws Exception {
    // 1. 获取批次大小配置和预期总数
    int batchSize = meshImportConfig.getBatchSizeForTable(DESCRIPTOR.getCode());
    Integer expectedTotal = meshImportConfig.getExpectedCountForTable(DESCRIPTOR.getCode());
    log.info("[Descriptor-Import] 开始导入 | 预期数量: {} 条 | 批次大小: {}", expectedTotal, batchSize);

    int totalProcessed = 0;
    int batchNum = 1;

    try (FileInputStream fis = new FileInputStream(xmlFile);
        Stream<MeshDescriptorAggregate> stream = xmlParserPort.parseDescriptors(fis)) {

      // 2. 准备批次容器
      List<MeshDescriptorAggregate> batch = new ArrayList<>(batchSize);

      // 3. 流式消费
      Iterator<MeshDescriptorAggregate> iterator = stream.iterator();
      while (iterator.hasNext()) {
        batch.add(iterator.next());

        // 4. 达到批次大小或已是最后一条
        if (batch.size() >= batchSize || !iterator.hasNext()) {
          try {
            // 5. 批量保存到数据库
            int currentBatchSize = batch.size();
            meshDescriptorRepository.saveBatch(batch);

            totalProcessed += currentBatchSize;
            double progress = expectedTotal != null ? (totalProcessed * 100.0 / expectedTotal) : 0;
            String progressStr = String.format("%.1f%%", progress);

            // 6. 更新进度
            aggregate.updateTableProgress(DESCRIPTOR.getCode(), totalProcessed, batchNum);
            meshImportPort.save(aggregate);

            log.info(
                "[Descriptor-Import] 批次 {} 保存成功 | 本批: {} 条 | 累计: {}/{} 条 ({})",
                batchNum,
                currentBatchSize,
                totalProcessed,
                expectedTotal != null ? expectedTotal : "?",
                progressStr);

            batchNum++;
            batch.clear();

          } catch (Exception e) {
            log.error(
                "[Descriptor-Import] 批次 {} 保存失败 | 本批记录数: {} | 错误: {}",
                batchNum,
                batch.size(),
                e.getMessage(),
                e);
            throw new RuntimeException("批次保存失败：" + e.getMessage(), e);
          }
        }
      }

      // 标记表为已完成（设置实际总数）
      aggregate.markTableAsCompleted(DESCRIPTOR.getCode(), totalProcessed);
      meshImportPort.save(aggregate);

      log.info("[Descriptor-Import] 导入完成 | 总数量: {} 条 | 批次数: {}", totalProcessed, batchNum - 1);
      return totalProcessed;
    }
  }

  /// 导入 TreeNumber（树形编号）。
  ///
  /// @param xmlFile XML 文件
  /// @param aggregate 导入任务聚合根（用于更新进度）
  /// @return 实际导入数量
  private int importTreeNumbers(File xmlFile, MeshImportAggregate aggregate) throws Exception {
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
            meshImportPort.save(aggregate);

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
      meshImportPort.save(aggregate);

      log.info("[TreeNumber-Import] 导入完成 | 总数量: {} 条 | 批次数: {}", totalProcessed, batchNum - 1);
      return totalProcessed;
    }
  }

  /// 导入 EntryTerm（入口术语）。
  ///
  /// @param xmlFile XML 文件
  /// @param aggregate 导入任务聚合根（用于更新进度）
  /// @return 实际导入数量
  private int importEntryTerms(File xmlFile, MeshImportAggregate aggregate) throws Exception {
    // 1. 获取批次大小配置和预期总数
    int batchSize = meshImportConfig.getBatchSizeForTable(ENTRY_TERM.getCode());
    Integer expectedTotal = meshImportConfig.getExpectedCountForTable(ENTRY_TERM.getCode());
    log.info("[EntryTerm-Import] 开始导入 | 预期数量: {} 条 | 批次大小: {}", expectedTotal, batchSize);

    int totalProcessed = 0;
    int batchNum = 1;

    try (FileInputStream fis = new FileInputStream(xmlFile);
        Stream<MeshEntryTerm> stream = xmlParserPort.parseEntryTerms(fis)) {

      // 2. 准备批次容器
      List<MeshEntryTerm> batch = new ArrayList<>(batchSize);

      // 3. 流式消费
      Iterator<MeshEntryTerm> iterator = stream.iterator();
      while (iterator.hasNext()) {
        batch.add(iterator.next());

        // 4. 达到批次大小或已是最后一条
        if (batch.size() >= batchSize || !iterator.hasNext()) {
          try {
            // 5. 批量保存到数据库
            int currentBatchSize = batch.size();
            meshDescriptorRepository.saveEntryTermsBatch(batch);

            totalProcessed += currentBatchSize;
            double progress = expectedTotal != null ? (totalProcessed * 100.0 / expectedTotal) : 0;
            String progressStr = String.format("%.1f%%", progress);

            // 6. 更新进度
            aggregate.updateTableProgress(ENTRY_TERM.getCode(), totalProcessed, batchNum);
            meshImportPort.save(aggregate);

            log.info(
                "[EntryTerm-Import] 批次 {} 保存成功 | 本批: {} 条 | 累计: {}/{} 条 ({})",
                batchNum,
                currentBatchSize,
                totalProcessed,
                expectedTotal != null ? expectedTotal : "?",
                progressStr);

            batchNum++;
            batch.clear();

          } catch (Exception e) {
            log.error(
                "[EntryTerm-Import] 批次 {} 保存失败 | 本批记录数: {} | 错误: {}",
                batchNum,
                batch.size(),
                e.getMessage(),
                e);
            throw new RuntimeException("批次保存失败：" + e.getMessage(), e);
          }
        }
      }

      // 标记表为已完成（设置实际总数）
      aggregate.markTableAsCompleted(ENTRY_TERM.getCode(), totalProcessed);
      meshImportPort.save(aggregate);

      log.info("[EntryTerm-Import] 导入完成 | 总数量: {} 条 | 批次数: {}", totalProcessed, batchNum - 1);
      return totalProcessed;
    }
  }

  /// 导入 Concept（概念）。
  ///
  /// @param xmlFile XML 文件
  /// @param aggregate 导入任务聚合根（用于更新进度）
  /// @return 实际导入数量
  private int importConcepts(File xmlFile, MeshImportAggregate aggregate) throws Exception {
    // 1. 获取批次大小配置和预期总数
    int batchSize = meshImportConfig.getBatchSizeForTable(CONCEPT.getCode());
    Integer expectedTotal = meshImportConfig.getExpectedCountForTable(CONCEPT.getCode());
    log.info("[Concept-Import] 开始导入 | 预期数量: {} 条 | 批次大小: {}", expectedTotal, batchSize);

    int totalProcessed = 0;
    int batchNum = 1;

    try (FileInputStream fis = new FileInputStream(xmlFile);
        Stream<MeshConcept> stream = xmlParserPort.parseConcepts(fis)) {

      // 2. 准备批次容器
      List<MeshConcept> batch = new ArrayList<>(batchSize);

      // 3. 流式消费
      Iterator<MeshConcept> iterator = stream.iterator();
      while (iterator.hasNext()) {
        batch.add(iterator.next());

        // 4. 达到批次大小或已是最后一条
        if (batch.size() >= batchSize || !iterator.hasNext()) {
          try {
            // 5. 批量保存到数据库
            int currentBatchSize = batch.size();
            meshDescriptorRepository.saveConceptsBatch(batch);

            totalProcessed += currentBatchSize;
            double progress = expectedTotal != null ? (totalProcessed * 100.0 / expectedTotal) : 0;
            String progressStr = String.format("%.1f%%", progress);

            // 6. 更新进度
            aggregate.updateTableProgress(CONCEPT.getCode(), totalProcessed, batchNum);
            meshImportPort.save(aggregate);

            log.info(
                "[Concept-Import] 批次 {} 保存成功 | 本批: {} 条 | 累计: {}/{} 条 ({})",
                batchNum,
                currentBatchSize,
                totalProcessed,
                expectedTotal != null ? expectedTotal : "?",
                progressStr);

            batchNum++;
            batch.clear();

          } catch (Exception e) {
            log.error(
                "[Concept-Import] 批次 {} 保存失败 | 本批记录数: {} | 错误: {}",
                batchNum,
                batch.size(),
                e.getMessage(),
                e);
            throw new RuntimeException("批次保存失败：" + e.getMessage(), e);
          }
        }
      }

      // 标记表为已完成（设置实际总数）
      aggregate.markTableAsCompleted(CONCEPT.getCode(), totalProcessed);
      meshImportPort.save(aggregate);

      log.info("[Concept-Import] 导入完成 | 总数量: {} 条 | 批次数: {}", totalProcessed, batchNum - 1);
      return totalProcessed;
    }
  }

  /// 验证数据量。
  ///
  /// @param importedCounts 实际导入数量
  /// @param aggregate 任务聚合根
  private void validateDataCounts(
      Map<String, Integer> importedCounts, MeshImportAggregate aggregate) {
    log.info("[MeshImport] 开始验证数据量 | 实际导入: {}", importedCounts);

    MeshDataValidator.ValidationResult result =
        meshDataValidator.validateDataCounts(importedCounts);

    if (result.hasWarnings()) {
      log.warn("[MeshImport] 数据量验证发现 {} 个警告", result.warningCount());
      result.warnings().forEach(warning -> log.warn("[MeshImport] 警告: {}", warning));
      // 注意：有警告不阻塞任务完成，只记录警告信息
    } else {
      log.info("[MeshImport] 数据量验证通过，所有表数据量在预期范围内");
    }
  }

  /// 完成导入流程（导入数据 → 验证 → 标记完成 → 返回结果）。
  ///
  /// 此方法封装了导入流程的核心步骤，被 {@link #startImport()} 和 {@link #retryFailedTask(MeshImportId)} 复用。
  ///
  /// @param descXmlFile 主题词 XML 文件
  /// @param qualXmlFile 限定词 XML 文件
  /// @param aggregate 任务聚合根
  /// @return 导入结果 DTO
  /// @throws Exception 如果导入过程失败
  private MeshImportResultDTO completeImportProcess(
      File descXmlFile, File qualXmlFile, MeshImportAggregate aggregate) throws Exception {
    // 1. 解析并批量导入数据（先 qualifier，再 descriptor 及其子表）
    Map<String, Integer> importedCounts = importAllData(descXmlFile, qualXmlFile, aggregate);

    // 2. 验证数据量
    validateDataCounts(importedCounts, aggregate);

    // 3. 标记任务完成
    aggregate.markAsCompleted();
    aggregate = meshImportPort.save(aggregate);

    // 4. 记录完成日志
    long duration = Duration.between(aggregate.getStartTime(), aggregate.getEndTime()).toMillis();
    log.info(
        "[MeshImport] 导入任务成功完成 | 任务ID: {} | 耗时: {}ms | Descriptor: {} 条 | Qualifier: {} 条 | TreeNumber: {} 条 | EntryTerm: {} 条 | Concept: {} 条",
        aggregate.getId().value(),
        duration,
        importedCounts.get(DESCRIPTOR.getCode()),
        importedCounts.get(QUALIFIER.getCode()),
        importedCounts.get(TREE_NUMBER.getCode()),
        importedCounts.get(ENTRY_TERM.getCode()),
        importedCounts.get(CONCEPT.getCode()));

    // 5. 返回结果
    return buildSuccessResult(aggregate);
  }

  /// 构建成功结果。
  ///
  /// @param aggregate 任务聚合根
  /// @return 导入结果 DTO
  private MeshImportResultDTO buildSuccessResult(MeshImportAggregate aggregate) {
    return MeshImportResultDTO.builder()
        .taskId(aggregate.getId().value().toString())
        .taskName(aggregate.getTaskName())
        .status(aggregate.getStatus().getCode())
        .startTime(aggregate.getStartTime())
        .message("任务已完成，成功导入 " + aggregate.getProcessedRecords() + " 条记录")
        .build();
  }
}
