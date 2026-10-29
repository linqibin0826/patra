package dev.patra.catalog.app.usecase.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import dev.patra.catalog.app.usecase.fulltext.fetch.PmcFullTextFetchPersister;
import dev.patra.catalog.domain.model.read.fulltext.FetchCandidate;
import dev.patra.catalog.domain.model.vo.fulltext.PmcArticleFile;
import dev.patra.catalog.domain.model.vo.fulltext.PmcArticleMetadata;
import dev.patra.catalog.domain.port.repository.PublicationFullTextRepository;
import dev.patra.catalog.domain.port.repository.PublicationRepository;
import dev.patra.catalog.domain.service.fulltext.FullTextRetrySchedule;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/// 全文抓取写入与「标为 PMC 开放获取」同成同败：监听在发布方的事务里同步执行，抛出异常时全文记录一起回滚。
///
/// 用无资源事务管理器观察事务结局：全文记录写入时登记事务同步，回调里拿到提交还是回滚。
@SpringJUnitConfig(PmcOpenAccessListenerIT.Config.class)
@DisplayName("PmcOpenAccessListener 同事务集成测试")
class PmcOpenAccessListenerIT {

  private static final AtomicInteger FULL_TEXT_OUTCOME = new AtomicInteger(-1);

  @Autowired private PmcFullTextFetchPersister persister;

  @Test
  @DisplayName("监听抛出异常时，同一事务里写入的全文记录也回滚")
  void should_roll_back_full_text_record_when_listener_fails() {
    FetchCandidate candidate = FetchCandidate.builder().publicationId(7L).pmcid("PMC1").build();
    PmcArticleMetadata selected =
        PmcArticleMetadata.builder()
            .pmcid("PMC1")
            .versionPrefix("PMC1.1")
            .openAccess(true)
            .licenseCode("CC BY")
            .xml(PmcArticleFile.of("PMC1.1.xml", "md5"))
            .build();

    assertThatThrownBy(
            () -> persister.recordFetched(candidate, selected, null, List.of(), Instant.EPOCH))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("mark failed");
    assertThat(FULL_TEXT_OUTCOME.get()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
  }

  /// 持久化器、监听与两个仓储替身。
  @Configuration
  @EnableTransactionManagement
  @Import({PmcFullTextFetchPersister.class, PmcOpenAccessListener.class})
  static class Config {

    /// 无资源事务管理器：支持事务同步，足以观察提交或回滚。
    ///
    /// @return 事务管理器
    @Bean
    PlatformTransactionManager transactionManager() {
      return new ResourcelessTransactionManager();
    }

    /// 全文记录仓储：写入时登记事务同步，记下事务结局。
    ///
    /// @return 仓储替身
    @Bean
    PublicationFullTextRepository fullTextRepository() {
      PublicationFullTextRepository repository = mock(PublicationFullTextRepository.class);
      doAnswer(
              invocation -> {
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                      @Override
                      public void afterCompletion(int status) {
                        FULL_TEXT_OUTCOME.set(status);
                      }
                    });
                return null;
              })
          .when(repository)
          .save(any());
      return repository;
    }

    /// 文献仓储：标为开放获取时失败。
    ///
    /// @return 仓储替身
    @Bean
    PublicationRepository publicationRepository() {
      PublicationRepository repository = mock(PublicationRepository.class);
      doThrow(new IllegalStateException("mark failed"))
          .when(repository)
          .markPmcOpenAccess(anyLong(), any());
      return repository;
    }

    /// 重试时间表。
    ///
    /// @return 时间表
    @Bean
    FullTextRetrySchedule retrySchedule() {
      return new FullTextRetrySchedule(Clock.systemUTC());
    }
  }
}
