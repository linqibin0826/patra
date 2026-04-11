package com.patra.catalog.app.usecase.venue.scopus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort.PersistStats;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// ScopusEnrichmentPersister 单元测试。
///
/// Persister 的唯一职责是**承载事务边界**并把调用转发给 PersistPort。
/// 单元测试只验证参数/返回值/异常透传；事务边界的实际生效留给集成测试。
@DisplayName("ScopusEnrichmentPersister 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class ScopusEnrichmentPersisterTest {

  @Mock ScopusEnrichmentPersistPort persistPort;

  ScopusEnrichmentPersister persister;

  @BeforeEach
  void setUp() {
    persister = new ScopusEnrichmentPersister(persistPort);
  }

  @Test
  @DisplayName("透传 venueId / data 参数到 PersistPort")
  void persist_delegatesArgumentsToPort() {
    ScopusVenueData data = ScopusVenueData.builder().build();
    PersistStats expected = PersistStats.of(3);
    when(persistPort.persist(200L, data)).thenReturn(expected);

    PersistStats actual = persister.persist(200L, data);

    assertThat(actual).isSameAs(expected);
    verify(persistPort).persist(200L, data);
  }

  @Test
  @DisplayName("PersistPort 抛异常 - 向上传播（由事务边界触发回滚）")
  void persist_portThrows_propagates() {
    ScopusVenueData data = ScopusVenueData.builder().build();
    when(persistPort.persist(200L, data))
        .thenThrow(new RuntimeException("db constraint violation"));

    assertThatThrownBy(() -> persister.persist(200L, data))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db constraint violation");
  }
}
