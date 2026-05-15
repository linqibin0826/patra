package dev.linqibin.patra.catalog.infra.adapter.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationExternalIdDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.OrganizationEntity;
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

/// CachingFunderLookupDecorator 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("CachingFunderLookupDecorator")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class CachingFunderLookupDecoratorTest {

  @Mock private OrganizationExternalIdDao externalIdDao;

  @Mock private OrganizationDao organizationDao;

  @Mock private OrganizationExternalIdEntity externalIdEntity;

  @Mock private OrganizationEntity organizationEntity;

  private CachingFunderLookupDecorator decorator;

  private static final Long ORG_ID = 123456L;
  private static final Long ORG_ID_2 = 789012L;
  private static final String FUNDREF_ID = "100000002";
  private static final String ROR_ID = "01cwqze88";
  private static final String FUNDER_NAME = "National Institutes of Health";

  @BeforeEach
  void setUp() {
    decorator = new CachingFunderLookupDecorator(externalIdDao, organizationDao);
  }

  @Nested
  @DisplayName("findByIdentifier() 缓存行为")
  class FindByIdentifierCacheTest {

    @Test
    @DisplayName("FundRef ID 应该被缓存")
    void should_cache_fundref_id() {
      // given
      when(externalIdEntity.getOrgId()).thenReturn(ORG_ID);
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", FUNDREF_ID))
          .thenReturn(Optional.of(externalIdEntity));

      // when - 第一次调用
      Optional<Long> result1 = decorator.findByIdentifier(FUNDREF_ID);

      // when - 第二次调用（应该从缓存返回）
      Optional<Long> result2 = decorator.findByIdentifier(FUNDREF_ID);

      // then
      assertThat(result1).contains(ORG_ID);
      assertThat(result2).contains(ORG_ID);
      // 数据库只应该被查询一次
      verify(externalIdDao, times(1)).findByIdTypeAndPreferredValue("FUNDREF", FUNDREF_ID);
    }

    @Test
    @DisplayName("ROR ID 应该被缓存")
    void should_cache_ror_id() {
      // given
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", ROR_ID))
          .thenReturn(Optional.empty());
      when(organizationEntity.getId()).thenReturn(ORG_ID);
      when(organizationDao.findByRorId(ROR_ID)).thenReturn(Optional.of(organizationEntity));

      // when - 第一次调用
      Optional<Long> result1 = decorator.findByIdentifier(ROR_ID);

      // when - 第二次调用（应该从缓存返回）
      Optional<Long> result2 = decorator.findByIdentifier(ROR_ID);

      // then
      assertThat(result1).contains(ORG_ID);
      assertThat(result2).contains(ORG_ID);
      // 数据库只应该被查询一次
      verify(organizationDao, times(1)).findByRorId(ROR_ID);
    }

    @Test
    @DisplayName("未找到的标识符应该被加入 Negative Cache")
    void should_cache_not_found_identifiers() {
      // given
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", "unknown"))
          .thenReturn(Optional.empty());
      when(organizationDao.findByRorId("unknown")).thenReturn(Optional.empty());

      // when - 第一次调用
      Optional<Long> result1 = decorator.findByIdentifier("unknown");

      // when - 第二次调用（应该从 Negative Cache 返回）
      Optional<Long> result2 = decorator.findByIdentifier("unknown");

      // then
      assertThat(result1).isEmpty();
      assertThat(result2).isEmpty();
      // 数据库只应该被查询一次
      verify(externalIdDao, times(1)).findByIdTypeAndPreferredValue("FUNDREF", "unknown");
      verify(organizationDao, times(1)).findByRorId("unknown");
    }

    @Test
    @DisplayName("空标识符应该返回 empty 且不查询数据库")
    void should_return_empty_for_null_identifier() {
      // when
      Optional<Long> result = decorator.findByIdentifier(null);

      // then
      assertThat(result).isEmpty();
      verify(externalIdDao, never()).findByIdTypeAndPreferredValue(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("findByName() 缓存行为")
  class FindByNameCacheTest {

    @Test
    @DisplayName("机构名称应该被缓存")
    void should_cache_funder_name() {
      // given
      when(organizationEntity.getId()).thenReturn(ORG_ID);
      when(organizationDao.findByDisplayName(FUNDER_NAME))
          .thenReturn(Optional.of(organizationEntity));

      // when - 第一次调用
      Optional<Long> result1 = decorator.findByName(FUNDER_NAME);

      // when - 第二次调用（应该从缓存返回）
      Optional<Long> result2 = decorator.findByName(FUNDER_NAME);

      // then
      assertThat(result1).contains(ORG_ID);
      assertThat(result2).contains(ORG_ID);
      // 数据库只应该被查询一次
      verify(organizationDao, times(1)).findByDisplayName(FUNDER_NAME);
    }

    @Test
    @DisplayName("未找到的名称应该被加入 Negative Cache")
    void should_cache_not_found_names() {
      // given
      when(organizationDao.findByDisplayName("Unknown")).thenReturn(Optional.empty());

      // when - 第一次调用
      Optional<Long> result1 = decorator.findByName("Unknown");

      // when - 第二次调用（应该从 Negative Cache 返回）
      Optional<Long> result2 = decorator.findByName("Unknown");

      // then
      assertThat(result1).isEmpty();
      assertThat(result2).isEmpty();
      // 数据库只应该被查询一次
      verify(organizationDao, times(1)).findByDisplayName("Unknown");
    }

    @Test
    @DisplayName("空名称应该返回 empty 且不查询数据库")
    void should_return_empty_for_blank_name() {
      // when
      Optional<Long> result = decorator.findByName("  ");

      // then
      assertThat(result).isEmpty();
      verify(organizationDao, never()).findByDisplayName(anyString());
    }
  }

  @Nested
  @DisplayName("warmupFundRefCache()")
  class WarmupCacheTest {

    @Test
    @DisplayName("应该批量加载所有 FundRef 映射")
    void should_load_all_fundref_mappings() {
      // given
      OrganizationExternalIdEntity entity1 =
          OrganizationExternalIdEntity.builder().preferredValue(FUNDREF_ID).orgId(ORG_ID).build();
      OrganizationExternalIdEntity entity2 =
          OrganizationExternalIdEntity.builder()
              .preferredValue("100000001")
              .orgId(ORG_ID_2)
              .build();
      when(externalIdDao.findAllByIdType("FUNDREF")).thenReturn(List.of(entity1, entity2));

      // when
      decorator.warmupFundRefCache();

      // then - 预热后应该能从缓存获取
      Optional<Long> result1 = decorator.findByIdentifier(FUNDREF_ID);
      Optional<Long> result2 = decorator.findByIdentifier("100000001");

      assertThat(result1).contains(ORG_ID);
      assertThat(result2).contains(ORG_ID_2);
      // 预热后不应该再查询 FundRef
      verify(externalIdDao, never()).findByIdTypeAndPreferredValue(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("getStats()")
  class GetStatsTest {

    @Test
    @DisplayName("应该返回缓存统计信息")
    void should_return_cache_stats() {
      // given - 添加一些缓存数据
      when(externalIdEntity.getOrgId()).thenReturn(ORG_ID);
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", FUNDREF_ID))
          .thenReturn(Optional.of(externalIdEntity));
      decorator.findByIdentifier(FUNDREF_ID);

      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", "notfound"))
          .thenReturn(Optional.empty());
      when(organizationDao.findByRorId("notfound")).thenReturn(Optional.empty());
      decorator.findByIdentifier("notfound");

      // when
      String stats = decorator.getStats();

      // then
      assertThat(stats).contains("FunderLookupCache");
      assertThat(stats).contains("fundRefIdCache=1");
      assertThat(stats).contains("notFound=1");
    }
  }

  @Nested
  @DisplayName("clear()")
  class ClearTest {

    @Test
    @DisplayName("清空后应该重新查询数据库")
    void should_query_db_after_clear() {
      // given - 先缓存
      when(externalIdEntity.getOrgId()).thenReturn(ORG_ID);
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", FUNDREF_ID))
          .thenReturn(Optional.of(externalIdEntity));
      decorator.findByIdentifier(FUNDREF_ID);

      // when - 清空缓存
      decorator.clear();

      // then - 再次查询应该重新访问数据库
      decorator.findByIdentifier(FUNDREF_ID);
      verify(externalIdDao, times(2)).findByIdTypeAndPreferredValue("FUNDREF", FUNDREF_ID);
    }
  }
}
