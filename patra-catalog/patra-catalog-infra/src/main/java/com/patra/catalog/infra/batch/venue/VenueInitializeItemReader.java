package com.patra.catalog.infra.batch.venue;

import com.patra.catalog.domain.port.source.FileDownloadPort;
import com.patra.catalog.domain.port.source.FileDownloadResult;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/// OpenAlex Venue 多文件 JSON Lines 读取器。
///
/// **职责**：
///
/// - 逐个下载多个 .gz 压缩的 JSON Lines 文件到临时文件并解析
/// - 支持断点续传（通过 ExecutionContext 保存/恢复 fileIndex 和 lineIndex）
/// - 委托 FileDownloadPort 下载，OpenAlexSourceParser 解析
///
/// **临时文件策略**：
///
/// - 每个分区文件在 `openCurrentFile()` 中下载到临时文件
/// - 在 `closeCurrentFile()` 中删除当前临时文件（切换文件或关闭时）
/// - 逐个下载→处理→删除，减少磁盘占用
///
/// **断点续传实现**：
///
/// - 使用双索引：`fileIndex`（当前文件索引）+ `lineIndex`（当前文件内行索引）
/// - 在 `open()` 中从 ExecutionContext 恢复进度，跳过已处理文件和行
/// - 在 `update()` 中保存当前进度到 ExecutionContext
/// - chunk size 决定断点精度
/// - 恢复时需要重新下载当前文件
///
/// **文件切换逻辑**：
///
/// 当当前文件读取完毕时，删除当前临时文件，下载并打开下一个文件。
///
/// **Bean 注册**：
///
/// 通过 [VenueInitializeJobConfig#venueInitializeItemReader] 方法注册为 `@StepScope` Bean，
/// 支持 Job 参数注入。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class VenueInitializeItemReader implements ItemStreamReader<VenueParseResult> {

  private static final String FILE_INDEX_KEY = "venue.import.file.index";
  private static final String LINE_INDEX_KEY = "venue.import.line.index";

  /// 进度日志输出间隔（每处理多少条记录输出一次）。
  private static final int PROGRESS_LOG_INTERVAL = 2000;

  private final FileDownloadPort fileDownloadPort;
  private final OpenAlexSourceParser parser;
  private final List<String> partitionUrls;

  /// 当前文件索引（0-based）。
  private int currentFileIndex = 0;

  /// 当前文件内已读取的行数。
  private int currentLineIndex = 0;

  /// 需要跳过的行数（断点恢复时使用）。
  private int skipLineCount = 0;

  /// 当前分区文件的临时文件路径。
  private Path currentTempFilePath;

  /// 当前文件的记录流。
  private Stream<VenueParseResult> currentStream;

  /// 当前文件的迭代器。
  private Iterator<VenueParseResult> currentIterator;

  /// 已处理的总记录数。
  private int totalProcessedCount = 0;

  /// 开始时间（用于计算处理速率）。
  private Instant startTime;

  /// 上次日志输出时的处理数量。
  private int lastLoggedCount = 0;

  /// 构造函数。
  ///
  /// @param fileDownloadPort 文件下载端口
  /// @param parser OpenAlex Source 解析器
  /// @param partitionUrls 待处理的分区 URL 列表
  public VenueInitializeItemReader(
      FileDownloadPort fileDownloadPort, OpenAlexSourceParser parser, List<String> partitionUrls) {
    this.fileDownloadPort = fileDownloadPort;
    this.parser = parser;
    this.partitionUrls = partitionUrls;
  }

  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    log.info("打开 VenueInitializeItemReader，共 {} 个分区 URL", partitionUrls.size());
    startTime = Instant.now();

    // 从 ExecutionContext 恢复进度
    if (executionContext.containsKey(FILE_INDEX_KEY)) {
      currentFileIndex = executionContext.getInt(FILE_INDEX_KEY);
      skipLineCount = executionContext.getInt(LINE_INDEX_KEY);
      log.info("从断点恢复：文件索引={}，行索引={}（需重新下载当前文件）", currentFileIndex, skipLineCount);
    }

    // 如果 URL 列表为空，直接返回
    if (partitionUrls.isEmpty()) {
      log.info("分区 URL 列表为空，无需处理");
      return;
    }

    // 打开初始文件
    if (currentFileIndex < partitionUrls.size()) {
      try {
        openCurrentFile();
      } catch (Exception e) {
        throw new ItemStreamException("无法下载分区文件：" + partitionUrls.get(currentFileIndex), e);
      }
    }
  }

  @Override
  public VenueParseResult read() {
    // 尝试从当前迭代器读取
    while (true) {
      if (currentIterator != null && currentIterator.hasNext()) {
        // 先获取记录，成功后再递增索引（确保断点续传不会跳过未处理的记录）
        VenueParseResult item = currentIterator.next();
        currentLineIndex++;
        totalProcessedCount++;
        return item;
      }

      // 当前文件读完，关闭 HTTP 连接，切换到下一个文件
      closeCurrentFile();
      currentFileIndex++;
      currentLineIndex = 0;
      skipLineCount = 0;

      if (currentFileIndex >= partitionUrls.size()) {
        // 所有文件处理完成
        log.info("所有文件处理完成，共处理 {} 条记录", totalProcessedCount);
        return null;
      }

      try {
        openCurrentFile();
      } catch (Exception e) {
        throw new ItemStreamException("无法下载分区文件：" + partitionUrls.get(currentFileIndex), e);
      }
    }
  }

  @Override
  public void update(ExecutionContext executionContext) throws ItemStreamException {
    // 保存当前进度
    executionContext.putInt(FILE_INDEX_KEY, currentFileIndex);
    executionContext.putInt(LINE_INDEX_KEY, currentLineIndex);

    // 每隔 PROGRESS_LOG_INTERVAL 条记录输出一次进度日志
    if (totalProcessedCount - lastLoggedCount >= PROGRESS_LOG_INTERVAL) {
      lastLoggedCount = totalProcessedCount;
      logProgress();
    }
  }

  @Override
  public void close() throws ItemStreamException {
    log.info("关闭 VenueInitializeItemReader，共处理 {} 条记录", totalProcessedCount);
    closeCurrentFile();
  }

  /// 下载并打开当前索引指向的分区文件。
  ///
  /// 下载到临时文件，从本地文件创建输入流并开始解析。
  ///
  /// @throws java.io.IOException 下载或解析失败时
  private void openCurrentFile() throws java.io.IOException {
    String url = partitionUrls.get(currentFileIndex);
    log.debug("下载分区文件 [{}/{}]: {}", currentFileIndex + 1, partitionUrls.size(), url);

    // 下载到临时文件
    FileDownloadResult downloadResult = fileDownloadPort.download(URI.create(url));
    currentTempFilePath = downloadResult.filePath();
    log.debug("分区文件下载完成，临时文件：{}，大小：{} bytes", currentTempFilePath, downloadResult.fileSize());

    // 从临时文件创建输入流并解析
    currentStream = parser.parse(new FileInputStream(currentTempFilePath.toFile()));
    currentIterator = currentStream.iterator();

    // 跳过已处理的行（断点续传）
    for (int i = 0; i < skipLineCount && currentIterator.hasNext(); i++) {
      currentIterator.next();
      currentLineIndex++;
    }

    if (skipLineCount > 0) {
      log.debug("跳过 {} 行，当前行索引: {}", skipLineCount, currentLineIndex);
    }
  }

  /// 关闭当前文件（释放解析器资源并删除临时文件）。
  private void closeCurrentFile() {
    // 先关闭 Stream（释放解析器资源）
    if (currentStream != null) {
      try {
        currentStream.close();
      } catch (Exception e) {
        log.warn("关闭 Stream 失败", e);
      }
      currentStream = null;
    }

    // 删除当前分区的临时文件
    if (currentTempFilePath != null) {
      try {
        Files.deleteIfExists(currentTempFilePath);
        log.debug("临时文件已删除：{}", currentTempFilePath);
      } catch (Exception e) {
        log.warn("删除临时文件失败：{}", currentTempFilePath, e);
      }
      currentTempFilePath = null;
    }

    currentIterator = null;
  }

  /// 输出进度日志。
  private void logProgress() {
    Duration elapsed = Duration.between(startTime, Instant.now());
    long elapsedMillis = elapsed.toMillis();
    long rate = elapsedMillis > 0 ? (totalProcessedCount * 1000L) / elapsedMillis : 0;

    log.info(
        "进度: {} 条 | 分区 [{}/{}] | 速率: {} 条/秒 | 已用时: {}",
        formatNumber(totalProcessedCount),
        currentFileIndex + 1,
        partitionUrls.size(),
        formatNumber(rate),
        formatDuration(elapsed));
  }

  /// 格式化数字（使用中文区域的数字格式）。
  ///
  /// @param value 需要格式化的数值
  /// @return 格式化后的字符串
  private String formatNumber(long value) {
    return NumberFormat.getNumberInstance(Locale.CHINA).format(value);
  }

  /// 格式化时长为 HH:mm:ss 格式。
  private String formatDuration(Duration duration) {
    long seconds = duration.getSeconds();
    return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
  }
}
