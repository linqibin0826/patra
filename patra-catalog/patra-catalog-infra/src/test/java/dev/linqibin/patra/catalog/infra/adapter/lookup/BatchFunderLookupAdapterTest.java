package dev.linqibin.patra.catalog.infra.adapter.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationExternalIdDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.OrganizationExternalIdEntity;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// BatchFunderLookupAdapter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("BatchFunderLookupAdapter")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class BatchFunderLookupAdapterTest {

  @Mock private OrganizationExternalIdDao externalIdDao;

  @Mock private OrganizationDao organizationDao;

  private BatchFunderLookupAdapter adapter;

  private static final Long ORG_ID = 123456L;
  private static final String FUNDREF_ID = "100000002";

  @BeforeEach
  void setUp() {
    // BatchFunderLookupAdapter 构造时会自动预热缓存
    when(externalIdDao.findAllByIdType("FUNDREF")).thenReturn(List.of());
    adapter = new BatchFunderLookupAdapter(externalIdDao, organizationDao);
  }

  @Nested
  @DisplayName("构造函数")
  class ConstructorTest {

    @Test
    @DisplayName("构造时应该自动预热 FundRef 缓存")
    void should_warmup_cache_on_construction() {
      // then - 验证预热方法被调用
      verify(externalIdDao).findAllByIdType("FUNDREF");
    }
  }

  @Nested
  @DisplayName("findByIdentifier()")
  class FindByIdentifierTest {

    @Test
    @DisplayName("应该委托给内部 CachingFunderLookupDecorator")
    void should_delegate_to_caching_decorator() {
      // given - 预热缓存时加载的数据
      OrganizationExternalIdEntity entity =
          OrganizationExternalIdEntity.builder().preferredValue(FUNDREF_ID).orgId(ORG_ID).build();
      when(externalIdDao.findAllByIdType("FUNDREF")).thenReturn(List.of(entity));

      // 重新创建 adapter 以加载新的缓存数据
      adapter = new BatchFunderLookupAdapter(externalIdDao, organizationDao);

      // when
      Optional<Long> result = adapter.findByIdentifier(FUNDREF_ID);

      // then
      assertThat(result).contains(ORG_ID);
    }

    @Test
    @DisplayName("未找到时应该返回 empty")
    void should_return_empty_when_not_found() {
      // given - 模拟数据库查询未找到
      when(externalIdDao.findByIdTypeAndPreferredValue(anyString(), anyString()))
          .thenReturn(Optional.empty());
      when(organizationDao.findByRorId(anyString())).thenReturn(Optional.empty());

      // when
      Optional<Long> result = adapter.findByIdentifier("unknown");

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByName()")
  class FindByNameTest {

    @Test
    @DisplayName("应该委托给内部 CachingFunderLookupDecorator")
    void should_delegate_to_caching_decorator() {
      // given
      when(organizationDao.findByDisplayName("NIH")).thenReturn(Optional.empty());

      // when
      Optional<Long> result = adapter.findByName("NIH");

      // then
      assertThat(result).isEmpty();
      verify(organizationDao).findByDisplayName("NIH");
    }
  }

  @Nested
  @DisplayName("getStats()")
  class GetStatsTest {

    @Test
    @DisplayName("应该返回缓存统计信息")
    void should_return_cache_stats() {
      // when
      String stats = adapter.getStats();

      // then
      assertThat(stats).contains("FunderLookupCache");
      assertThat(stats).contains("fundRefIdCache=");
      assertThat(stats).contains("rorIdCache=");
      assertThat(stats).contains("nameCache=");
    }
  }
}
