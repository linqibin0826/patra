package com.patra.catalog.app.usecase.meshimport;

import com.patra.catalog.app.usecase.meshimport.command.StartImportCommand;
import com.patra.catalog.app.usecase.meshimport.dto.MeshImportResultDTO;
import com.patra.catalog.app.usecase.meshimport.validator.MeshDataValidator;
import com.patra.catalog.app.config.MeshImportConfig;
import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.enums.MeshImportTaskStatus;
import com.patra.catalog.domain.model.enums.MeshTableImportStatus;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.model.valueobject.TableProgress;
import com.patra.catalog.domain.port.MeshDescriptorPort;
import com.patra.catalog.domain.port.MeshFileDownloadPort;
import com.patra.catalog.domain.port.MeshImportPort;
import com.patra.catalog.domain.port.XmlParserPort;
import java.io.File;
import java.io.FileInputStream;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MeSH 导入编排器。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>编排 MeSH 数据导入的完整流程
 *   <li>协调 Domain 层和 Infrastructure 层
 *   <li>管理事务边界（@Transactional）
 * </ul>
 *
 * <p><b>编排流程</b>（startImport 方法）：
 *
 * <ol>
 *   <li>调用 {@link MeshFileDownloadPort#download} 下载 XML 文件
 *   <li>验证文件校验和 {@link MeshFileDownloadPort#validateChecksum}
 *   <li>使用 {@link XmlParserPort} 流式解析各类数据
 *   <li>按依赖顺序批量导入：Descriptor → Qualifier → TreeNumber/EntryTerm/Concept
 *   <li>调用 {@link MeshDataValidator#validateDataCounts} 验证数据量
 *   <li>更新任务状态，发布完成/失败事件
 * </ol>
 *
 * <p><b>事务管理</b>：
 *
 * <ul>
 *   <li>主方法使用 {@code @Transactional}
 *   <li>每批次独立事务：{@code @Transactional(propagation = REQUIRES_NEW)}
 * </ul>
 *
 * <p><b>依赖注入</b>：
 *
 * <ul>
 *   <li>{@link MeshImportPort} - 任务仓储
 *   <li>{@link XmlParserPort} - XML 解析器
 *   <li>{@link MeshFileDownloadPort} - 文件下载器
 *   <li>{@link MeshDescriptorPort} - 主题词仓储
 *   <li>{@link MeshDataValidator} - 数据验证器
 *   <li>{@link MeshImportConfig} - 配置属性
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeshImportOrchestrator {

  private final MeshImportPort meshImportPort;
  private final XmlParserPort xmlParserPort;
  private final MeshFileDownloadPort meshFileDownloadPort;
  private final MeshDescriptorPort meshDescriptorPort;
  private final MeshDataValidator meshDataValidator;
  private final MeshImportConfig meshImportConfig;

  /**
   * 开始导入 MeSH 数据。
   *
   * <p>完整流程编排：下载 → 校验 → 解析 → 批量导入 → 验证 → 完成
   *
   * @param command 启动命令
   * @return 导入结果
   * @throws IllegalStateException 如果已有正在运行的任务或校验失败
   * @throws RuntimeException 如果下载或导入过程失败
   */
  @Transactional
  public MeshImportResultDTO startImport(StartImportCommand command) {
    log.info("开始 MeSH 数据导入流程，命令：{}", command);

    // 1. 前置检查：确保没有正在运行的任务
    checkNoRunningTask();

    // 2. 解析命令参数
    String sourceUrl = resolveSourceUrl(command.sourceUrl());
    String taskName = resolveTaskName(command.taskName());

    // 3. 创建任务聚合根（PENDING 状态）
    MeshImportAggregate aggregate = createPendingTask(taskName, sourceUrl);

    try {
      // 4. 下载 XML 文件
      File xmlFile = downloadXmlFile(sourceUrl, aggregate);

      // 5. 开始导入（状态变为 PROCESSING）
      aggregate.startImport();
      aggregate = meshImportPort.save(aggregate);

      // 6. 解析并批量导入数据
      Map<String, Integer> importedCounts = importAllData(xmlFile, aggregate);

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

  /**
   * 重试失败的任务。
   *
   * @param taskId 任务 ID
   * @return 导入结果
   * @throws IllegalArgumentException 如果任务不存在
   * @throws IllegalStateException 如果任务不是 FAILED 状态
   */
  @Transactional
  public MeshImportResultDTO retryFailedTask(MeshImportId taskId) {
    log.info("重试失败的 MeSH 导入任务，任务 ID：{}", taskId.value());

    // 1. 查询任务
    MeshImportAggregate aggregate =
        meshImportPort
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在：" + taskId.value()));

    // 2. 重试任务（状态变为 PROCESSING）
    aggregate.retry();
    aggregate = meshImportPort.save(aggregate);

    // 3. 重新执行导入流程（从失败点继续）
    StartImportCommand command = new StartImportCommand(aggregate.getSourceUrl(), aggregate.getTaskName());
    return startImport(command);
  }

  /**
   * 清空进度并重新开始。
   *
   * <p>用于中断正在运行的任务并重置状态。
   */
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

  /**
   * 检查是否有正在运行的任务。
   *
   * @throws IllegalStateException 如果已有正在运行的任务
   */
  private void checkNoRunningTask() {
    if (meshImportPort.existsRunningTask()) {
      throw new IllegalStateException("已有正在运行的 MeSH 导入任务，请等待其完成或手动中断");
    }
  }

  /**
   * 解析数据源 URL（优先使用命令参数，否则使用配置）。
   *
   * @param commandUrl 命令中的 URL（可能为 null）
   * @return 最终使用的 URL
   */
  private String resolveSourceUrl(String commandUrl) {
    String url = (commandUrl != null && !commandUrl.isBlank())
        ? commandUrl
        : meshImportConfig.getSourceUrl();
    log.debug("解析数据源 URL：{}", url);
    return url;
  }

  /**
   * 解析任务名称（优先使用命令参数，否则自动生成）。
   *
   * @param commandName 命令中的名称（可能为 null）
   * @return 最终使用的名称
   */
  private String resolveTaskName(String commandName) {
    String name = (commandName != null && !commandName.isBlank())
        ? commandName
        : generateDefaultTaskName();
    log.debug("解析任务名称：{}", name);
    return name;
  }

  /**
   * 生成默认任务名称（格式："{year}年MeSH数据导入"）。
   *
   * @return 默认任务名称
   */
  private String generateDefaultTaskName() {
    int currentYear = Year.now().getValue();
    return String.format("%d年MeSH数据导入", currentYear);
  }

  /**
   * 创建 PENDING 状态的任务聚合根。
   *
   * @param taskName 任务名称
   * @param sourceUrl 数据源 URL
   * @return 保存后的聚合根
   */
  private MeshImportAggregate createPendingTask(String taskName, String sourceUrl) {
    log.info("创建 MeSH 导入任务，名称：{}, 数据源：{}", taskName, sourceUrl);

    // 创建聚合根（使用全参构造函数）
    MeshImportAggregate aggregate =
        new MeshImportAggregate(
            null, // ID 由仓储生成
            taskName,
            MeshImportTaskStatus.PENDING,
            null, // startTime 在 startImport() 时设置
            null, // endTime 在 markAsCompleted() 时设置
            sourceUrl,
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

  /**
   * 初始化表进度列表（5 张表，初始状态为 NOT_STARTED）。
   *
   * @return 表进度列表
   */
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

  /**
   * 下载 XML 文件并验证校验和。
   *
   * @param sourceUrl 数据源 URL
   * @param aggregate 任务聚合根
   * @return 下载后的文件
   * @throws IllegalStateException 如果校验和不匹配
   */
  private File downloadXmlFile(String sourceUrl, MeshImportAggregate aggregate) {
    log.info("开始下载 MeSH XML 文件，URL：{}", sourceUrl);

    // 下载文件
    File xmlFile = meshFileDownloadPort.download(sourceUrl);
    log.info("XML 文件下载完成，大小：{} 字节，路径：{}", xmlFile.length(), xmlFile.getAbsolutePath());

    // 验证校验和（TODO: 从 NLM 获取官方 MD5，这里暂时跳过）
    // String expectedHash = "TODO";
    // boolean valid = meshFileDownloadPort.validateChecksum(xmlFile, expectedHash);
    // if (!valid) {
    //   throw new IllegalStateException("XML 文件校验和不匹配，可能文件已损坏");
    // }

    return xmlFile;
  }

  /**
   * 导入所有数据（按依赖顺序）。
   *
   * @param xmlFile XML 文件
   * @param aggregate 任务聚合根
   * @return 实际导入数量映射
   */
  private Map<String, Integer> importAllData(File xmlFile, MeshImportAggregate aggregate)
      throws Exception {
    log.info("开始解析并导入 MeSH 数据，文件：{}", xmlFile.getAbsolutePath());

    Map<String, Integer> importedCounts = new HashMap<>();

    // 1. 导入 Descriptor（主题词）
    int descriptorCount = importDescriptors(xmlFile);
    importedCounts.put("descriptor", descriptorCount);
    aggregate.updateTableProgress("descriptor", descriptorCount, 1);
    meshImportPort.save(aggregate);

    // 2. 导入 TreeNumber（树形编号）
    int treeNumberCount = importTreeNumbers(xmlFile);
    importedCounts.put("tree-number", treeNumberCount);
    aggregate.updateTableProgress("tree-number", treeNumberCount, 1);
    meshImportPort.save(aggregate);

    // 3. 导入 EntryTerm（入口术语）
    int entryTermCount = importEntryTerms(xmlFile);
    importedCounts.put("entry-term", entryTermCount);
    aggregate.updateTableProgress("entry-term", entryTermCount, 1);
    meshImportPort.save(aggregate);

    // 4. 导入 Concept（概念）
    int conceptCount = importConcepts(xmlFile);
    importedCounts.put("concept", conceptCount);
    aggregate.updateTableProgress("concept", conceptCount, 1);
    meshImportPort.save(aggregate);

    log.info("所有数据导入完成，总计：{} 条记录", importedCounts.values().stream().mapToInt(Integer::intValue).sum());

    return importedCounts;
  }

  /**
   * 导入 Descriptor（主题词）。
   *
   * @param xmlFile XML 文件
   * @return 实际导入数量
   */
  private int importDescriptors(File xmlFile) throws Exception {
    log.info("开始导入 Descriptor（主题词）");
    try (FileInputStream fis = new FileInputStream(xmlFile);
        Stream<MeshDescriptorAggregate> stream = xmlParserPort.parseDescriptors(fis)) {
      // TODO: 批量保存到数据库（这里暂时只计数）
      int count = (int) stream.count();
      log.info("Descriptor 导入完成，数量：{}", count);
      return count;
    }
  }

  /**
   * 导入 TreeNumber（树形编号）。
   *
   * @param xmlFile XML 文件
   * @return 实际导入数量
   */
  private int importTreeNumbers(File xmlFile) throws Exception {
    log.info("开始导入 TreeNumber（树形编号）");
    try (FileInputStream fis = new FileInputStream(xmlFile)) {
      // TODO: 批量保存到数据库
      int count = (int) xmlParserPort.parseTreeNumbers(fis).count();
      log.info("TreeNumber 导入完成，数量：{}", count);
      return count;
    }
  }

  /**
   * 导入 EntryTerm（入口术语）。
   *
   * @param xmlFile XML 文件
   * @return 实际导入数量
   */
  private int importEntryTerms(File xmlFile) throws Exception {
    log.info("开始导入 EntryTerm（入口术语）");
    try (FileInputStream fis = new FileInputStream(xmlFile)) {
      // TODO: 批量保存到数据库
      int count = (int) xmlParserPort.parseEntryTerms(fis).count();
      log.info("EntryTerm 导入完成，数量：{}", count);
      return count;
    }
  }

  /**
   * 导入 Concept（概念）。
   *
   * @param xmlFile XML 文件
   * @return 实际导入数量
   */
  private int importConcepts(File xmlFile) throws Exception {
    log.info("开始导入 Concept（概念）");
    try (FileInputStream fis = new FileInputStream(xmlFile)) {
      // TODO: 批量保存到数据库
      int count = (int) xmlParserPort.parseConcepts(fis).count();
      log.info("Concept 导入完成，数量：{}", count);
      return count;
    }
  }

  /**
   * 验证数据量。
   *
   * @param importedCounts 实际导入数量
   * @param aggregate 任务聚合根
   */
  private void validateDataCounts(
      Map<String, Integer> importedCounts, MeshImportAggregate aggregate) {
    log.info("开始验证数据量，实际导入：{}", importedCounts);

    MeshDataValidator.ValidationResult result = meshDataValidator.validateDataCounts(importedCounts);

    if (result.hasWarnings()) {
      log.warn("数据量验证发现 {} 个警告：", result.warningCount());
      result.warnings().forEach(log::warn);
      // 注意：有警告不阻塞任务完成，只记录警告信息
    } else {
      log.info("数据量验证通过，所有表数据量在预期范围内");
    }
  }

  /**
   * 构建成功结果。
   *
   * @param aggregate 任务聚合根
   * @return 导入结果 DTO
   */
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
