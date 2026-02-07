package com.patra.catalog.infra.adapter.parser.support;

import com.patra.catalog.infra.adapter.parser.strategy.RecordParsingStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// StAX XML 解析器适配器抽象基类。
///
/// 封装通用的资源管理和流式解析逻辑，消除子类重复代码。
///
/// **模板方法模式**：
///
/// 子类只需调用 `doParse()` 并提供：
/// - 解析策略实例
/// - 日志消息模板
/// - 异常工厂
///
/// **资源管理**：
///
/// - 使用 `Stream.onClose()` 注册资源清理回调
/// - 确保 XMLStreamReader 和 InputStream 正确关闭
///
/// **使用示例**：
///
/// ```java
/// @Component
/// public class MyParserAdapter
///     extends AbstractStaxParserAdapter<MyEntity>
///     implements MyParserPort {
///
///   @Override
///   public Stream<MyEntity> parse(Path filePath) {
///     return doParse(
///         filePath,
///         MyParsingStrategy.INSTANCE,
///         "开始解析 My XML 文件：{}",
///         MyException::new);
///   }
/// }
/// ```
///
/// @param <T> 解析结果类型
/// @author linqibin
/// @since 0.1.0
public abstract class AbstractStaxParserAdapter<T> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  /// 解析 XML 文件，返回惰性求值的 Stream。
  ///
  /// @param filePath 文件路径
  /// @param strategy 解析策略
  /// @param logMessage 日志消息模板（使用 {} 占位符）
  /// @param exceptionFactory 异常工厂（接收错误消息和原始异常，返回运行时异常）
  /// @return 解析结果流（惰性求值）
  protected Stream<T> doParse(
      Path filePath,
      RecordParsingStrategy<T> strategy,
      String logMessage,
      BiFunction<String, Throwable, RuntimeException> exceptionFactory) {

    log.info(logMessage, filePath);

    try {
      InputStream inputStream = Files.newInputStream(filePath);
      XMLStreamReader reader =
          SecureXmlInputFactory.getInstance().createXMLStreamReader(inputStream);
      var spliterator = new RecordSpliterator<>(reader, strategy, XmlParsingContext.empty());

      return StreamSupport.stream(spliterator, false)
          .onClose(() -> closeReader(reader))
          .onClose(() -> closeInputStream(inputStream));
    } catch (IOException e) {
      log.error("打开文件失败：{}", filePath, e);
      throw exceptionFactory.apply("打开 XML 文件失败：" + filePath, e);
    } catch (XMLStreamException e) {
      log.error("创建 XML 读取器失败", e);
      throw exceptionFactory.apply("XML 解析失败", e);
    }
  }

  /// 解析 XML 输入流，返回惰性求值的 Stream。
  ///
  /// **与 `doParse(Path, ...)` 的区别**：
  ///
  /// - 此方法**不关闭**传入的 InputStream，由调用方负责管理
  /// - 适用于流式解析场景，与 `StreamingDownloadResult` 或 `FileInputStream` 配合使用
  ///
  /// **使用示例**：
  ///
  /// ```java
  /// // 方式 1：流式下载（小文件，App 层 Handler 使用）
  /// try (StreamingDownloadResult result = downloadPort.download(uri)) {
  ///     return doParse(result.inputStream(), strategy, "解析中", MyException::new);
  /// }
  /// // 方式 2：临时文件（大文件，Batch ItemReader 使用）
  /// var is = new FileInputStream(tempFilePath);
  /// return doParse(is, strategy, "解析中", MyException::new);
  /// ```
  ///
  /// @param inputStream 输入流（调用方负责关闭）
  /// @param strategy 解析策略
  /// @param logMessage 日志消息模板
  /// @param exceptionFactory 异常工厂
  /// @return 解析结果流（惰性求值）
  protected Stream<T> doParse(
      InputStream inputStream,
      RecordParsingStrategy<T> strategy,
      String logMessage,
      BiFunction<String, Throwable, RuntimeException> exceptionFactory) {

    log.info(logMessage);

    try {
      XMLStreamReader reader =
          SecureXmlInputFactory.getInstance().createXMLStreamReader(inputStream);
      var spliterator = new RecordSpliterator<>(reader, strategy, XmlParsingContext.empty());

      // 注意：不关闭 inputStream，由调用方管理（StreamingDownloadResult.close() 或 ItemReader.close()）
      return StreamSupport.stream(spliterator, false).onClose(() -> closeReader(reader));
    } catch (XMLStreamException e) {
      log.error("创建 XML 读取器失败", e);
      throw exceptionFactory.apply("XML 解析失败", e);
    }
  }

  /// 关闭 XMLStreamReader。
  private void closeReader(XMLStreamReader reader) {
    try {
      reader.close();
      log.debug("XMLStreamReader 已关闭");
    } catch (XMLStreamException e) {
      log.warn("关闭 XMLStreamReader 失败", e);
    }
  }

  /// 关闭输入流。
  private void closeInputStream(InputStream inputStream) {
    try {
      inputStream.close();
      log.debug("InputStream 已关闭");
    } catch (IOException e) {
      log.warn("关闭 InputStream 失败", e);
    }
  }
}
