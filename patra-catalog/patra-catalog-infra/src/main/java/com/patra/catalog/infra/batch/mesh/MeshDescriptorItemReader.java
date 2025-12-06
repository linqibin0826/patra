package com.patra.catalog.infra.batch.mesh;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.port.parser.XmlParserPort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

/// MeSH 主题词 XML 文件读取器。
///
/// **职责**：
///
/// - 读取 MeSH 主题词 XML 文件并逐条返回聚合根
///   - 支持断点续传（通过 ExecutionContext 保存/恢复进度）
///   - 委托 XmlParserPort 进行流式解析
///
/// **断点续传实现**：
///
/// - 在 `open()` 中从 ExecutionContext 恢复 `currentIndex`，跳过已处理记录
/// - 在 `update()` 中保存 `currentIndex` 到 ExecutionContext
/// - chunk size 决定断点精度（如 chunk=500，最多重复处理 499 条）
///
/// **Bean 注册**：
///
/// 通过 [MeshDescriptorJobConfig#meshDescriptorItemReader] 方法注册为 `@StepScope` Bean，
/// 支持 Job 参数注入（filePath、meshVersion）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class MeshDescriptorItemReader implements ItemStreamReader<MeshDescriptorAggregate> {

  private static final String CURRENT_INDEX_KEY = "mesh.descriptor.current.index";
  private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.CHINA);

  /// 进度日志输出间隔（每处理多少条记录输出一次）。
  private static final int PROGRESS_LOG_INTERVAL = 5000;

  private final XmlParserPort xmlParserPort;
  private final String filePath;
  private final String meshVersion;

  private InputStream inputStream;
  private Stream<MeshDescriptorAggregate> stream;
  private Iterator<MeshDescriptorAggregate> iterator;
  private int currentIndex = 0;
  private int skipCount = 0;
  private Instant startTime;
  private int lastLoggedIndex = 0;

  /// 构造函数。
  ///
  /// @param xmlParserPort XML 解析端口
  /// @param filePath XML 文件路径
  /// @param meshVersion MeSH 版本号
  public MeshDescriptorItemReader(
      XmlParserPort xmlParserPort, String filePath, String meshVersion) {
    this.xmlParserPort = xmlParserPort;
    this.filePath = filePath;
    this.meshVersion = meshVersion;
  }

  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    log.info("打开 MeSH Descriptor XML 文件：{}，版本：{}", filePath, meshVersion);

    // 记录开始时间（用于计算处理速率）
    startTime = Instant.now();

    // 从 ExecutionContext 恢复进度
    if (executionContext.containsKey(CURRENT_INDEX_KEY)) {
      skipCount = executionContext.getInt(CURRENT_INDEX_KEY);
      lastLoggedIndex = skipCount;
      log.info("从断点恢复，跳过前 {} 条记录", skipCount);
    }

    try {
      // 打开文件流
      inputStream = Files.newInputStream(Path.of(filePath));

      // 委托 XmlParserPort 解析
      stream = xmlParserPort.parseDescriptors(inputStream, meshVersion);
      iterator = stream.iterator();

      // 跳过已处理的记录
      for (int i = 0; i < skipCount && iterator.hasNext(); i++) {
        iterator.next();
        currentIndex++;
      }

      log.info("跳过完成，当前索引：{}", currentIndex);

    } catch (IOException e) {
      throw new ItemStreamException("无法打开文件：" + filePath, e);
    }
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
        NUMBER_FORMAT.format(currentIndex),
        NUMBER_FORMAT.format(rate),
        formatDuration(elapsed));
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
    log.info("关闭 MeSH Descriptor XML 文件，共处理 {} 条记录", currentIndex);

    // 关闭 Stream
    if (stream != null) {
      try {
        stream.close();
      } catch (Exception e) {
        log.warn("关闭 Stream 时发生异常", e);
      }
    }

    // 关闭 InputStream
    if (inputStream != null) {
      try {
        inputStream.close();
      } catch (IOException e) {
        log.warn("关闭 InputStream 时发生异常", e);
      }
    }
  }
}
