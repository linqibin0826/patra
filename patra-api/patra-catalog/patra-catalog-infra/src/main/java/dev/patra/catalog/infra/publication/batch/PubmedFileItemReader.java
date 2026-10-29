package dev.patra.catalog.infra.publication.batch;

import dev.patra.catalog.domain.publication.model.vo.PubmedSourceRecord;
import dev.patra.catalog.domain.publication.model.vo.SourcePosition;
import dev.patra.catalog.infra.publication.batch.update.PubmedImportContextKeys;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/// 一个 PubMed 文件（baseline 或 updatefiles）的读取器：下载并校验 md5，逐篇产出原文与来源位置。
///
/// `DeleteCitation` 里的 PMID 收集起来，每次 `update` 写进 Step 上下文，供删除 Step 读取。断点续传按已读条数
/// 跳过；跳过的过程同样会读到文件末尾的 `DeleteCitation`，删除列表会被重新收集完整。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class PubmedFileItemReader implements ItemStreamReader<PubmedSourceRecord> {

  private final PubmedFileFetcher fetcher;
  private final String downloadUrl;
  private final SourcePosition position;
  private final List<String> deletedPmids = new ArrayList<>();

  private Path tempFile;
  private InputStream input;
  private PubmedArticleSplitter splitter;
  private int currentIndex;

  /// 创建读取器。
  ///
  /// @param fetcher 下载与校验
  /// @param downloadUrl 文件地址，文件名须合 PubMed 命名（用于计算来源位置）
  public PubmedFileItemReader(PubmedFileFetcher fetcher, String downloadUrl) {
    this.fetcher = fetcher;
    this.downloadUrl = downloadUrl;
    this.position =
        SourcePosition.ofFileName(downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1));
  }

  /// 下载校验、开始切分，并按断点跳过已读的记录。
  ///
  /// @param executionContext Step 上下文
  /// @throws ItemStreamException 下载、校验或断点跳过失败时
  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    int skip =
        executionContext.containsKey(PubmedImportContextKeys.CURRENT_INDEX)
            ? executionContext.getInt(PubmedImportContextKeys.CURRENT_INDEX)
            : 0;
    log.info("开始读取 PubMed 文件：{}，断点 {}", downloadUrl, skip);
    try {
      tempFile = fetcher.fetch(URI.create(downloadUrl));
      input = new GZIPInputStream(Files.newInputStream(tempFile));
      splitter = new PubmedArticleSplitter(input, deletedPmids::add);
      for (int i = 0; i < skip; i++) {
        if (splitter.next().isEmpty()) {
          throw new ItemStreamException("断点恢复失败：期望跳过 %d 条记录，实际只有 %d 条".formatted(skip, i));
        }
        currentIndex++;
      }
    } catch (ItemStreamException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw new ItemStreamException("打开 PubMed 文件失败：" + downloadUrl, e);
    }
  }

  /// 读下一篇。
  ///
  /// @return 原文与来源位置；读完返回 `null`
  @Override
  public PubmedSourceRecord read() {
    Optional<String> raw = splitter.next();
    if (raw.isEmpty()) {
      return null;
    }
    currentIndex++;
    return PubmedSourceRecord.of(PubmedArticleSplitter.pmidOf(raw.get()), raw.get(), position);
  }

  /// 保存断点与已收集的删除 PMID。
  ///
  /// @param executionContext Step 上下文
  @Override
  public void update(ExecutionContext executionContext) throws ItemStreamException {
    executionContext.putInt(PubmedImportContextKeys.CURRENT_INDEX, currentIndex);
    executionContext.put(PubmedImportContextKeys.DELETED_PMIDS, new ArrayList<>(deletedPmids));
  }

  /// 关闭流并删除临时文件。
  @Override
  public void close() throws ItemStreamException {
    log.info("关闭 PubMed 文件读取器，共读 {} 条，删除列表 {} 条", currentIndex, deletedPmids.size());
    try {
      if (splitter != null) {
        splitter.close();
      }
      if (input != null) {
        input.close();
      }
    } catch (Exception e) {
      log.warn("关闭 PubMed 文件流时发生异常", e);
    }
    if (tempFile != null) {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException e) {
        log.warn("删除临时文件失败：{}", tempFile, e);
      }
    }
  }
}
