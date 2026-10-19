package dev.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.model.enums.PubmedUpdateFileStatus;
import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileCounts;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileRecord;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileRef;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.persistence.dao.PubmedUpdateFileDao;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `PubmedUpdateFileRepositoryAdapter` 集成测试。
///
/// 关闭测试事务：调用方 Handler 不开事务，写方法必须自带事务，测试事务会掩盖这一点。
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PubmedUpdateFileRepositoryAdapter.class, JpaAuditingConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
@DisplayName("PubmedUpdateFileRepositoryAdapter 集成测试")
class PubmedUpdateFileRepositoryAdapterIT {

  private static final Instant T0 = Instant.parse("2026-10-20T05:00:00Z");

  @Autowired PubmedUpdateFileRepositoryAdapter adapter;
  @Autowired PubmedUpdateFileDao dao;

  @AfterEach
  void cleanUp() {
    dao.deleteAll();
  }

  @Test
  @DisplayName("返回最后一个成功文件的文件号，失败的不算")
  void should_return_last_completed_seq() {
    complete(1335);
    complete(1336);
    PubmedUpdateFileRef failed = ref(1337);
    adapter.markRunning(failed, PubmedUpdateScope.EXISTING_ONLY, null, T0);
    adapter.markFailed(failed.fileName(), "boom", T0);

    assertThat(adapter.findLastCompletedSeq()).hasValue(1336);
  }

  @Test
  @DisplayName("没有成功文件时返回空")
  void should_return_empty_when_no_completed_file() {
    assertThat(adapter.findLastCompletedSeq()).isEmpty();
  }

  @Test
  @DisplayName("失败的文件重跑时清空计数与错误、状态回到 RUNNING")
  void should_reset_counts_when_rerunning_failed_file() {
    PubmedUpdateFileRef file = ref(1337);
    adapter.markRunning(file, PubmedUpdateScope.EXISTING_ONLY, null, T0);
    adapter.markCompleted(
        file.fileName(),
        PubmedUpdateFileCounts.builder().updated(9).deleted(2).build(),
        T0.plusSeconds(60));
    adapter.markFailed(file.fileName(), "x".repeat(3000), T0.plusSeconds(120));

    PubmedUpdateFileRecord failed = adapter.findByFileName(file.fileName()).orElseThrow();
    assertThat(failed.status()).isEqualTo(PubmedUpdateFileStatus.FAILED);
    assertThat(failed.lastError()).hasSize(2000);

    adapter.markRunning(file, PubmedUpdateScope.ALL, null, T0.plusSeconds(180));

    PubmedUpdateFileRecord rerun = adapter.findByFileName(file.fileName()).orElseThrow();
    assertThat(rerun.status()).isEqualTo(PubmedUpdateFileStatus.RUNNING);
    assertThat(rerun.scope()).isEqualTo(PubmedUpdateScope.ALL);
    assertThat(rerun.lastError()).isNull();
    assertThat(rerun.finishedAt()).isNull();
    assertThat(rerun.counts()).isEqualTo(PubmedUpdateFileCounts.empty());
    assertThat(dao.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("完成时写入全部计数")
  void should_store_counts_when_completed() {
    PubmedUpdateFileRef file = ref(1340);
    adapter.markRunning(file, PubmedUpdateScope.EXISTING_ONLY, null, T0);
    PubmedUpdateFileCounts counts = new PubmedUpdateFileCounts(1, 2, 3, 4, 5, 6, 7);
    adapter.markCompleted(file.fileName(), counts, T0.plusSeconds(30));

    PubmedUpdateFileRecord record = adapter.findByFileName(file.fileName()).orElseThrow();
    assertThat(record.status()).isEqualTo(PubmedUpdateFileStatus.COMPLETED);
    assertThat(record.fileSeq()).isEqualTo(1340);
    assertThat(record.counts()).isEqualTo(counts);
    assertThat(record.startedAt()).isEqualTo(T0);
    assertThat(record.finishedAt()).isEqualTo(T0.plusSeconds(30));
  }

  @Test
  @DisplayName("记下这次处理所用的 generation，失败后仍可读回")
  void should_keep_generation_after_failure() {
    PubmedUpdateFileRef file = ref(1341);
    adapter.markRunning(file, PubmedUpdateScope.EXISTING_ONLY, "replay-1", T0);
    adapter.markFailed(file.fileName(), "boom", T0.plusSeconds(10));

    assertThat(adapter.findByFileName(file.fileName()).orElseThrow().generation())
        .isEqualTo("replay-1");
  }

  private void complete(int seq) {
    PubmedUpdateFileRef file = ref(seq);
    adapter.markRunning(file, PubmedUpdateScope.EXISTING_ONLY, null, T0);
    adapter.markCompleted(file.fileName(), PubmedUpdateFileCounts.empty(), T0);
  }

  private static PubmedUpdateFileRef ref(int seq) {
    return PubmedUpdateFileRef.of("pubmed26n", seq);
  }
}
