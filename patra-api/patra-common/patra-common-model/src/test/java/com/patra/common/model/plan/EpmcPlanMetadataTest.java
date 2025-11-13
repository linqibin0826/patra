package com.patra.common.model.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EpmcPlanMetadata 单元测试
 *
 * <p>测试重点：
 * <ul>
 *   <li>有效构造：totalCount + cursorMark
 *   <li>无 cursor 构造：totalCount only
 *   <li>hasSessionToken() 方法
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@DisplayName("EpmcPlanMetadata 单元测试")
class EpmcPlanMetadataTest {

    @Test
    @DisplayName("应该成功创建包含 cursor mark 的元数据")
    void should_create_metadata_with_cursor_mark() {
        // given
        int totalCount = 5000;
        String cursorMark = "AoE/B4AAAAAAAAAAAAAAAAA=";

        // when
        EpmcPlanMetadata metadata = new EpmcPlanMetadata(totalCount, cursorMark);

        // then
        assertThat(metadata.totalCount()).isEqualTo(totalCount);
        assertThat(metadata.cursorMark()).isEqualTo(cursorMark);
        assertThat(metadata.dataSourceType()).isEqualTo("epmc");
        assertThat(metadata.hasSessionToken()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建不包含 cursor mark 的元数据")
    void should_create_metadata_without_cursor_mark() {
        // given
        int totalCount = 5000;

        // when
        EpmcPlanMetadata metadata = new EpmcPlanMetadata(totalCount, null);

        // then
        assertThat(metadata.totalCount()).isEqualTo(totalCount);
        assertThat(metadata.cursorMark()).isNull();
        assertThat(metadata.dataSourceType()).isEqualTo("epmc");
        assertThat(metadata.hasSessionToken()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建空白 cursor mark 的元数据")
    void should_create_metadata_with_blank_cursor_mark() {
        // given
        int totalCount = 5000;

        // when
        EpmcPlanMetadata metadata = new EpmcPlanMetadata(totalCount, "");

        // then
        assertThat(metadata.totalCount()).isEqualTo(totalCount);
        assertThat(metadata.cursorMark()).isEmpty();
        assertThat(metadata.hasSessionToken()).isFalse();
    }

    @Test
    @DisplayName("当 totalCount 为 0 时应该成功创建")
    void should_create_metadata_when_total_count_is_zero() {
        // given
        int totalCount = 0;

        // when
        EpmcPlanMetadata metadata = new EpmcPlanMetadata(totalCount, null);

        // then
        assertThat(metadata.totalCount()).isZero();
        assertThat(metadata.hasSessionToken()).isFalse();
    }

    @Test
    @DisplayName("hasSessionToken 应该在有非空 cursorMark 时返回 true")
    void should_return_true_when_has_non_blank_cursor_mark() {
        // given
        EpmcPlanMetadata metadata = new EpmcPlanMetadata(5000, "AoE/B4AAAAAAAAAAAAAAAAA=");

        // when
        boolean hasToken = metadata.hasSessionToken();

        // then
        assertThat(hasToken).isTrue();
    }

    @Test
    @DisplayName("hasSessionToken 应该在 cursorMark 为 null 时返回 false")
    void should_return_false_when_cursor_mark_is_null() {
        // given
        EpmcPlanMetadata metadata = new EpmcPlanMetadata(5000, null);

        // when
        boolean hasToken = metadata.hasSessionToken();

        // then
        assertThat(hasToken).isFalse();
    }

    @Test
    @DisplayName("hasSessionToken 应该在 cursorMark 为空白时返回 false")
    void should_return_false_when_cursor_mark_is_blank() {
        // given
        EpmcPlanMetadata metadata = new EpmcPlanMetadata(5000, "  ");

        // when
        boolean hasToken = metadata.hasSessionToken();

        // then
        assertThat(hasToken).isFalse();
    }

    @Test
    @DisplayName("toString 应该返回包含关键信息的字符串")
    void should_return_informative_string_representation() {
        // given
        EpmcPlanMetadata metadata = new EpmcPlanMetadata(5000, "AoE/B4AAAAAAAAAAAAAAAAA=");

        // when
        String result = metadata.toString();

        // then
        assertThat(result).contains("EpmcPlanMetadata");
        assertThat(result).contains("totalCount=5000");
        assertThat(result).contains("hasCursorMark=true");
    }

    @Test
    @DisplayName("应该正确处理只包含空格的 cursor mark")
    void should_handle_whitespace_only_cursor_mark() {
        // given
        int totalCount = 5000;
        String cursorMark = "   ";

        // when
        EpmcPlanMetadata metadata = new EpmcPlanMetadata(totalCount, cursorMark);

        // then
        assertThat(metadata.cursorMark()).isEqualTo(cursorMark);
        assertThat(metadata.hasSessionToken()).isFalse();
    }
}
