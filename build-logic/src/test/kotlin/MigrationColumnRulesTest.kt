import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MigrationColumnRulesTest {

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
        assertThat(MigrationColumnRules.violations(sql))
            .containsExactly(MigrationColumnRules.Violation(line = 4, table = "cat_comment", column = "username"))
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
        assertThat(MigrationColumnRules.violations(sql)).isEmpty()
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
        assertThat(MigrationColumnRules.violations(sql))
            .containsExactly(MigrationColumnRules.Violation(line = 4, table = "cat_comment", column = "user_name"))
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
        assertThat(MigrationColumnRules.violations(sql))
            .containsExactly(MigrationColumnRules.Violation(line = 6, table = "cat_comment", column = "username"))
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
        assertThat(MigrationColumnRules.violations(sql)).isEmpty()
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
        assertThat(MigrationColumnRules.violations(sql))
            .containsExactly(MigrationColumnRules.Violation(line = 11, table = "cat_comment", column = "username"))
    }

    @Test
    fun flags_a_username_column_added_by_alter_table() {
        val sql = """
            ALTER TABLE cat_comment ADD COLUMN IF NOT EXISTS username VARCHAR(30);
            ALTER TABLE cat_comment ADD CONSTRAINT uk_cat_comment_username UNIQUE (username);
            ALTER TABLE idn_user ADD COLUMN username VARCHAR(30);
        """.trimIndent()
        assertThat(MigrationColumnRules.violations(sql))
            .containsExactly(MigrationColumnRules.Violation(line = 1, table = "cat_comment", column = "username"))
    }

    @Test
    fun flags_a_column_renamed_to_username() {
        val sql = """
            ALTER TABLE cat_comment RENAME COLUMN handle TO username;
            ALTER TABLE cat_comment RENAME TO cat_remark;
        """.trimIndent()
        assertThat(MigrationColumnRules.violations(sql))
            .containsExactly(MigrationColumnRules.Violation(line = 1, table = "cat_comment", column = "username"))
    }

    @Test
    fun normalizes_if_not_exists_schema_prefix_and_quoted_identifiers() {
        val sql = """
            CREATE TABLE IF NOT EXISTS public."idn_user" ("username" VARCHAR(30) NOT NULL);
            CREATE TABLE IF NOT EXISTS public."Cat_Comment" ("UserName" VARCHAR(30) NOT NULL);
        """.trimIndent()
        assertThat(MigrationColumnRules.violations(sql))
            .containsExactly(MigrationColumnRules.Violation(line = 2, table = "cat_comment", column = "username"))
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
        assertThat(MigrationColumnRules.violations(sql)).isEmpty()
    }

    @Test
    fun tolerates_a_truncated_statement() {
        assertThat(MigrationColumnRules.violations("CREATE TABLE")).isEmpty()
        assertThat(MigrationColumnRules.violations("ALTER TABLE")).isEmpty()
    }
}
