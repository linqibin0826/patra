package com.patra.catalog.infra.adapter.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.infra.persistence.dao.OrganizationDao;
import com.patra.catalog.infra.persistence.dao.OrganizationExternalIdDao;
import com.patra.catalog.infra.persistence.entity.OrganizationEntity;
import com.patra.catalog.infra.persistence.entity.OrganizationExternalIdEntity;
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

/// DefaultFunderLookupAdapter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("DefaultFunderLookupAdapter")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class DefaultFunderLookupAdapterTest {

  @Mock private OrganizationExternalIdDao externalIdDao;

  @Mock private OrganizationDao organizationDao;

  @Mock private OrganizationExternalIdEntity externalIdEntity;

  @Mock private OrganizationEntity organizationEntity;

  private DefaultFunderLookupAdapter adapter;

  private static final Long ORG_ID = 123456L;
  private static final String FUNDREF_ID = "100000002";
  private static final String ROR_ID = "01cwqze88";
  private static final String FUNDER_NAME = "National Institutes of Health";

  @BeforeEach
  void setUp() {
    adapter = new DefaultFunderLookupAdapter(externalIdDao, organizationDao);
  }

  @Nested
  @DisplayName("findByIdentifier()")
  class FindByIdentifierTest {

    @Test
    @DisplayName("空标识符应该返回 empty")
    void should_return_empty_when_identifier_is_null() {
      // when
      Optional<Long> result = adapter.findByIdentifier(null);

      // then
      assertThat(result).isEmpty();
      verify(externalIdDao, never()).findByIdTypeAndPreferredValue(anyString(), anyString());
    }

    @Test
    @DisplayName("空白标识符应该返回 empty")
    void should_return_empty_when_identifier_is_blank() {
      // when
      Optional<Long> result = adapter.findByIdentifier("  ");

      // then
      assertThat(result).isEmpty();
      verify(externalIdDao, never()).findByIdTypeAndPreferredValue(anyString(), anyString());
    }

    @Test
    @DisplayName("通过 FundRef ID 应该返回机构 ID")
    void should_return_org_id_when_fundref_id_matches() {
      // given
      when(externalIdEntity.getOrgId()).thenReturn(ORG_ID);
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", FUNDREF_ID))
          .thenReturn(Optional.of(externalIdEntity));

      // when
      Optional<Long> result = adapter.findByIdentifier(FUNDREF_ID);

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(ORG_ID);
      // FundRef 匹配成功后不应该查询 ROR
      verify(organizationDao, never()).findByRorId(anyString());
    }

    @Test
    @DisplayName("FundRef 不匹配时应该尝试 ROR ID")
    void should_try_ror_id_when_fundref_not_found() {
      // given
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", ROR_ID))
          .thenReturn(Optional.empty());
      when(organizationEntity.getId()).thenReturn(ORG_ID);
      when(organizationDao.findByRorId(ROR_ID)).thenReturn(Optional.of(organizationEntity));

      // when
      Optional<Long> result = adapter.findByIdentifier(ROR_ID);

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(ORG_ID);
    }

    @Test
    @DisplayName("所有标识符都不匹配时应该返回 empty")
    void should_return_empty_when_all_identifiers_not_found() {
      // given
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", "unknown"))
          .thenReturn(Optional.empty());
      when(organizationDao.findByRorId("unknown")).thenReturn(Optional.empty());

      // when
      Optional<Long> result = adapter.findByIdentifier("unknown");

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该对标识符进行 trim 处理")
    void should_trim_identifier() {
      // given
      when(externalIdEntity.getOrgId()).thenReturn(ORG_ID);
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", FUNDREF_ID))
          .thenReturn(Optional.of(externalIdEntity));

      // when
      Optional<Long> result = adapter.findByIdentifier("  " + FUNDREF_ID + "  ");

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(ORG_ID);
    }
  }

  @Nested
  @DisplayName("findByName()")
  class FindByNameTest {

    @Test
    @DisplayName("空名称应该返回 empty")
    void should_return_empty_when_name_is_null() {
      // when
      Optional<Long> result = adapter.findByName(null);

      // then
      assertThat(result).isEmpty();
      verify(organizationDao, never()).findByDisplayName(anyString());
    }

    @Test
    @DisplayName("空白名称应该返回 empty")
    void should_return_empty_when_name_is_blank() {
      // when
      Optional<Long> result = adapter.findByName("  ");

      // then
      assertThat(result).isEmpty();
      verify(organizationDao, never()).findByDisplayName(anyString());
    }

    @Test
    @DisplayName("精确匹配时应该返回机构 ID")
    void should_return_org_id_when_name_matches() {
      // given
      when(organizationEntity.getId()).thenReturn(ORG_ID);
      when(organizationDao.findByDisplayName(FUNDER_NAME))
          .thenReturn(Optional.of(organizationEntity));

      // when
      Optional<Long> result = adapter.findByName(FUNDER_NAME);

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(ORG_ID);
    }

    @Test
    @DisplayName("名称不匹配时应该返回 empty")
    void should_return_empty_when_name_not_found() {
      // given
      when(organizationDao.findByDisplayName("Unknown Funder")).thenReturn(Optional.empty());

      // when
      Optional<Long> result = adapter.findByName("Unknown Funder");

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该对名称进行 trim 处理")
    void should_trim_name() {
      // given
      when(organizationEntity.getId()).thenReturn(ORG_ID);
      when(organizationDao.findByDisplayName(FUNDER_NAME))
          .thenReturn(Optional.of(organizationEntity));

      // when
      Optional<Long> result = adapter.findByName("  " + FUNDER_NAME + "  ");

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(ORG_ID);
    }
  }

  @Nested
  @DisplayName("findByPriority()")
  class FindByPriorityTest {

    @Test
    @DisplayName("标识符匹配时应该优先返回")
    void should_return_by_identifier_first() {
      // given
      when(externalIdEntity.getOrgId()).thenReturn(ORG_ID);
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", FUNDREF_ID))
          .thenReturn(Optional.of(externalIdEntity));

      // when
      Optional<Long> result = adapter.findByPriority(FUNDREF_ID, FUNDER_NAME);

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(ORG_ID);
      // 标识符匹配成功后不应该查询名称
      verify(organizationDao, never()).findByDisplayName(anyString());
    }

    @Test
    @DisplayName("标识符不匹配时应该尝试名称匹配")
    void should_try_name_when_identifier_not_found() {
      // given
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", FUNDREF_ID))
          .thenReturn(Optional.empty());
      when(organizationDao.findByRorId(FUNDREF_ID)).thenReturn(Optional.empty());
      when(organizationEntity.getId()).thenReturn(ORG_ID);
      when(organizationDao.findByDisplayName(FUNDER_NAME))
          .thenReturn(Optional.of(organizationEntity));

      // when
      Optional<Long> result = adapter.findByPriority(FUNDREF_ID, FUNDER_NAME);

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(ORG_ID);
    }

    @Test
    @DisplayName("所有都不匹配时应该返回 empty")
    void should_return_empty_when_all_not_found() {
      // given
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", FUNDREF_ID))
          .thenReturn(Optional.empty());
      when(organizationDao.findByRorId(FUNDREF_ID)).thenReturn(Optional.empty());
      when(organizationDao.findByDisplayName(FUNDER_NAME)).thenReturn(Optional.empty());

      // when
      Optional<Long> result = adapter.findByPriority(FUNDREF_ID, FUNDER_NAME);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("标识符为 null 时应该直接尝试名称")
    void should_try_name_when_identifier_is_null() {
      // given
      when(organizationEntity.getId()).thenReturn(ORG_ID);
      when(organizationDao.findByDisplayName(FUNDER_NAME))
          .thenReturn(Optional.of(organizationEntity));

      // when
      Optional<Long> result = adapter.findByPriority(null, FUNDER_NAME);

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(ORG_ID);
      verify(externalIdDao, never()).findByIdTypeAndPreferredValue(anyString(), anyString());
    }

    @Test
    @DisplayName("名称为 null 时应该只尝试标识符")
    void should_only_try_identifier_when_name_is_null() {
      // given
      when(externalIdDao.findByIdTypeAndPreferredValue("FUNDREF", FUNDREF_ID))
          .thenReturn(Optional.empty());
      when(organizationDao.findByRorId(FUNDREF_ID)).thenReturn(Optional.empty());

      // when
      Optional<Long> result = adapter.findByPriority(FUNDREF_ID, null);

      // then
      assertThat(result).isEmpty();
      verify(organizationDao, never()).findByDisplayName(anyString());
    }
  }
}
