package dev.patra.catalog.infra.publication.batch;

import dev.patra.catalog.domain.publication.model.vo.PubmedSourceRecord;
import dev.patra.catalog.domain.publication.port.repository.PublicationSyncRepository;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/// 本地重算的读取器：从来源表按 PMID 键集分页读出原文，不访问网络。
///
/// 只读有原文、文献未软删的行（墓碑与软删的文献不重算）。断点记最后一个已读 PMID。
///
/// @author linqibin
/// @since 0.1.0
public class PubmedSourceRecordReader implements ItemStreamReader<PubmedSourceRecord> {

  /// Step 上下文里记最后一个已读 PMID 的键。
  public static final String LAST_PMID = "pubmed.reparse.last.pmid";

  private final PublicationSyncRepository repository;
  private final int pageSize;
  private final Deque<PubmedSourceRecord> page = new ArrayDeque<>();

  private String lastPmid;
  private boolean exhausted;

  /// 创建读取器。
  ///
  /// @param repository 覆盖更新仓储（读来源表）
  /// @param pageSize 每页条数
  public PubmedSourceRecordReader(PublicationSyncRepository repository, int pageSize) {
    this.repository = repository;
    this.pageSize = pageSize;
  }

  /// 从断点恢复。
  ///
  /// @param executionContext Step 上下文
  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    lastPmid =
        executionContext.containsKey(LAST_PMID) ? executionContext.getString(LAST_PMID) : null;
  }

  /// 读下一篇；当前页读完时取下一页。
  ///
  /// @return 原文与来源位置；读完返回 `null`
  @Override
  public PubmedSourceRecord read() {
    if (page.isEmpty() && !exhausted) {
      List<PubmedSourceRecord> next = repository.findReparseCandidates(lastPmid, pageSize);
      page.addAll(next);
      exhausted = next.size() < pageSize;
    }
    PubmedSourceRecord record = page.pollFirst();
    if (record != null) {
      lastPmid = record.pmid();
    }
    return record;
  }

  /// 保存断点。
  ///
  /// @param executionContext Step 上下文
  @Override
  public void update(ExecutionContext executionContext) throws ItemStreamException {
    if (lastPmid != null) {
      executionContext.putString(LAST_PMID, lastPmid);
    }
  }
}
