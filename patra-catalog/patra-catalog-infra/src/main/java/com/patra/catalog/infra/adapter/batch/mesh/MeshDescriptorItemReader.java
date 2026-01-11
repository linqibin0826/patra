package com.patra.catalog.infra.adapter.batch.mesh;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.port.parser.MeshDescriptorParserPort;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import java.net.URI;
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

/// MeSH 主题词流式读取器。
///
/// **职责**：
///
/// - 从远程 URL 流式下载并解析 MeSH 主题词 XML
/// - 支持断点续传（通过 ExecutionContext 保存/恢复进度）
/// - 委托 StreamingDownloadPort 下载，MeshDescriptorParserPort 解析
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
/// - Stream.close() 释放 XMLStreamReader
///
/// **Bean 注册**：
///
/// 通过 [MeshDescriptorJobConfig#meshDescriptorItemReader] 方法注册为 `@StepScope` Bean，
/// 支持 Job 参数注入（downloadUrl、meshVersion）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class MeshDescriptorItemReader implements ItemStreamReader<MeshDescriptorAggregate> {

  private static final String CURRENT_INDEX_KEY = "mesh.descriptor.current.index";

  /// 进度日志输出间隔（每处理多少条记录输出一次）。
  private static final int PROGRESS_LOG_INTERVAL = 5000;

  private final StreamingDownloadPort streamingDownloadPort;
  private final MeshDescriptorParserPort descriptorParserPort;
  private final String downloadUrl;
  private final String meshVersion;

  private StreamingDownloadResult downloadResult;
  private Stream<MeshDescriptorAggregate> stream;
  private Iterator<MeshDescriptorAggregate> iterator;
  private int currentIndex = 0;
  private int skipCount = 0;
  private Instant startTime;
  private int lastLoggedIndex = 0;

  /// 构造函数。
  ///
  /// @param streamingDownloadPort 流式下载端口
  /// @param descriptorParserPort 主题词解析端口
  /// @param downloadUrl XML 文件下载 URL
  /// @param meshVersion MeSH 版本号
  public MeshDescriptorItemReader(
      StreamingDownloadPort streamingDownloadPort,
      MeshDescriptorParserPort descriptorParserPort,
      String downloadUrl,
      String meshVersion) {
    this.streamingDownloadPort = streamingDownloadPort;
    this.descriptorParserPort = descriptorParserPort;
    this.downloadUrl = downloadUrl;
    this.meshVersion = meshVersion;
  }

  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    log.info("开始流式下载 MeSH Descriptor XML：{}，版本：{}", downloadUrl, meshVersion);

    // 记录开始时间（用于计算处理速率）
    startTime = Instant.now();

    // 从 ExecutionContext 恢复进度
    if (executionContext.containsKey(CURRENT_INDEX_KEY)) {
      skipCount = executionContext.getInt(CURRENT_INDEX_KEY);
      lastLoggedIndex = skipCount;
      log.info("从断点恢复，将跳过前 {} 条记录（需重新下载文件）", skipCount);
    }

    // 流式下载（无磁盘落盘）
    downloadResult = streamingDownloadPort.download(URI.create(downloadUrl));
    log.info(
        "HTTP 连接建立成功，Content-Length：{}，开始解析",
        downloadResult.contentLength() > 0 ? downloadResult.contentLength() : "未知");

    // 委托 MeshDescriptorParserPort 解析
    // Parser 返回不含版本的聚合根，在流转换时设置版本号
    stream =
        descriptorParserPort
            .parse(downloadResult.inputStream())
            .map(d -> d.withMeshVersion(meshVersion));
    iterator = stream.iterator();

    // 跳过已处理的记录（断点续传）
    for (int i = 0; i < skipCount && iterator.hasNext(); i++) {
      iterator.next();
      currentIndex++;
    }

    log.info("跳过完成，当前索引：{}", currentIndex);
  }

  @Override
  public MeshDescriptorAggregate read() {
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
    log.info("关闭 MeSH Descriptor 读取器，共处理 {} 条记录", currentIndex);

    // 关闭 Stream（释放 XMLStreamReader）
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
