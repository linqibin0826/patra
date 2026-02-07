package com.patra.catalog.infra.adapter.batch.author;

import com.patra.catalog.domain.model.aggregate.AuthorAggregate;
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
import java.util.Locale;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/// PubMed Computed Authors 读取器。
///
/// **职责**：
///
/// - 从远程 URL 下载并解析 PubMed Computed Authors JSON Lines
/// - 支持断点续传（通过 ExecutionContext 保存/恢复进度）
/// - 委托 FileDownloadPort 下载，PubMedComputedAuthorParser 解析
///
/// **临时文件策略**：
///
/// - 在 `open()` 中通过 FileDownloadPort 下载到临时文件（约 3.6GB）
/// - 从本地临时文件创建 InputStream 进行解析，彻底解耦下载与处理
/// - 在 `close()` 中删除临时文件
///
/// **断点续传实现**：
///
/// - 在 `open()` 中从 ExecutionContext 恢复 `currentIndex`，跳过已处理记录
/// - 在 `update()` 中保存 `currentIndex` 到 ExecutionContext
/// - chunk size 决定断点精度（如 chunk=1000，最多重复处理 999 条）
/// - 恢复时需要重新下载文件
///
/// **资源管理**：
///
/// - Stream.close() 释放 BufferedReader 和 FileInputStream
/// - close() 中删除临时文件
///
/// **Bean 注册**：
///
/// 通过 [AuthorImportJobConfig#authorItemReader] 方法注册为 `@StepScope` Bean，
/// 支持 Job 参数注入（downloadUrl）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class AuthorItemReader implements ItemStreamReader<AuthorAggregate> {

  private static final String CURRENT_INDEX_KEY = "author.import.current.index";

  /// 进度日志输出间隔（每处理多少条记录输出一次）。
  ///
  /// 由于数据量大（2100 万+），设置为 100,000 条以减少日志量。
  private static final int PROGRESS_LOG_INTERVAL = 100_000;

  private final FileDownloadPort fileDownloadPort;
  private final PubMedComputedAuthorParser parser;
  private final String downloadUrl;
  private final Long maxRecords;

  /// 当前下载的临时文件路径。
  private Path tempFilePath;

  private Stream<AuthorAggregate> stream;
  private Iterator<AuthorAggregate> iterator;
  private int currentIndex = 0;
  private int skipCount = 0;
  private Instant startTime;
  private int lastLoggedIndex = 0;

  /// 构造函数。
  ///
  /// @param fileDownloadPort 文件下载端口
  /// @param parser JSON Lines 解析器
  /// @param downloadUrl JSON Lines 文件下载 URL
  /// @param maxRecords 最大导入记录数限制（null 或 ≤0 表示不限制）
  public AuthorItemReader(
      FileDownloadPort fileDownloadPort,
      PubMedComputedAuthorParser parser,
      String downloadUrl,
      Long maxRecords) {
    this.fileDownloadPort = fileDownloadPort;
    this.parser = parser;
    this.downloadUrl = downloadUrl;
    this.maxRecords = maxRecords;
  }

  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    log.info("开始下载 PubMed Computed Authors JSON Lines：{}", downloadUrl);

    // 输出记录数限制配置
    if (hasRecordLimit()) {
      log.info("配置了最大导入记录数限制：{}（达到后将自动终止）", formatNumber(maxRecords));
    } else {
      log.info("未配置记录数限制，将导入全部数据");
    }

    // 记录开始时间（用于计算处理速率）
    startTime = Instant.now();

    // 从 ExecutionContext 恢复进度
    if (executionContext.containsKey(CURRENT_INDEX_KEY)) {
      skipCount = executionContext.getInt(CURRENT_INDEX_KEY);
      lastLoggedIndex = skipCount;
      log.info("从断点恢复，将跳过前 {} 条记录（需重新下载文件）", formatNumber(skipCount));
    }

    // 下载到临时文件
    try {
      FileDownloadResult downloadResult = fileDownloadPort.download(URI.create(downloadUrl));
      tempFilePath = downloadResult.filePath();
      log.info(
          "文件下载完成，临时文件：{}，大小：{} bytes（{} GB）",
          tempFilePath,
          formatNumber(downloadResult.fileSize()),
          String.format("%.2f", downloadResult.fileSize() / (1024.0 * 1024.0 * 1024.0)));

      // 从临时文件创建输入流并解析
      stream = parser.parse(new FileInputStream(tempFilePath.toFile()));
      iterator = stream.iterator();

      // 跳过已处理的记录（断点续传）
      if (skipCount > 0) {
        log.info("开始跳过已处理的记录...");
        int skippedCount = 0;
        for (int i = 0; i < skipCount && iterator.hasNext(); i++) {
          iterator.next();
          currentIndex++;
          skippedCount++;
          // 每跳过 100 万条记录输出一次进度
          if (currentIndex % 1_000_000 == 0) {
            log.info("跳过进度：{} / {}", formatNumber(currentIndex), formatNumber(skipCount));
          }
        }

        // 验证是否成功跳过了所有记录
        if (skippedCount < skipCount) {
          throw new ItemStreamException(
              String.format("断点恢复失败：期望跳过 %d 条记录，实际只跳过 %d 条（文件可能已损坏或被截断）", skipCount, skippedCount));
        }

        log.info("跳过完成，当前索引：{}", formatNumber(currentIndex));
      }

    } catch (Exception e) {
      throw new ItemStreamException("打开 PubMed Computed Authors 读取器失败", e);
    }
  }

  @Override
  public AuthorAggregate read() {
    // 检查是否达到最大记录数限制
    if (hasRecordLimit() && currentIndex >= maxRecords) {
      if (iterator != null && iterator.hasNext()) {
        log.info("已达到最大导入记录数限制（{}），终止读取", formatNumber(maxRecords));
      }
      return null;
    }

    if (iterator != null && iterator.hasNext()) {
      // 先获取记录，成功后再递增索引（确保断点续传不会跳过未处理的记录）
      AuthorAggregate author = iterator.next();
      currentIndex++;
      return author;
    }
    return null; // 返回 null 表示读取完成
  }

  /// 检查是否配置了记录数限制。
  ///
  /// @return 如果 maxRecords > 0 返回 true
  private boolean hasRecordLimit() {
    return maxRecords != null && maxRecords > 0;
  }

  @Override
  public void update(ExecutionContext executionContext) throws ItemStreamException {
    // 每个 chunk 提交时保存进度
    executionContext.putInt(CURRENT_INDEX_KEY, currentIndex);

    // 每隔 PROGRESS_LOG_INTERVAL 条记录输出一次进度日志
    if (currentIndex - lastLoggedIndex >= PROGRESS_LOG_INTERVAL) {
      lastLoggedIndex = currentIndex;
      logProgress();
    }
  }

  /// 输出进度日志。
  ///
  /// 格式：`进度: 100,000 条 | 速率: 1,234 条/秒 | 已用时: 00:01:21 | 预计剩余: 04:42:30`
  private void logProgress() {
    Duration elapsed = Duration.between(startTime, Instant.now());
    long elapsedMillis = elapsed.toMillis();

    // 计算处理速率（条/秒）
    // 注意：这里计算的是本次执行的处理速率，不包括断点恢复跳过的记录
    int processedInThisRun = currentIndex - skipCount;
    long rate = elapsedMillis > 0 ? (processedInThisRun * 1000L) / elapsedMillis : 0;

    // 估算剩余时间（假设总量约 2100 万）
    long estimatedTotal = 21_000_000L;
    long remaining = estimatedTotal - currentIndex;
    String etaStr = "未知";
    if (rate > 0 && remaining > 0) {
      Duration eta = Duration.ofSeconds(remaining / rate);
      etaStr = formatDuration(eta);
    }

    log.info(
        "进度: {} 条 | 速率: {} 条/秒 | 已用时: {} | 预计剩余: {}",
        formatNumber(currentIndex),
        formatNumber(rate),
        formatDuration(elapsed),
        etaStr);
  }

  /// 格式化数字为千分位格式。
  ///
  /// 使用方法级别创建 NumberFormat 以确保线程安全。
  ///
  /// @param value 数值
  /// @return 格式化后的字符串（如 `1,234`）
  private String formatNumber(long value) {
    return NumberFormat.getNumberInstance(Locale.CHINA).format(value);
  }

  /// 格式化时长为 HH:mm:ss 格式。
  ///
  /// @param duration 时长
  /// @return 格式化后的字符串
  private String formatDuration(Duration duration) {
    long seconds = duration.getSeconds();
    return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
  }

  @Override
  public void close() throws ItemStreamException {
    log.info("关闭 PubMed Computed Authors 读取器，共处理 {} 条记录", formatNumber(currentIndex));

    // 关闭 Stream（释放 BufferedReader 和 FileInputStream）
    if (stream != null) {
      try {
        stream.close();
      } catch (Exception e) {
        log.warn("关闭 Stream 时发生异常", e);
      }
      stream = null;
    }

    // 删除临时文件
    if (tempFilePath != null) {
      try {
        Files.deleteIfExists(tempFilePath);
        log.debug("临时文件已删除：{}", tempFilePath);
      } catch (Exception e) {
        log.warn("删除临时文件失败：{}", tempFilePath, e);
      }
      tempFilePath = null;
    }

    iterator = null;
  }
}
