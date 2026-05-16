package dev.linqibin.patra.catalog.infra.batch.mesh;

import dev.linqibin.patra.catalog.domain.model.aggregate.MeshScrAggregate;
import dev.linqibin.patra.catalog.domain.port.parser.MeshScrParserPort;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadResult;
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

/// MeSH 补充概念记录（SCR）读取器。
///
/// **职责**：
///
/// - 从远程 URL 下载 MeSH SCR XML 到临时文件
/// - 从本地临时文件解析 XML，逐条返回 `MeshScrAggregate`
/// - 支持断点续传（通过 ExecutionContext 保存/恢复进度）
/// - 委托 FileDownloadPort 下载，MeshScrParserPort 解析
///
/// **临时文件策略**：
///
/// - 在 `open()` 中下载到临时文件，解耦 HTTP 连接与数据处理速度
/// - 在 `close()` 中删除临时文件
/// - 断点恢复时需重新下载文件
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
/// 通过 [MeshScrJobConfig#meshScrItemReader] 方法注册为 `@StepScope` Bean，
/// 支持 Job 参数注入（downloadUrl、meshVersion）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class MeshScrItemReader implements ItemStreamReader<MeshScrAggregate> {

  private static final String CURRENT_INDEX_KEY = "mesh.scr.current.index";

  /// 进度日志输出间隔（每处理多少条记录输出一次）。
  private static final int PROGRESS_LOG_INTERVAL = 10000;

  private final FileDownloadPort fileDownloadPort;
  private final MeshScrParserPort scrParserPort;
  private final String downloadUrl;
  private final String meshVersion;

  private Path tempFilePath;
  private Stream<MeshScrAggregate> stream;
  private Iterator<MeshScrAggregate> iterator;
  private int currentIndex = 0;
  private int skipCount = 0;
  private Instant startTime;
  private int lastLoggedIndex = 0;

  /// 构造函数。
  ///
  /// @param fileDownloadPort 文件下载端口
  /// @param scrParserPort SCR 解析端口
  /// @param downloadUrl XML 文件下载 URL
  /// @param meshVersion MeSH 版本号
  public MeshScrItemReader(
      FileDownloadPort fileDownloadPort,
      MeshScrParserPort scrParserPort,
      String downloadUrl,
      String meshVersion) {
    this.fileDownloadPort = fileDownloadPort;
    this.scrParserPort = scrParserPort;
    this.downloadUrl = downloadUrl;
    this.meshVersion = meshVersion;
  }

  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    log.info("开始下载 MeSH SCR XML 到临时文件：{}，版本：{}", downloadUrl, meshVersion);

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

      // 从临时文件创建 InputStream，委托 Parser 解析
      // Parser 返回不含版本的聚合根，在流转换时设置版本号
      FileInputStream fileInputStream = new FileInputStream(tempFilePath.toFile());
      stream = scrParserPort.parse(fileInputStream).map(scr -> scr.withMeshVersion(meshVersion));
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
      throw new ItemStreamException("打开 MeSH SCR 读取器失败", e);
    }
  }

  @Override
  public MeshScrAggregate read() {
    if (iterator != null && iterator.hasNext()) {
      // 先获取记录，成功后再递增索引（确保断点续传不会跳过未处理的记录）
      MeshScrAggregate item = iterator.next();
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
  /// 格式：`进度: 10,000 条 | 速率: 1,234 条/秒 | 已用时: 00:00:08`
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
    log.info("关闭 MeSH SCR 读取器，共处理 {} 条记录", currentIndex);

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
