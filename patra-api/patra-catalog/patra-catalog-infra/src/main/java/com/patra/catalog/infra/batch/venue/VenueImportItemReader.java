package com.patra.catalog.infra.batch.venue;

import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import java.net.URI;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

/// OpenAlex Venue 流式多文件 JSON Lines 读取器。
///
/// **职责**：
///
/// - 按需流式下载并顺序读取多个 .gz 压缩的 JSON Lines 文件
/// - 支持断点续传（通过 ExecutionContext 保存/恢复 fileIndex 和 lineIndex）
/// - 委托 OpenAlexSourceParser 进行解析
///
/// **流式处理特性**：
///
/// - 无磁盘落盘，每个分区文件按需从远程 URL 流式下载
/// - 切换文件时自动关闭当前 HTTP 连接，打开下一个
///
/// **断点续传实现**：
///
/// - 使用双索引：`fileIndex`（当前文件索引）+ `lineIndex`（当前文件内行索引）
/// - 在 `open()` 中从 ExecutionContext 恢复进度，跳过已处理文件和行
/// - 在 `update()` 中保存当前进度到 ExecutionContext
/// - chunk size 决定断点精度
/// - 恢复时需要重新下载当前文件（用户已确认可接受）
///
/// **文件切换逻辑**：
///
/// 当当前文件读取完毕时，关闭当前 HTTP 连接，打开下一个 URL 的连接。
///
/// **Bean 注册**：
///
/// 通过 VenueImportJobConfig 注册为 `@StepScope` Bean，支持 Job 参数注入。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class VenueImportItemReader implements ItemStreamReader<VenueParseResult> {

  private static final String FILE_INDEX_KEY = "venue.import.file.index";
  private static final String LINE_INDEX_KEY = "venue.import.line.index";

  /// 进度日志输出间隔（每处理多少条记录输出一次）。
  private static final int PROGRESS_LOG_INTERVAL = 2000;

  private final StreamingDownloadPort streamingDownloadPort;
  private final OpenAlexSourceParser parser;
  private final List<String> partitionUrls;

  /// 当前文件索引（0-based）。
  private int currentFileIndex = 0;

  /// 当前文件内已读取的行数。
  private int currentLineIndex = 0;

  /// 需要跳过的行数（断点恢复时使用）。
  private int skipLineCount = 0;

  /// 当前文件的 HTTP 下载结果（持有连接）。
  private StreamingDownloadResult currentDownloadResult;

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
  /// @param streamingDownloadPort 流式下载端口
  /// @param parser OpenAlex Source 解析器
  /// @param partitionUrls 待处理的分区 URL 列表
  public VenueImportItemReader(
      StreamingDownloadPort streamingDownloadPort,
      OpenAlexSourceParser parser,
      List<String> partitionUrls) {
    this.streamingDownloadPort = streamingDownloadPort;
    this.parser = parser;
    this.partitionUrls = partitionUrls;
  }

  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    log.info("打开 VenueImportItemReader，共 {} 个分区 URL", partitionUrls.size());
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
        currentLineIndex++;
        totalProcessedCount++;
        return currentIterator.next();
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
    log.info("关闭 VenueImportItemReader，共处理 {} 条记录", totalProcessedCount);
    closeCurrentFile();
  }

  /// 流式下载并打开当前索引指向的分区文件。
  ///
  /// 建立 HTTP 连接，获取输入流并开始解析。
  ///
  /// @throws java.io.IOException 下载或解析失败时
  private void openCurrentFile() throws java.io.IOException {
    String url = partitionUrls.get(currentFileIndex);
    log.debug("流式下载分区文件 [{}/{}]: {}", currentFileIndex + 1, partitionUrls.size(), url);

    // 流式下载（建立 HTTP 连接）
    currentDownloadResult = streamingDownloadPort.download(URI.create(url));
    currentStream = parser.parse(currentDownloadResult.inputStream());
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

  /// 关闭当前文件（释放 HTTP 连接）。
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

    // 再关闭 HTTP 连接
    if (currentDownloadResult != null) {
      try {
        currentDownloadResult.close();
      } catch (Exception e) {
        log.warn("关闭 HTTP 连接失败", e);
      }
      currentDownloadResult = null;
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
