package dev.patra.registry.infra.dictionary.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.registry.domain.dictionary.model.vo.DictionaryItem;
import dev.patra.registry.domain.dictionary.model.vo.DictionaryType;
import dev.patra.registry.infra.config.RegistryITPostgreSQLContainerInitializer;
import dev.patra.registry.infra.dictionary.persistence.mapper.DictionaryJpaMapper;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// Flyway 字典种子的回归测试。
///
/// catalog 导入期刊时按 `country / NAME_EN` 解析国家、按 `language / ISO_639_3` 解析语言。
/// 种子里缺了这些参考标准时，registry 报标准不存在，catalog 只打 warn、字段静默变空，
/// 其余测试都自己造数据或 mock，拦不住。这里不造数据，只读 Flyway 灌入的种子；
/// `test` profile 把 Flyway 限在 V1（跳过种子），本类覆盖为执行到最新版本。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest(properties = "spring.flyway.target=latest")
@ContextConfiguration(initializers = RegistryITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({
  ReferenceStandardRepositoryAdapter.class,
  DictionaryRepositoryAdapter.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class
})
@ComponentScan(basePackageClasses = DictionaryJpaMapper.class)
@ActiveProfiles("test")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
@DisplayName("字典种子回归测试")
class DictionarySeedIT {

  @Autowired private ReferenceStandardRepositoryAdapter standards;
  @Autowired private DictionaryRepositoryAdapter dictionaries;

  /// catalog 用到的参考标准都应由种子灌入并启用（停用的标准解析时会被拒绝），国家的 ISO 代码是规范标准。
  @Test
  void should_seed_reference_standards_used_by_catalog() {
    assertThat(standards.findByDictTypeCodeAndStandardCode("country", "ISO_3166_1_ALPHA2"))
        .hasValueSatisfying(
            standard -> {
              assertThat(standard.canonical()).isTrue();
              assertThat(standard.enabled()).isTrue();
            });
    assertThat(standards.findByDictTypeCodeAndStandardCode("country", "NAME_EN"))
        .hasValueSatisfying(standard -> assertThat(standard.enabled()).isTrue());
    assertThat(standards.findByDictTypeCodeAndStandardCode("language", "ISO_639_3"))
        .hasValueSatisfying(standard -> assertThat(standard.enabled()).isTrue());
  }

  /// 英文国家名应能经 `name_en` 别名解析成 ISO 3166-1 alpha-2 代码。
  @Test
  void should_resolve_english_country_name_to_iso_code() {
    DictionaryType country = dictionaries.findTypeByCode("country").orElseThrow();

    Map<String, DictionaryItem> items =
        dictionaries.findItemsByAliases(country.id(), "name_en", Set.of("China"));

    assertThat(items).containsKey("China");
    assertThat(items.get("China").itemCode()).isEqualTo("CN");
  }
}
