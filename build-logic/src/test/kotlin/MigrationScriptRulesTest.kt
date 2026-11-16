import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MigrationScriptRulesTest {

    @Test
    fun flags_a_username_column_defined_outside_identity() {
        val sql = """
            CREATE TABLE cat_comment
            (
                id       BIGINT      NOT NULL,
                username VARCHAR(30) NOT NULL,
                body     TEXT        NOT NULL
            );
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql))
            .containsExactly(username(4, "cat_comment", "username"))
    }

    @Test
    fun allows_username_in_the_two_identity_tables() {
        val sql = """
            CREATE TABLE idn_user
            (
                id       BIGINT      NOT NULL,
                username VARCHAR(30) NOT NULL
            );
            CREATE TABLE idn_user_former_username
            (
                id       BIGINT      NOT NULL,
                user_id  BIGINT      NOT NULL,
                username VARCHAR(30) NOT NULL
            );
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql)).isEmpty()
    }

    @Test
    fun flags_user_name_as_well() {
        val sql = """
            CREATE TABLE cat_comment
            (
                id        BIGINT NOT NULL,
                user_name TEXT   NOT NULL
            );
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql))
            .containsExactly(username(4, "cat_comment", "user_name"))
    }

    @Test
    fun ignores_comments_but_still_sees_the_column_after_them() {
        val sql = """
            -- CREATE TABLE cat_note (username TEXT);
            CREATE TABLE cat_comment
            (
                id       BIGINT NOT NULL, /* CREATE TABLE cat_memo (username TEXT); */
                -- public handle
                username TEXT   NOT NULL
            );
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql))
            .containsExactly(username(6, "cat_comment", "username"))
    }

    @Test
    fun ignores_string_literals() {
        val sql = """
            CREATE TABLE cat_comment
            (
                id   BIGINT NOT NULL,
                note TEXT   NOT NULL DEFAULT 'it''s, username',
                body TEXT   NOT NULL CHECK (body !~ ');')
            );
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql)).isEmpty()
    }

    @Test
    fun ignores_dollar_quoted_function_bodies() {
        val sql = """
            CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
            BEGIN
              NEW.updated_at = now();
              CREATE TABLE cat_shadow (username TEXT);
              RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;
            CREATE TABLE cat_comment
            (
                id       BIGINT NOT NULL,
                username TEXT   NOT NULL
            );
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql))
            .containsExactly(username(11, "cat_comment", "username"))
    }

    @Test
    fun flags_a_username_column_added_by_alter_table() {
        val sql = """
            ALTER TABLE cat_comment ADD COLUMN IF NOT EXISTS username VARCHAR(30);
            ALTER TABLE cat_comment ADD CONSTRAINT uk_cat_comment_username UNIQUE (username);
            ALTER TABLE idn_user ADD COLUMN username VARCHAR(30);
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql))
            .containsExactly(username(1, "cat_comment", "username"))
    }

    @Test
    fun flags_a_column_renamed_to_username() {
        val sql = """
            ALTER TABLE cat_comment RENAME COLUMN handle TO username;
            ALTER TABLE cat_comment RENAME TO cat_remark;
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql))
            .containsExactly(username(1, "cat_comment", "username"))
    }

    @Test
    fun normalizes_if_not_exists_schema_prefix_and_quoted_identifiers() {
        val sql = """
            CREATE TABLE IF NOT EXISTS public."idn_user" ("username" VARCHAR(30) NOT NULL);
            CREATE TABLE IF NOT EXISTS public."Cat_Comment" ("UserName" VARCHAR(30) NOT NULL);
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql))
            .containsExactly(username(2, "cat_comment", "username"))
    }

    @Test
    fun ignores_longer_column_names_and_username_inside_constraints() {
        val sql = """
            CREATE TABLE cat_comment
            (
                id                  BIGINT         NOT NULL,
                username_changed_at timestamptz(6) NULL,
                handle              VARCHAR(30)    NOT NULL,
                CONSTRAINT ck_cat_comment_handle CHECK (
                    username ~ '^[a-z0-9]+$'
                    AND char_length(handle) BETWEEN 3 AND 30)
            );
            CREATE INDEX idx_cat_comment_handle ON cat_comment (username);
            COMMENT ON COLUMN cat_comment.username IS 'not a definition';
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql)).isEmpty()
    }

    @Test
    fun tolerates_a_truncated_statement() {
        assertThat(MigrationScriptRules.violations("CREATE TABLE")).isEmpty()
        assertThat(MigrationScriptRules.violations("ALTER TABLE")).isEmpty()
    }

    @Test
    fun flags_create_schema() {
        val sql = """
            CREATE TABLE cat_comment (id BIGINT NOT NULL);
            CREATE SCHEMA IF NOT EXISTS reaction;
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql)).containsExactly(schema(2))
    }

    @Test
    fun flags_references_in_create_table() {
        val sql = """
            CREATE TABLE itr_comment
            (
                id         BIGINT NOT NULL,
                subject_id BIGINT NOT NULL REFERENCES itr_subject (id),
                PRIMARY KEY (id)
            );
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql)).containsExactly(foreignKey(4, "itr_comment"))
    }

    @Test
    fun flags_references_in_alter_table_add_constraint() {
        val sql = """
            ALTER TABLE ONLY public.itr_comment
                ADD CONSTRAINT fk_itr_comment_subject FOREIGN KEY (subject_id)
                REFERENCES itr_subject (id) ON DELETE CASCADE;
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql)).containsExactly(foreignKey(3, "itr_comment"))
    }

    @Test
    fun ignores_references_in_comment_strings() {
        val sql = """
            CREATE TABLE itr_comment (id BIGINT NOT NULL, subject_id BIGINT NOT NULL);
            COMMENT ON COLUMN itr_comment.subject_id IS '引用 references itr_subject.id，不建外键';
            -- subject_id REFERENCES itr_subject (id)
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql)).isEmpty()
    }

    @Test
    fun ignores_create_schema_in_dollar_body() {
        val sql = """
            CREATE OR REPLACE FUNCTION make_space() RETURNS void AS $$
            BEGIN
              CREATE SCHEMA scratch;
              CREATE TABLE scratch.note (id BIGINT REFERENCES cat_comment (id));
            END;
            $$ LANGUAGE plpgsql;
        """.trimIndent()
        assertThat(MigrationScriptRules.violations(sql)).isEmpty()
    }

    private fun username(line: Int, table: String, column: String) = MigrationScriptRules.Violation(
        line,
        "表 $table 定义了列 $column：用户名只存 identity 的 idn_user / idn_user_former_username，" +
            "其他表引用用户存 user_id（database.md 第 3 节）",
    )

    private fun schema(line: Int) =
        MigrationScriptRules.Violation(line, "建了 schema：业务表都放 public，禁止 CREATE SCHEMA（database.md 第 1 节）")

    private fun foreignKey(line: Int, table: String) =
        MigrationScriptRules.Violation(line, "表 $table 建了外键（REFERENCES）：不建外键，引用关系由应用保证（database.md 第 2 节）")
}
