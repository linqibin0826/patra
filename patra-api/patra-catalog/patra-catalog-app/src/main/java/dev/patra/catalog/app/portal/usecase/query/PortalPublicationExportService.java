package dev.patra.catalog.app.portal.usecase.query;

import dev.patra.catalog.domain.portal.model.enums.ExportFormat;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import dev.patra.catalog.domain.portal.port.citation.CitationExportPort;
import dev.patra.catalog.domain.portal.port.read.CitationRecordReadPort;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/// 引用导出服务（spec 第 6.2 节）：去重后按 500 个一批交给导出端口懒加载，读一批 → 写一批 → 丢弃，内存里最多 500 条。
///
/// 在响应线程里执行（控制器返回之后），不能依赖请求级会话：每批用一个独立的只读事务读取。
///
/// @author linqibin
/// @since 0.1.0
@Service
public class PortalPublicationExportService {

  private final CitationRecordReadPort readPort;
  private final CitationExportPort exportPort;
  private final TransactionTemplate readOnlyTx;

  /// 创建服务。
  ///
  /// @param readPort 引用记录读端口
  /// @param exportPort 导出端口
  /// @param transactionManager 事务管理器（每批开一个只读事务）
  public PortalPublicationExportService(
      CitationRecordReadPort readPort,
      CitationExportPort exportPort,
      PlatformTransactionManager transactionManager) {
    this.readPort = readPort;
    this.exportPort = exportPort;
    this.readOnlyTx = new TransactionTemplate(transactionManager);
    this.readOnlyTx.setReadOnly(true);
  }

  /// 导出到输出流。
  ///
  /// @param ids 文献主键（调用方已校验 1 ~ 5,000 个正整数）
  /// @param format 导出格式
  /// @param out 输出流（不关闭）
  /// @throws IOException 写出失败时（例如客户端断开）
  public void export(List<Long> ids, ExportFormat format, OutputStream out) throws IOException {
    List<Long> keys = List.copyOf(new LinkedHashSet<>(ids));
    exportPort.export(format, new BatchIterator(keys), out);
  }

  /// 在只读事务里读一批，按请求顺序排好，跳过不存在的。
  ///
  /// @param batch 一批文献主键
  /// @return 按请求顺序的记录
  private List<CitationRecord> readOrdered(List<Long> batch) {
    List<CitationRecord> records = readOnlyTx.execute(status -> readPort.findByIds(batch));
    Map<Long, CitationRecord> found =
        Objects.requireNonNull(records).stream()
            .collect(Collectors.toMap(CitationRecord::id, Function.identity()));
    return batch.stream().map(found::get).filter(Objects::nonNull).toList();
  }

  /// 按 500 个一批懒加载：每次 `next()` 才在只读事务里读这一批。
  private final class BatchIterator implements Iterator<List<CitationRecord>> {

    private final List<Long> keys;
    private int from;

    /// 创建迭代器。
    ///
    /// @param keys 去重后的文献主键，按请求顺序
    private BatchIterator(List<Long> keys) {
      this.keys = keys;
    }

    /// 是否还有下一批。
    ///
    /// @return 是否
    @Override
    public boolean hasNext() {
      return from < keys.size();
    }

    /// 读下一批。
    ///
    /// @return 按请求顺序的记录（不存在的跳过，可能为空）
    /// @throws NoSuchElementException 没有下一批时
    @Override
    public List<CitationRecord> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      int to = Math.min(from + CitationRecordReadPort.MAX_BATCH, keys.size());
      List<Long> batch = keys.subList(from, to);
      from = to;
      return readOrdered(batch);
    }
  }
}
