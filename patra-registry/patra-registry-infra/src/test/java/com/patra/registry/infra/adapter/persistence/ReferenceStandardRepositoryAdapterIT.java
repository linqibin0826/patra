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

  @Test
  @DisplayName("应能查询启用的来源标准")
  void shouldFindEnabledStandard() {
    ReferenceStandardEntity entity = new ReferenceStandardEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setStandardCode("ISO_3166_1_ALPHA2");
    entity.setStandardName("ISO 3166-1 alpha-2");
    entity.setDisplayOrder(10);
    entity.setEnabled(true);
    dao.save(entity);

    Optional<ReferenceStandard> result = repository.findByCode("ISO_3166_1_ALPHA2");

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().enabled()).isTrue();
  }

  @Test
  @DisplayName("已软删除的来源标准不应被查询到")
  void shouldIgnoreDeletedStandard() {
    ReferenceStandardEntity entity = new ReferenceStandardEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setStandardCode("NAME_EN");
    entity.setStandardName("English Name");
    entity.setDisplayOrder(20);
    entity.setEnabled(true);
    entity.setDeletedAt(Instant.now());
    dao.save(entity);

    Optional<ReferenceStandard> result = repository.findByCode("NAME_EN");

    assertThat(result).isEmpty();
  }
}
