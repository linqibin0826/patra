package com.patra.catalog.infra.adapter.batch.organization;

import com.patra.catalog.domain.model.aggregate.OrganizationAggregate;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import java.io.InputStream;
import java.net.URI;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

/// ROR 机构流式读取器。
///
/// **职责**：
///
/// - 从远程 URL 流式下载并解析 ROR Data Dump JSON
/// - 支持断点续传（通过 ExecutionContext 保存/恢复进度）
/// - 委托 StreamingDownloadPort 下载，RorOrganizationParser 解析
///
/// **流式处理特性**：
///
/// - 无磁盘落盘，HTTP 响应体直接传递给 Parser
/// - 失败时需重新下载（用户已确认可接受）
///
/// **断点续传实现**：
///
/// - 在 `open()` 中从 ExecutionContext 恢复 `currentIndex`，跳过已处理记录
/// - 在 `update()` 中保存 `currentIndex` 到 ExecutionContext
/// - chunk size 决定断点精度（如 chunk=500，最多重复处理 499 条）
///
/// **资源管理**：
///
/// - StreamingDownloadResult 持有 HTTP 连接，在 `close()` 中释放
/// - Stream.close() 释放 JsonParser
///
/// **Bean 注册**：
///
/// 通过 [RorOrganizationJobConfig#rorOrganizationItemReader] 方法注册为 `@StepScope` Bean，
/// 支持 Job 参数注入（downloadUrl、rorVersion）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class RorOrganizationItemReader implements ItemStreamReader<OrganizationAggregate> {

  private static final String CURRENT_INDEX_KEY = "ror.organization.current.index";

  /// 进度日志输出间隔（每处理多少条记录输出一次）。
  private static final int PROGRESS_LOG_INTERVAL = 5000;

  private final StreamingDownloadPort streamingDownloadPort;
  private final RorOrganizationParser rorOrganizationParser;
  private final String downloadUrl;
  private final String rorVersion;

  private StreamingDownloadResult downloadResult;
  private Stream<OrganizationAggregate> stream;
  private Iterator<OrganizationAggregate> iterator;
  private int currentIndex = 0;
  private int skipCount = 0;
  private Instant startTime;
  private int lastLoggedIndex = 0;

  /// 构造函数。
  ///
  /// @param streamingDownloadPort 流式下载端口
  /// @param rorOrganizationParser ROR 机构解析器
  /// @param downloadUrl JSON 文件下载 URL
  /// @param rorVersion ROR 版本号
  public RorOrganizationItemReader(
      StreamingDownloadPort streamingDownloadPort,
      RorOrganizationParser rorOrganizationParser,
      String downloadUrl,
      String rorVersion) {
    this.streamingDownloadPort = streamingDownloadPort;
    this.rorOrganizationParser = rorOrganizationParser;
    this.downloadUrl = downloadUrl;
    this.rorVersion = rorVersion;
  }

  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    log.info("开始流式下载 ROR Organization JSON：{}，版本：{}", downloadUrl, rorVersion);

    // 记录开始时间（用于计算处理速率）
    startTime = Instant.now();

    // 从 ExecutionContext 恢复进度
    if (executionContext.containsKey(CURRENT_INDEX_KEY)) {
      skipCount = executionContext.getInt(CURRENT_INDEX_KEY);
      lastLoggedIndex = skipCount;
      log.info("从断点恢复，将跳过前 {} 条记录（需重新下载文件）", skipCount);
    }

    try {
      // 流式下载（无磁盘落盘）
      downloadResult = streamingDownloadPort.download(URI.create(downloadUrl));
      log.info(
          "HTTP 连接建立成功，Content-Length：{}，开始解析",
          downloadResult.contentLength() > 0 ? downloadResult.contentLength() : "未知");

      // 委托 RorOrganizationParser 解析
      InputStream dataStream = openDataStream(downloadResult, downloadUrl);
      stream = rorOrganizationParser.parse(dataStream);
      iterator = stream.iterator();

      // 跳过已处理的记录（断点续传）
      for (int i = 0; i < skipCount && iterator.hasNext(); i++) {
        iterator.next();
        currentIndex++;
      }

      log.info("跳过完成，当前索引：{}", currentIndex);
    } catch (Exception e) {
      throw new ItemStreamException("打开 ROR 机构数据流失败", e);
    }
  }

  /// 打开可解析的数据流。
  ///
  /// - ZIP 文件：读取首个 JSON 文件条目
  /// - 非 ZIP 文件：直接使用原始输入流
  ///
  /// @param downloadResult 下载结果
  /// @param url 下载 URL
  /// @return 可解析的数据流
  private InputStream openDataStream(StreamingDownloadResult downloadResult, String url) {
    InputStream inputStream = downloadResult.inputStream();
    if (!isZipUrl(url)) {
      return inputStream;
    }

    ZipInputStream zipInputStream = new ZipInputStream(inputStream);
    ZipEntry entry = nextJsonEntry(zipInputStream);
    if (entry == null) {
      throw new IllegalStateException("ZIP 中未找到 JSON 数据文件");
    }
    log.info("检测到 ZIP，使用条目解析：{}", entry.getName());
    return zipInputStream;
  }

  /// 判断下载 URL 是否为 ZIP 文件。
  ///
  /// @param url 下载 URL
  /// @return 是否为 ZIP
  private boolean isZipUrl(String url) {
    return url != null && url.toLowerCase(Locale.ROOT).contains(".zip");
  }

  /// 从 ZIP 中找到下一个 JSON 文件条目。
  ///
  /// @param zipInputStream ZIP 输入流
  /// @return JSON 条目（不存在则返回 null）
  private ZipEntry nextJsonEntry(ZipInputStream zipInputStream) {
    try {
      ZipEntry entry = zipInputStream.getNextEntry();
      while (entry != null) {
        if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
          return entry;
        }
        entry = zipInputStream.getNextEntry();
      }
      return null;
    } catch (Exception e) {
      throw new IllegalStateException("读取 ZIP 条目失败: " + e.getMessage(), e);
    }
  }

  @Override
  public OrganizationAggregate read() {
    if (iterator != null && iterator.hasNext()) {
      currentIndex++;
      return iterator.next();
    }
    return null; // 返回 null 表示读取完成
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
  /// 格式：`进度: 5,000 条 | 速率: 1,234 条/秒 | 已用时: 00:00:04`
  private void logProgress() {
    Duration elapsed = Duration.between(startTime, Instant.now());
    long elapsedMillis = elapsed.toMillis();

    // 计算处理速率（条/秒）
    // 注意：这里计算的是本次执行的处理速率，不包括断点恢复跳过的记录
    int processedInThisRun = currentIndex - skipCount;
    long rate = elapsedMillis > 0 ? (processedInThisRun * 1000L) / elapsedMillis : 0;

    log.info(
        "进度: {} 条 | 速率: {} 条/秒 | 已用时: {}",
        formatNumber(currentIndex),
        formatNumber(rate),
        formatDuration(elapsed));
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
    log.info("关闭 ROR Organization 读取器，共处理 {} 条记录", currentIndex);

    // 关闭 Stream（释放 JsonParser）
    if (stream != null) {
      try {
        stream.close();
      } catch (Exception e) {
        log.warn("关闭 Stream 时发生异常", e);
      }
    }

    // 关闭 HTTP 连接
    if (downloadResult != null) {
      try {
        downloadResult.close();
      } catch (Exception e) {
        log.warn("关闭 HTTP 连接时发生异常", e);
      }
    }
  }
}
