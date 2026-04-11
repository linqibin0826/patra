package com.patra.catalog.app.usecase.venue.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort.PersistStats;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// LetPubEnrichmentPersister 单元测试。
///
/// Persister 的唯一职责是**承载事务边界**并把调用转发给 PersistPort。
/// 单元测试只验证 1) 参数透传 2) 返回值透传 3) 异常透传。事务边界的实际
/// 生效需要在集成测试里通过真实 Spring 容器验证（超出单元测试范围）。
@DisplayName("LetPubEnrichmentPersister 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class LetPubEnrichmentPersisterTest {

  @Mock LetPubEnrichmentPersistPort persistPort;

  LetPubEnrichmentPersister persister;

  @BeforeEach
  void setUp() {
    persister = new LetPubEnrichmentPersister(persistPort);
  }

  @Test
  @DisplayName("透传 venueId / data / coverKey 参数到 PersistPort")
  void persist_delegatesArgumentsToPort() {
    LetPubVenueData data = LetPubVenueData.empty();
    PersistStats expected = new PersistStats(10, 5, 2, true);
    when(persistPort.persist(100L, data, "catalog/venue-cover/100.jpg")).thenReturn(expected);

    PersistStats actual = persister.persist(100L, data, "catalog/venue-cover/100.jpg");

    assertThat(actual).isSameAs(expected);
    verify(persistPort).persist(100L, data, "catalog/venue-cover/100.jpg");
  }

  @Test
  @DisplayName("null coverKey 也原样透传（跳过封面下载路径）")
  void persist_nullCoverKey_passedThrough() {
    LetPubVenueData data = LetPubVenueData.empty();
    PersistStats expected = new PersistStats(1, 0, 0, false);
    when(persistPort.persist(100L, data, null)).thenReturn(expected);

    PersistStats actual = persister.persist(100L, data, null);

    assertThat(actual).isSameAs(expected);
    verify(persistPort).persist(100L, data, null);
  }

  @Test
  @DisplayName("PersistPort 抛异常 - 向上传播（由事务边界触发回滚）")
  void persist_portThrows_propagates() {
    LetPubVenueData data = LetPubVenueData.empty();
    when(persistPort.persist(100L, data, null))
        .thenThrow(new RuntimeException("db constraint violation"));

    assertThatThrownBy(() -> persister.persist(100L, data, null))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db constraint violation");
  }
}
