package dev.patra.interaction.app.subject.usecase.reconcile;

import dev.patra.interaction.app.subject.service.SubjectSources;
import dev.patra.interaction.domain.subject.exception.SubjectSourceUnavailableException;
import dev.patra.interaction.domain.subject.model.aggregate.SubjectAggregate;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SourceRecord;
import dev.patra.interaction.domain.subject.port.repository.SubjectRepository;
import dev.patra.interaction.domain.subject.port.source.SubjectSourcePort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 每日对账：对每种外部对象按 `subject_id` 升序 {@value #PAGE_SIZE} 行一页，按身份锚批量回查来源（事务外），
/// 套聚合的对账规则后一页一个事务写回。某页来源不可用就跳过、计数，继续下一页；任务内不重试，次日再跑。
///
/// 不加锁：调度中心「单机串行」保证同一时间只有一个实例在跑。与首次解析并发写同一行时，两边写的都是来源的
/// 当前事实，后写者覆盖，无害。
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileSubjectsHandler
    implements CommandHandler<ReconcileSubjectsCommand, ReconciliationResult> {

  /// 一页的行数，等于来源批量接口的上限。
  static final int PAGE_SIZE = 100;

  private final SubjectRepository repository;
  private final SubjectSources sources;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 对账全部外部对象；本地对象由所属子域维护，不进对账。
  ///
  /// @param command 命令
  /// @return 计数
  @Override
  public ReconciliationResult handle(ReconcileSubjectsCommand command) {
    Counter total = new Counter();
    for (SubjectType type : SubjectType.externalTypes()) {
      Counter counter = reconcile(type, sources.of(type));
      log.info(
          "对象对账完成: type={}, scanned={}, sourceIdChanged={}, becameUnavailable={}, revived={},"
              + " failedBatches={}",
          type,
          counter.scanned,
          counter.sourceIdChanged,
          counter.becameUnavailable,
          counter.revived,
          counter.failedBatches);
      total.add(counter);
    }
    return total.toResult();
  }

  /// 对账一种对象。
  ///
  /// @param type 对象种类
  /// @param source 它的来源策略
  /// @return 计数
  private Counter reconcile(SubjectType type, SubjectSourcePort source) {
    Counter counter = new Counter();
    long cursor = 0L;
    List<SubjectAggregate> page;
    while (!(page = repository.findPage(type, cursor, PAGE_SIZE)).isEmpty()) {
      cursor = page.getLast().getSubjectId().value();
      counter.scanned += page.size();
      Map<NaturalKey, SourceRecord> found;
      try {
        found =
            source.resolveByNaturalKeys(
                page.stream().map(SubjectAggregate::getNaturalKey).toList());
      } catch (SubjectSourceUnavailableException ex) {
        counter.failedBatches++;
        log.warn("对象对账跳过一页: type={}, afterSubjectId={}", type, cursor);
        continue;
      }
      Instant now = clock.instant();
      List<SubjectAggregate> reconciled =
          page.stream()
              .map(s -> s.reconcile(Optional.ofNullable(found.get(s.getNaturalKey())), now))
              .toList();
      for (int i = 0; i < page.size(); i++) {
        counter.count(page.get(i), reconciled.get(i));
      }
      transactions.executeWithoutResult(status -> repository.saveReconciled(reconciled));
    }
    return counter;
  }

  /// 可变计数器。
  private static final class Counter {
    private int scanned;
    private int sourceIdChanged;
    private int becameUnavailable;
    private int revived;
    private int failedBatches;

    /// 记一行的变化。
    ///
    /// @param before 对账前
    /// @param after 对账后
    void count(SubjectAggregate before, SubjectAggregate after) {
      if (!Objects.equals(before.getSourceId(), after.getSourceId())) {
        sourceIdChanged++;
      }
      if (before.isActive() && !after.isActive()) {
        becameUnavailable++;
      }
      if (!before.isActive() && after.isActive()) {
        revived++;
      }
    }

    /// 累加另一个计数器。
    ///
    /// @param other 计数器
    void add(Counter other) {
      scanned += other.scanned;
      sourceIdChanged += other.sourceIdChanged;
      becameUnavailable += other.becameUnavailable;
      revived += other.revived;
      failedBatches += other.failedBatches;
    }

    /// 转成结果。
    ///
    /// @return 结果
    ReconciliationResult toResult() {
      return ReconciliationResult.builder()
          .scanned(scanned)
          .sourceIdChanged(sourceIdChanged)
          .becameUnavailable(becameUnavailable)
          .revived(revived)
          .failedBatches(failedBatches)
          .build();
    }
  }
}
