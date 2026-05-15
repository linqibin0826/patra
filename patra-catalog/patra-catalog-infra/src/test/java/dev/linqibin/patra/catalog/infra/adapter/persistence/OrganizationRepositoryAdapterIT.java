package dev.linqibin.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.patra.catalog.domain.model.aggregate.OrganizationAggregate;
import dev.linqibin.patra.catalog.domain.model.enums.ExternalIdType;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationNameType;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationRelationType;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationStatus;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationType;
import dev.linqibin.patra.catalog.domain.model.vo.organization.AdminInfo;
import dev.linqibin.patra.catalog.domain.model.vo.organization.ExternalId;
import dev.linqibin.patra.catalog.domain.model.vo.organization.GeoLocation;
import dev.linqibin.patra.catalog.domain.model.vo.organization.OrganizationId;
import dev.linqibin.patra.catalog.domain.model.vo.organization.OrganizationLink;
import dev.linqibin.patra.catalog.domain.model.vo.organization.OrganizationName;
import dev.linqibin.patra.catalog.domain.model.vo.organization.OrganizationRelation;
import dev.linqibin.patra.catalog.domain.model.vo.organization.RorId;
import dev.linqibin.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationExternalIdDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationLocationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationNameDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationRelationDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.OrganizationExternalIdEntity;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// OrganizationRepositoryAdapter 集成测试（JPA）。
///
/// 使用 Testcontainers + MySQL 8 验证 Organization 聚合根持久化逻辑。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  OrganizationRepositoryAdapter.class,
  JpaAuditingConfig.class,
  JacksonAutoConfiguration.class
})
@ComponentScan(basePackages = "dev.linqibin.patra.catalog.infra.persistence.converter")
@ActiveProfiles("test")
@DisplayName("OrganizationRepositoryAdapter 集成测试（JPA）")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class OrganizationRepositoryAdapterIT {

  @Autowired private OrganizationRepositoryAdapter repository;

  @Autowired private OrganizationDao organizationDao;
  @Autowired private OrganizationNameDao nameDao;
  @Autowired private OrganizationExternalIdDao externalIdDao;
  @Autowired private OrganizationRelationDao relationDao;
  @Autowired private OrganizationLocationDao locationDao;
  @Autowired private EntityManager entityManager;

  @Nested
  @DisplayName("hasAnyData() 测试")
  class HasAnyDataTests {

    @Test
    @DisplayName("空表 - 应该返回 false")
    void hasAnyData_emptyTable_shouldReturnFalse() {
      assertThat(organizationDao.count()).isEqualTo(0);
      assertThat(repository.hasAnyData()).isFalse();
    }

    @Test
    @DisplayName("有数据 - 应该返回 true")
    void hasAnyData_withData_shouldReturnTrue() {
      OrganizationAggregate organization =
          createOrganizationAggregate("03vek6s52", "Harvard University");
      repository.insertAll(List.of(organization));

      assertThat(repository.hasAnyData()).isTrue();
    }
  }

  @Nested
  @DisplayName("insertAll() 测试")
  class InsertAllTests {

    @Test
    @DisplayName("应正确插入机构及其子表数据")
    void insertAll_shouldInsertOrganizationWithChildren() {
      OrganizationAggregate organization =
          createOrganizationAggregate("03vek6s52", "Harvard University");

      repository.insertAll(List.of(organization));

      assertThat(organizationDao.count()).isEqualTo(1);
      assertThat(nameDao.count()).isEqualTo(1);
      assertThat(externalIdDao.count()).isEqualTo(1);
      assertThat(relationDao.count()).isEqualTo(1);
      assertThat(locationDao.count()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("updateBatch() 测试")
  class UpdateBatchTests {

    @Test
    @DisplayName("同类型 ExternalId 替换时应保留 ID 并更新值")
    void updateBatch_replaceExternalId_shouldKeepId() {
      String rorId = "03vek6s52";
      OrganizationAggregate organization = createOrganizationAggregate(rorId, "Harvard University");
      repository.insertAll(List.of(organization));

      Long orgId = organizationDao.findByRorId(RorId.fromId(rorId).getId()).orElseThrow().getId();
      OrganizationExternalIdEntity before = externalIdDao.findAllByOrgId(orgId).getFirst();
      Long beforeId = before.getId();

      // 清除 Session 缓存，避免 NonUniqueObjectException
      entityManager.clear();

      OrganizationAggregate loaded = repository.findByRorId(RorId.of(rorId)).orElseThrow();
      loaded.addExternalId(ExternalId.create(ExternalIdType.GRID, "grid.updated.1"));
      repository.updateBatch(List.of(loaded));

      List<OrganizationExternalIdEntity> updated = externalIdDao.findAllByOrgId(orgId);
      assertThat(updated).hasSize(1);
      assertThat(updated.getFirst().getId()).isEqualTo(beforeId);
      assertThat(updated.getFirst().getPreferredValue()).isEqualTo("grid.updated.1");
    }
  }

  @Nested
  @DisplayName("save() 测试")
  class SaveTests {

    @Test
    @DisplayName("应正确保存单个机构并返回带 ID 的聚合")
    void save_shouldSaveAndReturnAggregateWithId() {
      OrganizationAggregate organization =
          createOrganizationAggregate("0save1234", "Save Test Org");

      OrganizationAggregate saved = repository.save(organization);

      assertThat(saved).isNotNull();
      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getRorId()).isEqualTo(RorId.of("0save1234"));
      assertThat(saved.getDisplayName()).isEqualTo("Save Test Org");
    }

    @Test
    @DisplayName("save() 应正确加载子表数据")
    void save_shouldLoadChildEntities() {
      OrganizationAggregate organization =
          createOrganizationAggregate("0savechil", "Save Child Test");

      OrganizationAggregate saved = repository.save(organization);

      assertThat(saved.getNames()).hasSize(1);
      assertThat(saved.getExternalIds()).hasSize(1);
      assertThat(saved.getRelations()).hasSize(1);
      assertThat(saved.getLocations()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("findById() 测试")
  class FindByIdTests {

    @Test
    @DisplayName("应按 ID 查询机构")
    void findById_shouldReturnOrganization() {
      OrganizationAggregate organization =
          createOrganizationAggregate("0findbyid", "FindById Test");
      repository.insertAll(List.of(organization));
      Long orgId = organizationDao.findByRorId("0findbyid").orElseThrow().getId();

      var result = repository.findById(OrganizationId.of(orgId));

      assertThat(result).isPresent();
      assertThat(result.get().getRorId()).isEqualTo(RorId.of("0findbyid"));
    }

    @Test
    @DisplayName("ID 不存在时应返回 empty")
    void findById_notFound_shouldReturnEmpty() {
      var result = repository.findById(OrganizationId.of(99999999L));

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("existsByRorId() 测试")
  class ExistsByRorIdTests {

    @Test
    @DisplayName("存在的 ROR ID 应返回 true")
    void existsByRorId_exists_shouldReturnTrue() {
      OrganizationAggregate organization = createOrganizationAggregate("0existror", "Exists Test");
      repository.insertAll(List.of(organization));

      assertThat(repository.existsByRorId(RorId.of("0existror"))).isTrue();
    }

    @Test
    @DisplayName("不存在的 ROR ID 应返回 false")
    void existsByRorId_notExists_shouldReturnFalse() {
      assertThat(repository.existsByRorId(RorId.of("0notexist"))).isFalse();
    }
  }

  @Nested
  @DisplayName("count() 测试")
  class CountTests {

    @Test
    @DisplayName("空表应返回 0")
    void count_emptyTable_shouldReturnZero() {
      assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("应返回正确的记录数")
    void count_withData_shouldReturnCorrectCount() {
      OrganizationAggregate org1 = createOrganizationAggregate("0count001", "Count Test 1");
      OrganizationAggregate org2 = createOrganizationAggregate("0count002", "Count Test 2");
      repository.insertAll(List.of(org1, org2));

      assertThat(repository.count()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("countByStatus() 测试")
  class CountByStatusTests {

    @Test
    @DisplayName("应按状态分组统计")
    void countByStatus_shouldGroupByStatus() {
      OrganizationAggregate active1 = createOrganizationAggregate("0status01", "Active 1");
      OrganizationAggregate active2 = createOrganizationAggregate("0status02", "Active 2");
      OrganizationAggregate inactive =
          OrganizationAggregate.fromRor(
                  RorId.of("0status03"), "Inactive", OrganizationStatus.INACTIVE)
              .withTypes(Set.of(OrganizationType.EDUCATION));
      repository.insertAll(List.of(active1, active2, inactive));

      var result = repository.countByStatus();

      assertThat(result).containsEntry("active", 2L);
      assertThat(result).containsEntry("inactive", 1L);
    }
  }

  private OrganizationAggregate createOrganizationAggregate(String rorId, String displayName) {
    OrganizationAggregate aggregate =
        OrganizationAggregate.fromRor(RorId.of(rorId), displayName, OrganizationStatus.ACTIVE)
            .withEstablished(1636)
            .withAdminInfo(
                AdminInfo.of(LocalDate.of(2019, 1, 20), "1.0", LocalDate.of(2024, 12, 11), "2.1"))
            .withTypes(Set.of(OrganizationType.EDUCATION))
            .withDomains(List.of("harvard.edu"))
            .withLinks(List.of(OrganizationLink.website("https://www.harvard.edu")));

    aggregate.addName(
        OrganizationName.create(displayName, Set.of(OrganizationNameType.ROR_DISPLAY), "en"));
    aggregate.addExternalId(ExternalId.create(ExternalIdType.GRID, "grid.12345.6"));
    aggregate.addRelation(
        OrganizationRelation.create(
            OrganizationRelationType.PARENT,
            RorId.of("https://ror.org/04hjwq123"),
            "Parent Organization"));
    aggregate.addLocation(
        GeoLocation.builder()
            .geonamesId(4931972)
            .continentCode("NA")
            .continentName("North America")
            .countryCode("US")
            .countryName("United States")
            .subdivisionCode("MA")
            .subdivisionName("Massachusetts")
            .cityName("Cambridge")
            .latitude(new BigDecimal("42.3736"))
            .longitude(new BigDecimal("-71.1097"))
            .build());

    return aggregate;
  }
}
