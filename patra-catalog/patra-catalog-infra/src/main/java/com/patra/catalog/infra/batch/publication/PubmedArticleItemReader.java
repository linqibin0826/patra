package com.patra.catalog.infra.batch.publication;

import com.patra.catalog.domain.port.parser.PubmedXmlParserPort;
import com.patra.catalog.domain.port.source.FileDownloadPort;
import com.patra.catalog.domain.port.source.FileDownloadResult;
import dev.linqibin.patra.common.model.CanonicalPublication;
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
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/// PubMed 文献读取器。
///
/// **职责**：
///
/// - 从远程 URL 下载 PubMed Baseline gzip 压缩文件到临时文件
/// - 从本地临时文件解压并解析 XML，逐条返回 `CanonicalPublication`
/// - 支持断点续传（通过 ExecutionContext 保存/恢复进度）
/// - 委托 FileDownloadPort 下载，PubmedXmlParserPort 解析
///
/// **临时文件策略**：
///
/// - 在 `open()` 中下载到临时文件，解耦 HTTP 连接与数据处理速度
/// - 在 `close()` 中删除临时文件
/// - 断点恢复时需重新下载文件
/// - 单文件约 30,000 条记录，内存占用可控
///
/// **断点续传实现**：
///
/// - 在 `open()` 中从 ExecutionContext 恢复 `currentIndex`，跳过已处理记录
/// - 在 `update()` 中保存 `currentIndex` 到 ExecutionContext
/// - chunk size 决定断点精度（如 chunk=500，最多重复处理 499 条）
///
/// **资源管理**：
///
/// - 临时文件在 `close()` 中通过 `Files.deleteIfExists()` 删除
/// - Stream.close() 释放 XMLStreamReader
///
/// **Bean 注册**：
///
/// 通过 [PubmedBaselineJobConfig#pubmedArticleItemReader] 方法注册为 `@StepScope` Bean，
/// 支持 Job 参数注入（downloadUrl）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class PubmedArticleItemReader implements ItemStreamReader<CanonicalPublication> {

  private static final String CURRENT_INDEX_KEY = "pubmed.article.current.index";

  /// 进度日志输出间隔（每处理多少条记录输出一次）。
  private static final int PROGRESS_LOG_INTERVAL = 5000;

  private final FileDownloadPort fileDownloadPort;
  private final PubmedXmlParserPort parserPort;
  private final String downloadUrl;

  private Path tempFilePath;
  private Stream<CanonicalPublication> stream;
  private Iterator<CanonicalPublication> iterator;
  private int currentIndex = 0;
  private int skipCount = 0;
  private Instant startTime;
  private int lastLoggedIndex = 0;

  /// 构造函数。
  ///
  /// @param fileDownloadPort 文件下载端口
  /// @param parserPort PubMed XML 解析端口
  /// @param downloadUrl gzip 压缩的 XML 文件下载 URL
  public PubmedArticleItemReader(
      FileDownloadPort fileDownloadPort, PubmedXmlParserPort parserPort, String downloadUrl) {
    this.fileDownloadPort = fileDownloadPort;
    this.parserPort = parserPort;
    this.downloadUrl = downloadUrl;
  }

  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    log.info("开始下载 PubMed Baseline XML 到临时文件：{}", downloadUrl);

    // 记录开始时间（用于计算处理速率）
    startTime = Instant.now();

    // 从 ExecutionContext 恢复进度
    if (executionContext.containsKey(CURRENT_INDEX_KEY)) {
      skipCount = executionContext.getInt(CURRENT_INDEX_KEY);
      lastLoggedIndex = skipCount;
      log.info("从断点恢复，将跳过前 {} 条记录（需重新下载文件）", skipCount);
    }

    // 下载到临时文件
    try {
      FileDownloadResult downloadResult = fileDownloadPort.download(URI.create(downloadUrl));
      tempFilePath = downloadResult.filePath();
      log.info("文件下载完成，临时文件：{}，大小：{} bytes", tempFilePath, downloadResult.fileSize());

      // 从临时文件创建 GZIPInputStream，然后委托 Parser 解析
      GZIPInputStream gzipInputStream =
          new GZIPInputStream(new FileInputStream(tempFilePath.toFile()));
      stream = parserPort.parse(gzipInputStream);
      iterator = stream.iterator();

      // 跳过已处理的记录（断点续传）
      if (skipCount > 0) {
        int skippedCount = 0;
        for (int i = 0; i < skipCount && iterator.hasNext(); i++) {
          iterator.next();
          currentIndex++;
          skippedCount++;
        }

        // 验证是否成功跳过了所有记录
        if (skippedCount < skipCount) {
          throw new ItemStreamException(
              String.format("断点恢复失败：期望跳过 %d 条记录，实际只跳过 %d 条（文件可能已损坏或被截断）", skipCount, skippedCount));
        }

        log.info("跳过完成，当前索引：{}", currentIndex);
      }

    } catch (ItemStreamException e) {
      throw e;
    } catch (Exception e) {
      throw new ItemStreamException("打开 PubMed Baseline 读取器失败", e);
    }
  }

  @Override
  public CanonicalPublication read() {
    if (iterator != null && iterator.hasNext()) {
      // 先获取记录，成功后再递增索引（确保断点续传不会跳过未处理的记录）
      CanonicalPublication item = iterator.next();
      currentIndex++;
      return item;
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
    log.info("关闭 PubMed Article 读取器，共处理 {} 条记录", currentIndex);

    // 关闭 Stream（释放 XMLStreamReader）
    if (stream != null) {
      try {
        stream.close();
      } catch (Exception e) {
        log.warn("关闭 Stream 时发生异常", e);
      }
    }

    // 删除临时文件
    if (tempFilePath != null) {
      try {
        Files.deleteIfExists(tempFilePath);
        log.debug("临时文件已删除：{}", tempFilePath);
      } catch (Exception e) {
        log.warn("删除临时文件失败：{}", tempFilePath, e);
      }
    }
  }
}
