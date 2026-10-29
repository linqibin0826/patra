package dev.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `MeshNameReadAdapter` 集成测试：主题词、限定词按名称查 UI，键为输入名称，未命中不出现。
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MeshNameReadAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("MeshNameReadAdapter MeSH 名称读端集成测试")
class MeshNameReadAdapterIT {

  @Autowired private MeshNameReadAdapter adapter;
  @Autowired private JdbcTemplate jdbc;

  @Nested
  @DisplayName("主题词名称 → UI")
  class DescriptorTests {

    @Test
    @DisplayName("空集合返回空 Map")
    void should_return_empty_map_when_input_is_empty() {
      assertThat(adapter.findDescriptorUisByNameIn(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("null 返回空 Map")
    void should_return_empty_map_when_input_is_null() {
      assertThat(adapter.findDescriptorUisByNameIn(null)).isEmpty();
    }

    @Test
    @DisplayName("单个命中")
    void should_map_single_name() {
      saveDescriptor("D000001", "Cardiovascular Diseases");

      assertThat(adapter.findDescriptorUisByNameIn(Set.of("Cardiovascular Diseases")))
          .containsExactlyEntriesOf(Map.of("Cardiovascular Diseases", "D000001"));
    }

    @Test
    @DisplayName("多个命中")
    void should_map_every_name_when_all_hit() {
      saveDescriptor("D000001", "Cardiovascular Diseases");
      saveDescriptor("D000002", "Neoplasms");
      saveDescriptor("D000003", "Aspirin");

      assertThat(
              adapter.findDescriptorUisByNameIn(
                  Set.of("Cardiovascular Diseases", "Neoplasms", "Aspirin")))
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  "Cardiovascular Diseases",
                  "D000001",
                  "Neoplasms",
                  "D000002",
                  "Aspirin",
                  "D000003"));
    }

    @Test
    @DisplayName("部分命中：只返回存在的名称")
    void should_leave_out_missing_names() {
      saveDescriptor("D000001", "Cardiovascular Diseases");

      assertThat(
              adapter.findDescriptorUisByNameIn(
                  Set.of("Cardiovascular Diseases", "NonExistent 1", "NonExistent 2")))
          .containsExactlyEntriesOf(Map.of("Cardiovascular Diseases", "D000001"));
    }

    @Test
    @DisplayName("全部未命中返回空 Map")
    void should_return_empty_map_when_nothing_hits() {
      saveDescriptor("D000001", "Cardiovascular Diseases");

      assertThat(adapter.findDescriptorUisByNameIn(Set.of("NonExistent 1", "NonExistent 2")))
          .isEmpty();
    }

    @Test
    @DisplayName("名称区分大小写")
    void should_match_names_case_sensitively() {
      saveDescriptor("D000001", "Cardiovascular Diseases");

      assertThat(
              adapter.findDescriptorUisByNameIn(
                  Set.of(
                      "cardiovascular diseases",
                      "CARDIOVASCULAR DISEASES",
                      "Cardiovascular Diseases")))
          .containsExactlyEntriesOf(Map.of("Cardiovascular Diseases", "D000001"));
    }
  }

  @Nested
  @DisplayName("限定词名称 → UI")
  class QualifierTests {

    @Test
    @DisplayName("空集合返回空 Map")
    void should_return_empty_map_when_input_is_empty() {
      assertThat(adapter.findQualifierUisByNameIn(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("null 返回空 Map")
    void should_return_empty_map_when_input_is_null() {
      assertThat(adapter.findQualifierUisByNameIn(null)).isEmpty();
    }

    @Test
    @DisplayName("单个命中")
    void should_map_single_name() {
      saveQualifier("Q000001", "immunology");

      assertThat(adapter.findQualifierUisByNameIn(Set.of("immunology")))
          .containsExactlyEntriesOf(Map.of("immunology", "Q000001"));
    }

    @Test
    @DisplayName("多个命中")
    void should_map_every_name_when_all_hit() {
      saveQualifier("Q000001", "immunology");
      saveQualifier("Q000002", "genetics");
      saveQualifier("Q000003", "diagnosis");

      assertThat(adapter.findQualifierUisByNameIn(Set.of("immunology", "genetics", "diagnosis")))
          .containsExactlyInAnyOrderEntriesOf(
              Map.of("immunology", "Q000001", "genetics", "Q000002", "diagnosis", "Q000003"));
    }

    @Test
    @DisplayName("部分命中：只返回存在的名称")
    void should_leave_out_missing_names() {
      saveQualifier("Q000001", "immunology");

      assertThat(
              adapter.findQualifierUisByNameIn(
                  Set.of("immunology", "nonexistent", "another_missing")))
          .containsExactlyEntriesOf(Map.of("immunology", "Q000001"));
    }

    @Test
    @DisplayName("全部未命中返回空 Map")
    void should_return_empty_map_when_nothing_hits() {
      saveQualifier("Q000001", "immunology");

      assertThat(adapter.findQualifierUisByNameIn(Set.of("nonexistent_1", "nonexistent_2")))
          .isEmpty();
    }

    @Test
    @DisplayName("同名多条只取一条")
    void should_keep_one_ui_when_name_repeats() {
      saveQualifier("Q000001", "immunology");
      saveQualifier("Q000099", "immunology");

      Map<String, String> result = adapter.findQualifierUisByNameIn(Set.of("immunology"));

      assertThat(result).containsOnlyKeys("immunology");
      assertThat(result.get("immunology")).isIn("Q000001", "Q000099");
    }
  }

  /// 写一条主题词。
  ///
  /// @param ui UI
  /// @param name 名称
  private void saveDescriptor(String ui, String name) {
    jdbc.update(
        "INSERT INTO cat_mesh_descriptor (id, ui, name) VALUES (?, ?, ?)",
        SnowflakeIdGenerator.getId(),
        ui,
        name);
  }

  /// 写一条限定词。
  ///
  /// @param ui UI
  /// @param name 名称
  private void saveQualifier(String ui, String name) {
    jdbc.update(
        "INSERT INTO cat_mesh_qualifier (id, ui, name) VALUES (?, ?, ?)",
        SnowflakeIdGenerator.getId(),
        ui,
        name);
  }
}
