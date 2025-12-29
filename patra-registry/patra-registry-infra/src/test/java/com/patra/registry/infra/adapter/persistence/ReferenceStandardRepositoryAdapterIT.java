package com.patra.registry.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.registry.domain.model.vo.reference.ReferenceStandard;
import com.patra.registry.infra.adapter.persistence.dao.reference.ReferenceStandardDao;
import com.patra.registry.infra.adapter.persistence.entity.reference.ReferenceStandardEntity;
import com.patra.registry.infra.config.RegistryMySQLContainerInitializer;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// ReferenceStandardRepositoryAdapter 集成测试。
///
/// 验证来源标准的查询与软删除过滤。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = RegistryMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReferenceStandardRepositoryAdapter.class, JpaAuditingConfig.class})
@ComponentScan(basePackages = "com.patra.registry.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("ReferenceStandardRepositoryAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ReferenceStandardRepositoryAdapterIT {

  @Autowired private ReferenceStandardRepositoryAdapter repository;
  @Autowired private ReferenceStandardDao dao;

  @BeforeEach
  void setUp() {
    dao.deleteAllInBatch();
  }

  @Nested
  @DisplayName("findByDictTypeCodeAndStandardCode 测试")
  class FindByDictTypeCodeAndStandardCodeTests {

    @Test
    @DisplayName("应能查询启用的来源标准")
    void shouldFindEnabledStandard() {
      ReferenceStandardEntity entity = createEntity("country", "ISO_3166_1_ALPHA2", true, true);
      dao.save(entity);

      Optional<ReferenceStandard> result =
          repository.findByDictTypeCodeAndStandardCode("country", "ISO_3166_1_ALPHA2");

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().dictTypeCode()).isEqualTo("country");
      assertThat(result.orElseThrow().standardCode()).isEqualTo("ISO_3166_1_ALPHA2");
      assertThat(result.orElseThrow().canonical()).isTrue();
      assertThat(result.orElseThrow().enabled()).isTrue();
    }

    @Test
    @DisplayName("已软删除的来源标准不应被查询到")
    void shouldIgnoreDeletedStandard() {
      ReferenceStandardEntity entity = createEntity("country", "NAME_EN", false, true);
      entity.setDeletedAt(Instant.now());
      dao.save(entity);

      Optional<ReferenceStandard> result =
          repository.findByDictTypeCodeAndStandardCode("country", "NAME_EN");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("不同字典类型的同名标准不应混淆")
    void shouldNotConfuseStandardsFromDifferentDictTypes() {
      dao.save(createEntity("country", "NAME_EN", false, true));
      dao.save(createEntity("language", "NAME_EN", false, true));

      Optional<ReferenceStandard> countryResult =
          repository.findByDictTypeCodeAndStandardCode("country", "NAME_EN");
      Optional<ReferenceStandard> languageResult =
          repository.findByDictTypeCodeAndStandardCode("language", "NAME_EN");

      assertThat(countryResult).isPresent();
      assertThat(countryResult.orElseThrow().dictTypeCode()).isEqualTo("country");

      assertThat(languageResult).isPresent();
      assertThat(languageResult.orElseThrow().dictTypeCode()).isEqualTo("language");
    }
  }

  @Nested
  @DisplayName("findCanonicalByDictTypeCode 测试")
  class FindCanonicalByDictTypeCodeTests {

    @Test
    @DisplayName("应能查询规范标准")
    void shouldFindCanonicalStandard() {
      dao.save(createEntity("country", "ISO_3166_1_ALPHA2", true, true));
      dao.save(createEntity("country", "NAME_EN", false, true));

      Optional<ReferenceStandard> result = repository.findCanonicalByDictTypeCode("country");

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().standardCode()).isEqualTo("ISO_3166_1_ALPHA2");
      assertThat(result.orElseThrow().canonical()).isTrue();
    }

    @Test
    @DisplayName("禁用的规范标准不应被查询到")
    void shouldIgnoreDisabledCanonicalStandard() {
      ReferenceStandardEntity entity = createEntity("country", "ISO_3166_1_ALPHA2", true, false);
      dao.save(entity);

      Optional<ReferenceStandard> result = repository.findCanonicalByDictTypeCode("country");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("不存在规范标准时应返回空")
    void shouldReturnEmptyWhenNoCanonicalStandard() {
      dao.save(createEntity("country", "NAME_EN", false, true));

      Optional<ReferenceStandard> result = repository.findCanonicalByDictTypeCode("country");

      assertThat(result).isEmpty();
    }
  }

  private ReferenceStandardEntity createEntity(
      String dictTypeCode, String standardCode, boolean canonical, boolean enabled) {
    ReferenceStandardEntity entity = new ReferenceStandardEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setDictTypeCode(dictTypeCode);
    entity.setStandardCode(standardCode);
    entity.setStandardName(standardCode + " Name");
    entity.setDisplayOrder(10);
    entity.setCanonical(canonical);
    entity.setEnabled(enabled);
    return entity;
  }
}
