package dev.patra.catalog.infra.publication.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.publication.model.enums.PubmedFileStatus;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileRecord;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileRef;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.publication.persistence.dao.PubmedFileDao;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `PubmedFileRepositoryAdapter` 集成测试。
///
/// 写方法要求调用方的事务（`MANDATORY`），用例跑在测试事务里；事务外调用会被拒绝，单独验证。
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PubmedFileRepositoryAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("PubmedFileRepositoryAdapter 集成测试")
class PubmedFileRepositoryAdapterIT {

  private static final Instant T0 = Instant.parse("2026-10-20T05:00:00Z");

  @Autowired PubmedFileRepositoryAdapter adapter;
  @Autowired PubmedFileDao dao;

  @AfterEach
  void cleanUp() {
    dao.deleteAll();
  }

  /// 进度写方法在事务外调用时被拒绝（`MANDATORY`）。
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("事务外调用写方法被拒绝")
  void should_reject_write_outside_transaction() {
    assertThatThrownBy(
            () -> adapter.markRunning(ref(1336), PubmedImportScope.EXISTING_ONLY, null, T0))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  @DisplayName("返回最后一个成功文件的文件号，失败的不算")
  void should_return_last_completed_seq() {
    complete(1335);
    complete(1336);
    PubmedFileRef failed = ref(1337);
    adapter.markRunning(failed, PubmedImportScope.EXISTING_ONLY, null, T0);
    adapter.markFailed(failed.fileName(), "boom", T0);

    assertThat(adapter.findLastCompletedSeq(PubmedFileKind.UPDATE)).hasValue(1336);
  }

  @Test
  @DisplayName("返回最小的未完成文件号：失败与中断都算，越过它完成的后续文件不影响")
  void should_return_first_unfinished_seq() {
    complete(1335);
    PubmedFileRef failed = ref(1336);
    adapter.markRunning(failed, PubmedImportScope.EXISTING_ONLY, null, T0);
    adapter.markFailed(failed.fileName(), "boom", T0);
    complete(1337);
    adapter.markRunning(ref(1338), PubmedImportScope.EXISTING_ONLY, null, T0);

    assertThat(adapter.findFirstUnfinishedSeq(PubmedFileKind.UPDATE)).hasValue(1336);
    assertThat(adapter.findLastCompletedSeq(PubmedFileKind.UPDATE)).hasValue(1337);
  }

  @Test
  @DisplayName("UPDATE 的续跑只看 UPDATE 行：失败的 BASELINE 行不影响")
  void should_resume_update_seq_ignoring_failed_baseline_rows() {
    PubmedFileRef baseline = PubmedFileRef.of(PubmedFileKind.BASELINE, "pubmed26n", 1333);
    adapter.markRunning(baseline, PubmedImportScope.EXISTING_ONLY, null, T0);
    adapter.markFailed(baseline.fileName(), "boom", T0);
    complete(1335);

    assertThat(adapter.findFirstUnfinishedSeq(PubmedFileKind.UPDATE)).isEmpty();
    assertThat(adapter.findLastCompletedSeq(PubmedFileKind.UPDATE)).hasValue(1335);
    assertThat(adapter.findFirstUnfinishedSeq(PubmedFileKind.BASELINE)).hasValue(1333);
    assertThat(adapter.findByFileName(baseline.fileName()).orElseThrow().kind())
        .isEqualTo(PubmedFileKind.BASELINE);
  }

  @Test
  @DisplayName("全部完成时没有未完成文件")
  void should_return_empty_when_all_completed() {
    complete(1335);

    assertThat(adapter.findFirstUnfinishedSeq(PubmedFileKind.UPDATE)).isEmpty();
  }

  @Test
  @DisplayName("没有成功文件时返回空")
  void should_return_empty_when_no_completed_file() {
    assertThat(adapter.findLastCompletedSeq(PubmedFileKind.UPDATE)).isEmpty();
  }

  @Test
  @DisplayName("失败的文件重跑时清空计数与错误、状态回到 RUNNING")
  void should_reset_counts_when_rerunning_failed_file() {
    PubmedFileRef file = ref(1337);
    adapter.markRunning(file, PubmedImportScope.EXISTING_ONLY, null, T0);
    adapter.markCompleted(
        file.fileName(),
        PubmedFileCounts.builder().updated(9).deleted(2).build(),
        T0.plusSeconds(60));
    adapter.markFailed(file.fileName(), "x".repeat(3000), T0.plusSeconds(120));

    PubmedFileRecord failed = adapter.findByFileName(file.fileName()).orElseThrow();
    assertThat(failed.status()).isEqualTo(PubmedFileStatus.FAILED);
    assertThat(failed.lastError()).hasSize(2000);

    adapter.markRunning(file, PubmedImportScope.ALL, null, T0.plusSeconds(180));

    PubmedFileRecord rerun = adapter.findByFileName(file.fileName()).orElseThrow();
    assertThat(rerun.status()).isEqualTo(PubmedFileStatus.RUNNING);
    assertThat(rerun.scope()).isEqualTo(PubmedImportScope.ALL);
    assertThat(rerun.lastError()).isNull();
    assertThat(rerun.finishedAt()).isNull();
    assertThat(rerun.counts()).isEqualTo(PubmedFileCounts.empty());
    assertThat(dao.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("完成时写入全部计数")
  void should_store_counts_when_completed() {
    PubmedFileRef file = ref(1340);
    adapter.markRunning(file, PubmedImportScope.EXISTING_ONLY, null, T0);
    PubmedFileCounts counts = new PubmedFileCounts(1, 2, 3, 4, 5, 6, 7, 8);
    adapter.markCompleted(file.fileName(), counts, T0.plusSeconds(30));

    PubmedFileRecord record = adapter.findByFileName(file.fileName()).orElseThrow();
    assertThat(record.status()).isEqualTo(PubmedFileStatus.COMPLETED);
    assertThat(record.fileSeq()).isEqualTo(1340);
    assertThat(record.counts()).isEqualTo(counts);
    assertThat(record.startedAt()).isEqualTo(T0);
    assertThat(record.finishedAt()).isEqualTo(T0.plusSeconds(30));
  }

  @Test
  @DisplayName("记下这次处理所用的 generation，失败后仍可读回")
  void should_keep_generation_after_failure() {
    PubmedFileRef file = ref(1341);
    adapter.markRunning(file, PubmedImportScope.EXISTING_ONLY, "replay-1", T0);
    adapter.markFailed(file.fileName(), "boom", T0.plusSeconds(10));

    assertThat(adapter.findByFileName(file.fileName()).orElseThrow().generation())
        .isEqualTo("replay-1");
  }

  private void complete(int seq) {
    PubmedFileRef file = ref(seq);
    adapter.markRunning(file, PubmedImportScope.EXISTING_ONLY, null, T0);
    adapter.markCompleted(file.fileName(), PubmedFileCounts.empty(), T0);
  }

  private static PubmedFileRef ref(int seq) {
    return PubmedFileRef.of(PubmedFileKind.UPDATE, "pubmed26n", seq);
  }
}
