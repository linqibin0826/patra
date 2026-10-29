package dev.patra.identity.infra.account.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 资料表新列、链接与关注领域两张子表的约束（PAP-95）：绕过应用层直接写库，数据库自己也要拦住。
/// 每个用例只有一条会失败的写入：测试事务在第一次失败后就被 PostgreSQL 标成 aborted。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("资料表约束")
class ProfileSchemaConstraintsIT {

  private static final long USER = 1L;

  private static final String INSERT_LINK =
      "INSERT INTO idn_user_profile_link (id, user_id, position, title, url) VALUES (?, ?, ?, ?, ?)";

  private static final String INSERT_INTEREST =
      "INSERT INTO idn_user_research_interest (id, user_id, position, mesh_ui, mesh_name) "
          + "VALUES (?, ?, ?, ?, ?)";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void should_default_privacy_columns() {
    insertUserWithProfile();

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT profile_visibility, professional_visibility, search_indexing "
                + "FROM idn_user_profile WHERE user_id = ?",
            USER);

    assertThat(row)
        .containsEntry("profile_visibility", "PUBLIC")
        .containsEntry("professional_visibility", "PUBLIC")
        .containsEntry("search_indexing", false);
  }

  @Test
  void should_reject_bio_over_800_bytes() {
    insertUserWithProfile();
    assertViolates("bio", "汉".repeat(267), "ck_idn_user_profile_bio");
  }

  @Test
  void should_reject_bio_with_surrounding_whitespace() {
    insertUserWithProfile();
    assertViolates("bio", " 简介", "ck_idn_user_profile_bio");
  }

  @Test
  void should_reject_bio_with_carriage_return() {
    insertUserWithProfile();
    assertViolates("bio", "a\rb", "ck_idn_user_profile_bio");
  }

  @Test
  void should_reject_unknown_occupation() {
    insertUserWithProfile();
    assertViolates("occupation", "DOCTOR", "ck_idn_user_profile_occupation");
  }

  @Test
  void should_reject_job_title_over_200_bytes() {
    insertUserWithProfile();
    assertViolates("job_title", "汉".repeat(67), "ck_idn_user_profile_job_title");
  }

  @Test
  void should_reject_department_over_400_bytes() {
    insertUserWithProfile();
    assertViolates("department", "汉".repeat(134), "ck_idn_user_profile_department");
  }

  @Test
  void should_reject_upper_case_ror_id() {
    insertUserWithProfile();
    assertThatThrownBy(() -> setOrganization("04JZTAG35", "协和"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_profile_ror_id");
  }

  @Test
  void should_require_snapshot_name_with_ror_id() {
    insertUserWithProfile();
    assertThatThrownBy(() -> setOrganization("04jztag35", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_profile_organization");
  }

  @Test
  void should_reject_free_text_organization_over_800_bytes() {
    insertUserWithProfile();
    assertThatThrownBy(() -> setOrganization(null, "汉".repeat(267)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_profile_organization_name");
  }

  @Test
  void should_allow_long_snapshot_with_ror_id() {
    insertUserWithProfile();
    assertThatCode(() -> setOrganization("04jztag35", "汉".repeat(267))).doesNotThrowAnyException();
  }

  @Test
  void should_reject_unknown_profile_visibility() {
    insertUserWithProfile();
    assertViolates("profile_visibility", "PRIVATE", "ck_idn_user_profile_visibility");
  }

  @Test
  void should_reject_signed_in_professional_visibility() {
    insertUserWithProfile();
    assertViolates(
        "professional_visibility", "SIGNED_IN", "ck_idn_user_profile_professional_visibility");
  }

  @Test
  void should_reject_sixth_link_position() {
    insertUserWithProfile();
    assertThatThrownBy(() -> jdbcTemplate.update(INSERT_LINK, 10L, USER, 5, null, "https://a.org"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_profile_link_position");
  }

  @Test
  void should_reject_non_http_link() {
    insertUserWithProfile();
    assertThatThrownBy(() -> jdbcTemplate.update(INSERT_LINK, 10L, USER, 0, null, "ftp://x"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_profile_link_url");
  }

  @Test
  void should_reject_link_title_with_surrounding_whitespace() {
    insertUserWithProfile();
    assertThatThrownBy(() -> jdbcTemplate.update(INSERT_LINK, 10L, USER, 0, " 主页", "https://a.org"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_profile_link_title");
  }

  @Test
  void should_reject_duplicate_link_url_ignoring_case() {
    insertUserWithProfile();
    jdbcTemplate.update(INSERT_LINK, 10L, USER, 0, null, "https://a.org");
    assertThatThrownBy(() -> jdbcTemplate.update(INSERT_LINK, 11L, USER, 1, null, "HTTPS://A.org"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_idn_user_profile_link_url");
  }

  @Test
  void should_reject_link_of_missing_user() {
    assertThatThrownBy(
            () -> jdbcTemplate.update(INSERT_LINK, 10L, 999_998L, 0, null, "https://a.org"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_idn_user_profile_link_user");
  }

  @Test
  void should_reject_eleventh_interest_position() {
    insertUserWithProfile();
    assertThatThrownBy(
            () -> jdbcTemplate.update(INSERT_INTEREST, 20L, USER, 10, "D003920", "Diabetes"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_research_interest_position");
  }

  @Test
  void should_reject_malformed_mesh_ui() {
    insertUserWithProfile();
    assertThatThrownBy(
            () -> jdbcTemplate.update(INSERT_INTEREST, 20L, USER, 0, "d003920", "Diabetes"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_research_interest_mesh_ui");
  }

  @Test
  void should_reject_duplicate_interest_ui() {
    insertUserWithProfile();
    jdbcTemplate.update(INSERT_INTEREST, 20L, USER, 0, "D003920", "Diabetes");
    assertThatThrownBy(
            () -> jdbcTemplate.update(INSERT_INTEREST, 21L, USER, 1, "D003920", "Diabetes"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_idn_user_research_interest_mesh_ui");
  }

  @Test
  void should_reject_duplicate_interest_position() {
    insertUserWithProfile();
    jdbcTemplate.update(INSERT_INTEREST, 20L, USER, 0, "D003920", "Diabetes");
    assertThatThrownBy(
            () -> jdbcTemplate.update(INSERT_INTEREST, 21L, USER, 0, "D006333", "Heart Failure"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_idn_user_research_interest_position");
  }

  @Test
  void should_reject_interest_of_missing_user() {
    assertThatThrownBy(
            () -> jdbcTemplate.update(INSERT_INTEREST, 20L, 999_998L, 0, "D003920", "Diabetes"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_idn_user_research_interest_user");
  }

  /// 插一个正常状态的用户和他的空资料行。
  private void insertUserWithProfile() {
    jdbcTemplate.update(
        "INSERT INTO idn_user (id, email, username, status) VALUES (?, ?, ?, 'ACTIVE')",
        USER,
        "chen.yu@example.com",
        "chen-yu");
    jdbcTemplate.update("INSERT INTO idn_user_profile (id, user_id) VALUES (?, ?)", 2L, USER);
  }

  /// 把资料行的一列改成给定值，断言撞上给定约束。
  ///
  /// @param column 列名
  /// @param value 值
  /// @param constraint 约束名
  private void assertViolates(String column, Object value, String constraint) {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE idn_user_profile SET " + column + " = ? WHERE user_id = ?",
                    value,
                    USER))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(constraint);
  }

  /// 改资料行的机构两列。
  ///
  /// @param rorId ROR ID
  /// @param name 名称
  private void setOrganization(String rorId, String name) {
    jdbcTemplate.update(
        "UPDATE idn_user_profile SET organization_ror_id = ?, organization_name = ? "
            + "WHERE user_id = ?",
        rorId,
        name,
        USER);
  }
}
