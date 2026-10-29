package dev.patra.registry.infra.provenance.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.common.ProvenanceCode;
import dev.patra.registry.domain.provenance.exception.ProvenanceNotFoundException;
import dev.patra.registry.infra.config.RegistryITPostgreSQLContainerInitializer;
import dev.patra.registry.infra.provenance.persistence.dao.ProvenanceDao;
import dev.patra.registry.infra.provenance.persistence.entity.ProvenanceEntity;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// ProvenanceReadAdapter 集成测试。
///
/// 使用 TestContainers + PostgreSQL 17 验证按数据源代码解析数据源 ID。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = RegistryITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({
  ProvenanceReadAdapter.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class
})
@ActiveProfiles("test")
@DisplayName("ProvenanceReadAdapter 集成测试")
class ProvenanceReadAdapterIT {

  @Autowired private ProvenanceReadAdapter readAdapter;

  @Autowired private ProvenanceDao provenanceDao;

  /// 清理数据源表，保证每个用例从空表开始。
  @BeforeEach
  void setUp() {
    provenanceDao.deleteAllInBatch();
  }

  /// 已存在的数据源代码应解析为对应的数据源 ID。
  @Test
  void should_return_id_for_existing_code() {
    Long provenanceId = insertProvenance("PUBMED");

    long resolved = readAdapter.requireIdByCode(ProvenanceCode.PUBMED);

    assertThat(resolved).isEqualTo(provenanceId);
  }

  /// 不存在的数据源代码应抛出 ProvenanceNotFoundException。
  @Test
  void should_throw_provenance_not_found_for_unknown_code() {
    insertProvenance("PUBMED");

    assertThatThrownBy(() -> readAdapter.requireIdByCode(ProvenanceCode.CROSSREF))
        .isInstanceOf(ProvenanceNotFoundException.class)
        .hasMessageContaining("CROSSREF");
  }

  /// 插入一条激活的数据源记录。
  ///
  /// @param code 数据源代码
  /// @return 插入记录的 ID
  private Long insertProvenance(String code) {
    ProvenanceEntity provenance = new ProvenanceEntity();
    provenance.setId(SnowflakeIdGenerator.getId());
    provenance.setProvenanceCode(code);
    provenance.setProvenanceName("Test Provenance - " + code);
    provenance.setTimezoneDefault("UTC");
    provenance.setIsActive(true);
    provenance.setLifecycleStatusCode("ACTIVE");
    provenanceDao.saveAndFlush(provenance);
    return provenance.getId();
  }
}
