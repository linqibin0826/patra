package com.patra.catalog.app.usecase.meshimport;

import com.patra.catalog.app.config.MeshImportConfig;
import com.patra.catalog.app.usecase.meshimport.dto.MeshImportResultDTO;
import com.patra.catalog.app.usecase.meshimport.validator.MeshDataValidator;
import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
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

  private final MeshImportRepository meshImportPort;
  private final XmlParserPort xmlParserPort;
  private final MeshFileDownloadPort meshFileDownloadPort;
  private final MeshDescriptorRepository meshDescriptorPort;
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
    log.info("开始 MeSH 数据导入流程，使用配置文件默认值");

    // 1. 前置检查：确保没有正在运行的任务
    checkNoRunningTask();

    // 2. 从配置读取参数
    String descriptorSourceUrl = meshImportConfig.getDescriptorSourceUrl();
    String qualifierSourceUrl = meshImportConfig.getQualifierSourceUrl();
    String taskName = generateDefaultTaskName();
    log.debug("主题词数据源 URL：{}，限定词数据源 URL：{}，任务名称：{}", descriptorSourceUrl, qualifierSourceUrl, taskName);

    // 3. 创建任务聚合根（PENDING 状态）
    MeshImportAggregate aggregate = createPendingTask(taskName, descriptorSourceUrl, qualifierSourceUrl);

    try {
      // 4. 下载 XML 文件（主题词和限定词）
      File descXmlFile = downloadXmlFile(descriptorSourceUrl, aggregate);
      File qualXmlFile =
          downloadXmlFile(meshImportConfig.getQualifierSourceUrl(), aggregate);

      // 5. 开始导入（状态变为 PROCESSING）
      aggregate.startImport();
      aggregate = meshImportPort.save(aggregate);

      // 6. 解析并批量导入数据（先 qualifier，再 descriptor 及其子表）
      Map<String, Integer> importedCounts = importAllData(descXmlFile, qualXmlFile, aggregate);

      // 7. 验证数据量
      validateDataCounts(importedCounts, aggregate);

      // 8. 标记任务完成
      aggregate.markAsCompleted();
      aggregate = meshImportPort.save(aggregate);

      log.info("MeSH 数据导入成功完成，任务 ID：{}", aggregate.getId().value());

      return buildSuccessResult(aggregate);

    } catch (Exception ex) {
      log.error("MeSH 数据导入失败，任务 ID：{}", aggregate.getId().value(), ex);
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
    log.info("重试失败的 MeSH 导入任务，任务 ID：{}", taskId.value());

    // 1. 查询任务
    MeshImportAggregate aggregate =
        meshImportPort
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在：" + taskId.value()));

    // 2. 检查任务状态（必须是 FAILED）
    if (aggregate.getStatus() != MeshImportTaskStatus.FAILED) {
      throw new IllegalStateException(
          "只能重试失败的任务，当前状态：" + aggregate.getStatus().getCode());
    }

    // 3. 重试任务（状态变为 PROCESSING）
    aggregate.retry();
    aggregate = meshImportPort.save(aggregate);

    try {
      // 4. 从下载步骤重新开始（不调用 startImport，避免"已有任务运行"检查）
      File descXmlFile = downloadXmlFile(aggregate.getDescriptorSourceUrl(), aggregate);
      File qualXmlFile =
          downloadXmlFile(meshImportConfig.getQualifierSourceUrl(), aggregate);

      // 5. 解析并批量导入数据（先 qualifier，再 descriptor 及其子表）
      Map<String, Integer> importedCounts = importAllData(descXmlFile, qualXmlFile, aggregate);

      // 6. 验证数据量
      validateDataCounts(importedCounts, aggregate);

      // 7. 标记任务完成
      aggregate.markAsCompleted();
      aggregate = meshImportPort.save(aggregate);

      log.info("MeSH 数据重试导入成功，任务 ID：{}", aggregate.getId().value());

      return buildSuccessResult(aggregate);

    } catch (Exception ex) {
      log.error("MeSH 数据重试导入失败，任务 ID：{}", aggregate.getId().value(), ex);
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
    log.info("清空 MeSH 导入进度并重新开始");

    // 查询正在运行的任务
    meshImportPort
        .findRunningTask()
        .ifPresent(
            aggregate -> {
              log.warn("发现正在运行的任务，任务 ID：{}，重置为 PENDING 状态", aggregate.getId().value());
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
  private MeshImportAggregate createPendingTask(String taskName, String descriptorSourceUrl, String qualifierSourceUrl) {
    log.info("创建 MeSH 导入任务，名称：{}, 主题词数据源：{}, 限定词数据源：{}", taskName, descriptorSourceUrl, qualifierSourceUrl);

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
  /// @return 表进度列表
  private List<TableProgress> initializeTableProgressList() {
    List<String> tableNames =
        List.of("descriptor", "qualifier", "tree-number", "entry-term", "concept");

    List<TableProgress> progressList = new ArrayList<>();
    for (String tableName : tableNames) {
      Integer expectedCount = meshImportConfig.getExpectedCountForTable(tableName);
      progressList.add(
          TableProgress.builder()
              .tableName(tableName)
              .totalCount(expectedCount)
              .processedCount(0)
              .failedCount(0)
              .status(MeshTableImportStatus.NOT_STARTED)
              .lastBatchNum(0)
              .lastUpdateTime(Instant.now())
              .build());
    }
    return progressList;
  }

  /// 下载 XML 文件并验证文件完整性。
  ///
  /// 验证策略（增强验证方案）：
  ///
  /// - 第一道防线：文件大小验证（允许 ±10% 波动）
  ///   - 第二道防线：XML 结构解析验证（在后续 importAllData 中执行）
  ///   - 第三道防线：数据量阈值检查（在 MeshDataValidator 中执行）
  ///
  /// **设计说明**：由于 NLM 官方不提供 MeSH XML 文件的 MD5 校验和，我们采用多重验证策略来确保文件完整性。
  /// 这种方案比单一 MD5 验证更全面，能够检测文件损坏、下载不完整等问题。
  ///
  /// @param sourceUrl 数据源 URL
  /// @param aggregate 任务聚合根
  /// @return 下载后的文件
  /// @throws IllegalStateException 如果文件大小验证失败
  private File downloadXmlFile(String sourceUrl, MeshImportAggregate aggregate) {
    log.info("开始下载 MeSH XML 文件，URL：{}", sourceUrl);

    // 下载文件
    File xmlFile = meshFileDownloadPort.download(sourceUrl);
    long actualSize = xmlFile.length();
    log.info(
        "XML 文件下载完成，大小：{} 字节 ({} MB)，路径：{}",
        actualSize,
        actualSize / (1024 * 1024),
        xmlFile.getAbsolutePath());

    // 验证文件大小（第一道防线）
    long expectedSize = meshImportConfig.getExpectedFileSize();
    double threshold = meshImportConfig.getFileSizeDifferenceThreshold();
    double difference = Math.abs(actualSize - expectedSize) * 100.0 / expectedSize;

    if (difference > threshold) {
      String message =
          String.format(
              "XML 文件大小异常，预期：%d 字节 (%d MB)，实际：%d 字节 (%d MB)，差异：%.2f%% (阈值：%.1f%%)",
              expectedSize,
              expectedSize / (1024 * 1024),
              actualSize,
              actualSize / (1024 * 1024),
              difference,
              threshold);
      log.error(message);
      throw new IllegalStateException(message);
    }

    log.info("文件大小验证通过，差异：{:.2f}% (阈值：{:.1f}%)", difference, threshold);
    return xmlFile;
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
    log.info("开始解析并导入 MeSH 数据");
    log.info("  - 主题词文件：{}", descXmlFile.getAbsolutePath());
    log.info("  - 限定词文件：{}", qualXmlFile.getAbsolutePath());

    Map<String, Integer> importedCounts = new HashMap<>();

    // 1. 导入 Qualifier（限定词）- 必须先导入
    int qualifierCount = importQualifiers(qualXmlFile, aggregate);
    importedCounts.put("qualifier", qualifierCount);

    // 2. 导入 Descriptor（主题词）
    int descriptorCount = importDescriptors(descXmlFile, aggregate);
    importedCounts.put("descriptor", descriptorCount);

    // 3. 导入 TreeNumber（树形编号）
    int treeNumberCount = importTreeNumbers(descXmlFile, aggregate);
    importedCounts.put("tree-number", treeNumberCount);

    // 4. 导入 EntryTerm（入口术语）
    int entryTermCount = importEntryTerms(descXmlFile, aggregate);
    importedCounts.put("entry-term", entryTermCount);

    // 5. 导入 Concept（概念）
    int conceptCount = importConcepts(descXmlFile, aggregate);
    importedCounts.put("concept", conceptCount);

    log.info(
        "所有数据导入完成，总计：{} 条记录", importedCounts.values().stream().mapToInt(Integer::intValue).sum());

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
    log.info("开始导入 Qualifier（限定词）");

    try (FileInputStream fis = new FileInputStream(xmlFile);
        Stream<MeshQualifierAggregate> stream = xmlParserPort.parseQualifiers(fis)) {

      // 一次性收集所有记录（约 80 条，内存占用极小）
      List<MeshQualifierAggregate> qualifiers = stream.toList();

      // 一次性批量保存
      meshQualifierRepository.saveBatch(qualifiers);

      int totalCount = qualifiers.size();

      // 更新进度（标记为完成）
      aggregate.updateTableProgress("qualifier", totalCount, 1);
      meshImportPort.save(aggregate);

      log.info("Qualifier 导入完成，总数量：{}", totalCount);
      return totalCount;
    }
  }

  /// 导入 Descriptor（主题词）。
  ///
  /// @param xmlFile XML 文件
  /// @param aggregate 导入任务聚合根（用于更新进度）
  /// @return 实际导入数量
  private int importDescriptors(File xmlFile, MeshImportAggregate aggregate) throws Exception {
    log.info("开始导入 Descriptor（主题词）");

    // 1. 获取批次大小配置
    int batchSize = meshImportConfig.getBatchSizeForTable("descriptor");
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
            meshDescriptorPort.saveBatch(batch);

            totalProcessed += batch.size();

            // 6. 更新进度
            aggregate.updateTableProgress("descriptor", totalProcessed, batchNum);
            meshImportPort.save(aggregate);

            log.info("批次 {} 保存成功，已处理 {}", batchNum, totalProcessed);

            batchNum++;
            batch.clear();

          } catch (Exception e) {
            log.error("批次 {} 保存失败，表：descriptor", batchNum, e);
            throw new RuntimeException("批次保存失败：" + e.getMessage(), e);
          }
        }
      }

      log.info("Descriptor 导入完成，总数量：{}", totalProcessed);
      return totalProcessed;
    }
  }

  /// 导入 TreeNumber（树形编号）。
  ///
  /// @param xmlFile XML 文件
  /// @param aggregate 导入任务聚合根（用于更新进度）
  /// @return 实际导入数量
  private int importTreeNumbers(File xmlFile, MeshImportAggregate aggregate) throws Exception {
    log.info("开始导入 TreeNumber（树形编号）");

    // 1. 获取批次大小配置
    int batchSize = meshImportConfig.getBatchSizeForTable("tree-number");
    int totalProcessed = 0;
    int batchNum = 1;

    try (FileInputStream fis = new FileInputStream(xmlFile);
        Stream<com.patra.catalog.domain.model.entity.MeshTreeNumber> stream =
            xmlParserPort.parseTreeNumbers(fis)) {

      // 2. 准备批次容器
      List<com.patra.catalog.domain.model.entity.MeshTreeNumber> batch =
          new ArrayList<>(batchSize);

      // 3. 流式消费
      Iterator<com.patra.catalog.domain.model.entity.MeshTreeNumber> iterator =
          stream.iterator();
      while (iterator.hasNext()) {
        batch.add(iterator.next());

        // 4. 达到批次大小或已是最后一条
        if (batch.size() >= batchSize || !iterator.hasNext()) {
          try {
            // 5. 批量保存到数据库
            meshDescriptorPort.saveTreeNumbersBatch(batch);

            totalProcessed += batch.size();

            // 6. 更新进度
            aggregate.updateTableProgress("tree-number", totalProcessed, batchNum);
            meshImportPort.save(aggregate);

            log.info("批次 {} 保存成功，已处理 {}", batchNum, totalProcessed);

            batchNum++;
            batch.clear();

          } catch (Exception e) {
            log.error("批次 {} 保存失败，表：tree-number", batchNum, e);
            throw new RuntimeException("批次保存失败：" + e.getMessage(), e);
          }
        }
      }

      log.info("TreeNumber 导入完成，总数量：{}", totalProcessed);
      return totalProcessed;
    }
  }

  /// 导入 EntryTerm（入口术语）。
  ///
  /// @param xmlFile XML 文件
  /// @param aggregate 导入任务聚合根（用于更新进度）
  /// @return 实际导入数量
  private int importEntryTerms(File xmlFile, MeshImportAggregate aggregate) throws Exception {
    log.info("开始导入 EntryTerm（入口术语）");

    // 1. 获取批次大小配置
    int batchSize = meshImportConfig.getBatchSizeForTable("entry-term");
    int totalProcessed = 0;
    int batchNum = 1;

    try (FileInputStream fis = new FileInputStream(xmlFile);
        Stream<com.patra.catalog.domain.model.entity.MeshEntryTerm> stream =
            xmlParserPort.parseEntryTerms(fis)) {

      // 2. 准备批次容器
      List<com.patra.catalog.domain.model.entity.MeshEntryTerm> batch =
          new ArrayList<>(batchSize);

      // 3. 流式消费
      Iterator<com.patra.catalog.domain.model.entity.MeshEntryTerm> iterator = stream.iterator();
      while (iterator.hasNext()) {
        batch.add(iterator.next());

        // 4. 达到批次大小或已是最后一条
        if (batch.size() >= batchSize || !iterator.hasNext()) {
          try {
            // 5. 批量保存到数据库
            meshDescriptorPort.saveEntryTermsBatch(batch);

            totalProcessed += batch.size();

            // 6. 更新进度
            aggregate.updateTableProgress("entry-term", totalProcessed, batchNum);
            meshImportPort.save(aggregate);

            log.info("批次 {} 保存成功，已处理 {}", batchNum, totalProcessed);

            batchNum++;
            batch.clear();

          } catch (Exception e) {
            log.error("批次 {} 保存失败，表：entry-term", batchNum, e);
            throw new RuntimeException("批次保存失败：" + e.getMessage(), e);
          }
        }
      }

      log.info("EntryTerm 导入完成，总数量：{}", totalProcessed);
      return totalProcessed;
    }
  }

  /// 导入 Concept（概念）。
  ///
  /// @param xmlFile XML 文件
  /// @param aggregate 导入任务聚合根（用于更新进度）
  /// @return 实际导入数量
  private int importConcepts(File xmlFile, MeshImportAggregate aggregate) throws Exception {
    log.info("开始导入 Concept（概念）");

    // 1. 获取批次大小配置
    int batchSize = meshImportConfig.getBatchSizeForTable("concept");
    int totalProcessed = 0;
    int batchNum = 1;

    try (FileInputStream fis = new FileInputStream(xmlFile);
        Stream<com.patra.catalog.domain.model.entity.MeshConcept> stream =
            xmlParserPort.parseConcepts(fis)) {

      // 2. 准备批次容器
      List<com.patra.catalog.domain.model.entity.MeshConcept> batch = new ArrayList<>(batchSize);

      // 3. 流式消费
      Iterator<com.patra.catalog.domain.model.entity.MeshConcept> iterator = stream.iterator();
      while (iterator.hasNext()) {
        batch.add(iterator.next());

        // 4. 达到批次大小或已是最后一条
        if (batch.size() >= batchSize || !iterator.hasNext()) {
          try {
            // 5. 批量保存到数据库
            meshDescriptorPort.saveConceptsBatch(batch);

            totalProcessed += batch.size();

            // 6. 更新进度
            aggregate.updateTableProgress("concept", totalProcessed, batchNum);
            meshImportPort.save(aggregate);

            log.info("批次 {} 保存成功，已处理 {}", batchNum, totalProcessed);

            batchNum++;
            batch.clear();

          } catch (Exception e) {
            log.error("批次 {} 保存失败，表：concept", batchNum, e);
            throw new RuntimeException("批次保存失败：" + e.getMessage(), e);
          }
        }
      }

      log.info("Concept 导入完成，总数量：{}", totalProcessed);
      return totalProcessed;
    }
  }

  /// 验证数据量。
  ///
  /// @param importedCounts 实际导入数量
  /// @param aggregate 任务聚合根
  private void validateDataCounts(
      Map<String, Integer> importedCounts, MeshImportAggregate aggregate) {
    log.info("开始验证数据量，实际导入：{}", importedCounts);

    MeshDataValidator.ValidationResult result =
        meshDataValidator.validateDataCounts(importedCounts);

    if (result.hasWarnings()) {
      log.warn("数据量验证发现 {} 个警告：", result.warningCount());
      result.warnings().forEach(log::warn);
      // 注意：有警告不阻塞任务完成，只记录警告信息
    } else {
      log.info("数据量验证通过，所有表数据量在预期范围内");
    }
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
