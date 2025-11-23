package com.patra.catalog.app.usecase.meshimport;

import static com.patra.catalog.domain.model.enums.MeshDataType.CONCEPT;
import static com.patra.catalog.domain.model.enums.MeshDataType.DESCRIPTOR;
import static com.patra.catalog.domain.model.enums.MeshDataType.ENTRY_TERM;
import static com.patra.catalog.domain.model.enums.MeshDataType.QUALIFIER;
import static com.patra.catalog.domain.model.enums.MeshDataType.TREE_NUMBER;

import com.patra.catalog.app.config.MeshImportConfig;
import com.patra.catalog.app.usecase.meshimport.dto.MeshImportResultDTO;
import com.patra.catalog.app.usecase.meshimport.strategy.MeshDataImporter;
import com.patra.catalog.app.usecase.meshimport.validator.MeshDataValidator;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.enums.MeshDataType;
import com.patra.catalog.domain.model.enums.MeshImportTaskStatus;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.model.valueobject.TableProgress;
import com.patra.catalog.domain.port.MeshDescriptorRepository;
import com.patra.catalog.domain.port.MeshFileDownloadPort;
import com.patra.catalog.domain.port.MeshImportRepository;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.patra.catalog.domain.port.XmlParserPort;
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
public class MeshImportOrchestrator {

  /// 字节转 MB 的常量（1 MB = 1024 * 1024 字节）
  private static final long BYTES_PER_MB = 1024 * 1024;

  private final MeshImportRepository meshImportRepository;
  private final MeshFileDownloadPort meshFileDownloadPort;
  private final MeshDataValidator meshDataValidator;
  private final MeshImportConfig meshImportConfig;

  /// 数据导入策略 Map（dataType → Importer）
  ///
  /// 通过 Spring 自动注入所有 {@link MeshDataImporter} 实现类，
  /// 并按 {@link MeshDataType} 枚举值建立映射关系。
  private final Map<MeshDataType, MeshDataImporter> importerMap;

  /// 构造函数（注入所有策略实现）。
  ///
  /// @param meshImportRepository 任务仓储
  /// @param meshFileDownloadPort 文件下载器
  /// @param meshDataValidator 数据验证器
  /// @param meshImportConfig 配置属性
  /// @param importers 所有数据导入策略实现（Spring 自动注入）
  public MeshImportOrchestrator(
      MeshImportRepository meshImportRepository,
      MeshFileDownloadPort meshFileDownloadPort,
      MeshDataValidator meshDataValidator,
      MeshImportConfig meshImportConfig,
      List<MeshDataImporter> importers) {
    this.meshImportRepository = meshImportRepository;
    this.meshFileDownloadPort = meshFileDownloadPort;
    this.meshDataValidator = meshDataValidator;
    this.meshImportConfig = meshImportConfig;

    // 构建策略 Map（dataType → Importer）
    this.importerMap =
        importers.stream()
            .collect(Collectors.toMap(MeshDataImporter::getDataType, Function.identity()));

    log.info(
        "[MeshImport] 导入策略已注册 | 策略数量: {} | 策略: {}",
        importerMap.size(),
        importerMap.keySet().stream().map(MeshDataType::getCode).collect(Collectors.joining(", ")));
  }

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
      aggregate = meshImportRepository.save(aggregate);

      // 6. 导入所有数据（使用策略模式）
      Map<String, Integer> importedCounts = importAllData(descXmlFile, qualXmlFile, aggregate);

      // 7. 验证数据量
      validateDataCounts(importedCounts, aggregate);

      // 8. 标记任务完成（状态变为 SUCCESS）
      aggregate.markAsCompleted();
      aggregate = meshImportRepository.save(aggregate);

      // 9. 记录完成日志
      logCompletionSummary(aggregate, importedCounts);

      // 10. 返回结果
      return buildSuccessResult(aggregate);

    } catch (Exception ex) {
      log.error(
          "[MeshImport] 导入任务失败 | 任务ID: {} | 错误: {}",
          aggregate.getId().value(),
          ex.getMessage(),
          ex);
      aggregate.markAsFailed(ex.getMessage());
      meshImportRepository.save(aggregate);
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
  public MeshImportResultDTO retryFailedTask(Long taskId) {
    log.info("[MeshImport] 重试失败任务 | 任务ID: {}", taskId);

    // 1. 转换为强类型 ID 并查询任务
    MeshImportId importId = MeshImportId.of(taskId);
    MeshImportAggregate aggregate =
        meshImportRepository
            .findById(importId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在：" + taskId));

    // 2. 检查任务状态（必须是 FAILED）
    if (aggregate.getStatus() != MeshImportTaskStatus.FAILED) {
      throw new IllegalStateException("只能重试失败的任务，当前状态：" + aggregate.getStatus().getCode());
    }

    // 3. 重试任务（状态变为 PROCESSING）
    aggregate.retry();
    aggregate = meshImportRepository.save(aggregate);

    try {
      // 4. 从下载步骤重新开始（不调用 startImport，避免"已有任务运行"检查）
      //    重要：使用聚合根中保存的 URL，确保重试时使用与首次导入相同的数据源
      File descXmlFile = downloadXmlFile(aggregate.getDescriptorSourceUrl(), "Descriptor");
      File qualXmlFile = downloadXmlFile(aggregate.getQualifierSourceUrl(), "Qualifier");

      // 5. 导入所有数据（使用策略模式）
      Map<String, Integer> importedCounts = importAllData(descXmlFile, qualXmlFile, aggregate);

      // 6. 验证数据量
      validateDataCounts(importedCounts, aggregate);

      // 7. 标记任务完成（状态变为 SUCCESS）
      aggregate.markAsCompleted();
      aggregate = meshImportRepository.save(aggregate);

      // 8. 记录完成日志
      logCompletionSummary(aggregate, importedCounts);

      // 9. 返回结果
      return buildSuccessResult(aggregate);

    } catch (Exception ex) {
      log.error(
          "[MeshImport] 重试任务失败 | 任务ID: {} | 错误: {}",
          aggregate.getId().value(),
          ex.getMessage(),
          ex);
      aggregate.markAsFailed(ex.getMessage());
      meshImportRepository.save(aggregate);
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
    meshImportRepository
        .findRunningTask()
        .ifPresent(
            aggregate -> {
              log.warn("[MeshImport] 发现正在运行的任务 | 任务ID: {} | 操作: 标记为失败", aggregate.getId().value());
              // 重置任务状态（通过创建新的聚合根，保留原任务记录）
              aggregate.markAsFailed("用户手动中断任务");
              meshImportRepository.save(aggregate);
            });
  }

  // ========== 私有辅助方法 ==========

  /// 检查是否有正在运行的任务。
  ///
  /// @throws IllegalStateException 如果已有正在运行的任务
  private void checkNoRunningTask() {
    if (meshImportRepository.existsRunningTask()) {
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

    return meshImportRepository.save(aggregate);
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
    log.info("[MeshImport] 开始解析并导入所有数据（策略模式）");
    log.info("[MeshImport] Descriptor文件: {}", descXmlFile.getAbsolutePath());
    log.info("[MeshImport] Qualifier文件: {}", qualXmlFile.getAbsolutePath());

    Map<String, Integer> importedCounts = new HashMap<>();

    // 按 MeshDataType 枚举定义的导入顺序执行（支持自定义排序）
    for (MeshDataType dataType : MeshDataType.values()) {
      MeshDataImporter importer = importerMap.get(dataType);

      if (importer == null) {
        log.warn("[MeshImport] 未找到数据类型 [{}] 的导入策略，跳过", dataType.getCode());
        continue;
      }

      // 选择正确的 XML 文件（Qualifier 使用单独文件，其他使用 Descriptor 文件）
      File xmlFile = (dataType == QUALIFIER) ? qualXmlFile : descXmlFile;

      try {
        int count = importer.importData(xmlFile, aggregate);
        importedCounts.put(dataType.getCode(), count);
      } catch (Exception e) {
        log.error(
            "[MeshImport] 数据类型 [{}] 导入失败 | 错误: {}", dataType.getCode(), e.getMessage(), e);
        throw new RuntimeException(
            "数据类型 [" + dataType.getCode() + "] 导入失败：" + e.getMessage(), e);
      }
    }

    int totalRecords = importedCounts.values().stream().mapToInt(Integer::intValue).sum();
    log.info(
        "[MeshImport] 所有数据导入完成 | 总计: {} 条 | Descriptor: {} | Qualifier: {} | TreeNumber: {} | EntryTerm: {} | Concept: {}",
        totalRecords,
        importedCounts.getOrDefault(DESCRIPTOR.getCode(), 0),
        importedCounts.getOrDefault(QUALIFIER.getCode(), 0),
        importedCounts.getOrDefault(TREE_NUMBER.getCode(), 0),
        importedCounts.getOrDefault(ENTRY_TERM.getCode(), 0),
        importedCounts.getOrDefault(CONCEPT.getCode(), 0));

    return importedCounts;
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

  /// 记录导入完成日志。
  ///
  /// @param aggregate 任务聚合根
  /// @param importedCounts 导入数量统计
  private void logCompletionSummary(
      MeshImportAggregate aggregate, Map<String, Integer> importedCounts) {
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
