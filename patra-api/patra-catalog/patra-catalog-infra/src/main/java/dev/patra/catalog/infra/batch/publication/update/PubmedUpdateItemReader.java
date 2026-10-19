package dev.patra.catalog.infra.batch.publication.update;

import dev.patra.catalog.domain.port.parser.PubmedXmlParserPort;
import dev.patra.catalog.domain.port.source.FileDownloadPort;
import dev.patra.catalog.domain.port.source.FileDownloadResult;
import dev.patra.common.CanonicalPublication;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/// PubMed updatefiles 文件的读取器。
///
/// 照 baseline 的 `PubmedArticleItemReader` 写（下载到临时文件、`GZIPInputStream`、按已读条数断点续传），
/// 但改调 `parseUpdateFile`，把 `DeleteCitation` 里的 PMID 收集起来，每次 `update` 写进 Step 上下文。
/// 不继承 baseline 读取器：baseline 的 Step 按类型注入读取器，继承后会出现两个候选。
///
/// 断点续传时不从上下文恢复删除列表：跳过已处理记录的过程同样会解析到 `DeleteCitation`
/// （它在文件末尾），列表会被重新收集完整。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
public class PubmedUpdateItemReader implements ItemStreamReader<CanonicalPublication> {

  private final FileDownloadPort fileDownloadPort;
  private final PubmedXmlParserPort parserPort;
  private final String downloadUrl;
  private final List<String> deletedPmids = new ArrayList<>();

  private Path tempFilePath;
  private Stream<CanonicalPublication> stream;
  private Iterator<CanonicalPublication> iterator;
  private int currentIndex;

  /// 创建读取器。
  ///
  /// @param fileDownloadPort 文件下载端口
  /// @param parserPort PubMed XML 解析端口
  /// @param downloadUrl 文件下载地址
  public PubmedUpdateItemReader(
      FileDownloadPort fileDownloadPort, PubmedXmlParserPort parserPort, String downloadUrl) {
    this.fileDownloadPort = fileDownloadPort;
    this.parserPort = parserPort;
    this.downloadUrl = downloadUrl;
  }

  /// 下载文件、开始解析，并按断点跳过已处理的记录。
  ///
  /// @param executionContext Step 上下文
  /// @throws ItemStreamException 下载或解析失败、断点跳过不完整时抛出
  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    int skipCount =
        executionContext.containsKey(PubmedUpdateContextKeys.CURRENT_INDEX)
            ? executionContext.getInt(PubmedUpdateContextKeys.CURRENT_INDEX)
            : 0;
    log.info("开始下载 PubMed updatefiles 文件：{}，断点 {}", downloadUrl, skipCount);
    try {
      FileDownloadResult downloadResult = fileDownloadPort.download(URI.create(downloadUrl));
      tempFilePath = downloadResult.filePath();
      GZIPInputStream gzipInputStream =
          new GZIPInputStream(new FileInputStream(tempFilePath.toFile()));
      stream = parserPort.parseUpdateFile(gzipInputStream, deletedPmids::add);
      iterator = stream.iterator();
      for (int i = 0; i < skipCount; i++) {
        if (!iterator.hasNext()) {
          throw new ItemStreamException("断点恢复失败：期望跳过 %d 条记录，实际只跳过 %d 条".formatted(skipCount, i));
        }
        iterator.next();
        currentIndex++;
      }
    } catch (ItemStreamException e) {
      throw e;
    } catch (Exception e) {
      throw new ItemStreamException("打开 PubMed updatefiles 读取器失败：" + downloadUrl, e);
    }
  }

  /// 读下一条文章记录。
  ///
  /// @return 文章记录，读完返回 `null`
  @Override
  public CanonicalPublication read() {
    if (iterator != null && iterator.hasNext()) {
      CanonicalPublication item = iterator.next();
      currentIndex++;
      return item;
    }
    return null;
  }

  /// 保存断点与已收集的删除 PMID。
  ///
  /// @param executionContext Step 上下文
  @Override
  public void update(ExecutionContext executionContext) throws ItemStreamException {
    executionContext.putInt(PubmedUpdateContextKeys.CURRENT_INDEX, currentIndex);
    executionContext.put(PubmedUpdateContextKeys.DELETED_PMIDS, new ArrayList<>(deletedPmids));
  }

  /// 关闭解析流并删除临时文件。
  @Override
  public void close() throws ItemStreamException {
    log.info("关闭 PubMed updatefiles 读取器，共读 {} 条，删除列表 {} 条", currentIndex, deletedPmids.size());
    if (stream != null) {
      try {
        stream.close();
      } catch (Exception e) {
        log.warn("关闭解析流时发生异常", e);
      }
    }
    if (tempFilePath != null) {
      try {
        Files.deleteIfExists(tempFilePath);
      } catch (Exception e) {
        log.warn("删除临时文件失败：{}", tempFilePath, e);
      }
    }
  }
}
