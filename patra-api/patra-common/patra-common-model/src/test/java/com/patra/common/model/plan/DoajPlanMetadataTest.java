package com.patra.common.model.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DoajPlanMetadata 单元测试
 *
 * <p>测试重点：
 * <ul>
 *   <li>有效构造：totalCount + scrollId + pageSize
 *   <li>无 scroll 构造：totalCount only
 *   <li>业务约束：pageSize 必须 > 0
 *   <li>hasSessionToken() 方法
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@DisplayName("DoajPlanMetadata 单元测试")
class DoajPlanMetadataTest {

    @Test
    @DisplayName("应该成功创建包含 scroll ID 的元数据")
    void should_create_metadata_with_scroll_id() {
        // given
        int totalCount = 2000;
        String scrollId = "DXF1ZXJ5QW5kRmV0Y2gBAAAAAAAAAbMWZ1hOaW10cxRlU3NJLU44U0EtOA==";
        int pageSize = 100;

        // when
        DoajPlanMetadata metadata = new DoajPlanMetadata(totalCount, scrollId, pageSize);

        // then
        assertThat(metadata.totalCount()).isEqualTo(totalCount);
        assertThat(metadata.scrollId()).isEqualTo(scrollId);
        assertThat(metadata.pageSize()).isEqualTo(pageSize);
        assertThat(metadata.dataSourceType()).isEqualTo("doaj");
        assertThat(metadata.hasSessionToken()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建不包含 scroll ID 的元数据")
    void should_create_metadata_without_scroll_id() {
        // given
        int totalCount = 2000;
        int pageSize = 100;

        // when
        DoajPlanMetadata metadata = new DoajPlanMetadata(totalCount, null, pageSize);

        // then
        assertThat(metadata.totalCount()).isEqualTo(totalCount);
        assertThat(metadata.scrollId()).isNull();
        assertThat(metadata.pageSize()).isEqualTo(pageSize);
        assertThat(metadata.dataSourceType()).isEqualTo("doaj");
        assertThat(metadata.hasSessionToken()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建空白 scroll ID 的元数据")
    void should_create_metadata_with_blank_scroll_id() {
        // given
        int totalCount = 2000;
        int pageSize = 100;

        // when
        DoajPlanMetadata metadata = new DoajPlanMetadata(totalCount, "", pageSize);

        // then
        assertThat(metadata.totalCount()).isEqualTo(totalCount);
        assertThat(metadata.scrollId()).isEmpty();
        assertThat(metadata.pageSize()).isEqualTo(pageSize);
        assertThat(metadata.hasSessionToken()).isFalse();
    }

    @Test
    @DisplayName("当 pageSize 为 0 时应该抛出异常")
    void should_throw_exception_when_page_size_is_zero() {
        // given
        int totalCount = 2000;
        String scrollId = "scroll-id-123";
        int pageSize = 0;

        // when & then
        assertThatThrownBy(() -> new DoajPlanMetadata(totalCount, scrollId, pageSize))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("pageSize 必须 > 0");
    }

    @Test
    @DisplayName("当 pageSize 为负数时应该抛出异常")
    void should_throw_exception_when_page_size_is_negative() {
        // given
        int totalCount = 2000;
        String scrollId = "scroll-id-123";
        int pageSize = -100;

        // when & then
        assertThatThrownBy(() -> new DoajPlanMetadata(totalCount, scrollId, pageSize))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("pageSize 必须 > 0");
    }

    @Test
    @DisplayName("当 totalCount 为 0 时应该成功创建")
    void should_create_metadata_when_total_count_is_zero() {
        // given
        int totalCount = 0;
        int pageSize = 100;

        // when
        DoajPlanMetadata metadata = new DoajPlanMetadata(totalCount, null, pageSize);

        // then
        assertThat(metadata.totalCount()).isZero();
        assertThat(metadata.pageSize()).isEqualTo(pageSize);
        assertThat(metadata.hasSessionToken()).isFalse();
    }

    @Test
    @DisplayName("应该支持各种有效的 pageSize 值")
    void should_support_various_valid_page_sizes() {
        // given & when
        DoajPlanMetadata metadata1 = new DoajPlanMetadata(2000, null, 1);
        DoajPlanMetadata metadata2 = new DoajPlanMetadata(2000, null, 50);
        DoajPlanMetadata metadata3 = new DoajPlanMetadata(2000, null, 100);
        DoajPlanMetadata metadata4 = new DoajPlanMetadata(2000, null, 1000);

        // then
        assertThat(metadata1.pageSize()).isEqualTo(1);
        assertThat(metadata2.pageSize()).isEqualTo(50);
        assertThat(metadata3.pageSize()).isEqualTo(100);
        assertThat(metadata4.pageSize()).isEqualTo(1000);
    }

    @Test
    @DisplayName("hasSessionToken 应该在有非空 scrollId 时返回 true")
    void should_return_true_when_has_non_blank_scroll_id() {
        // given
        DoajPlanMetadata metadata = new DoajPlanMetadata(
            2000,
            "DXF1ZXJ5QW5kRmV0Y2gBAAAAAAAAAbMWZ1hOaW10cxRlU3NJLU44U0EtOA==",
            100
        );

        // when
        boolean hasToken = metadata.hasSessionToken();

        // then
        assertThat(hasToken).isTrue();
    }

    @Test
    @DisplayName("hasSessionToken 应该在 scrollId 为 null 时返回 false")
    void should_return_false_when_scroll_id_is_null() {
        // given
        DoajPlanMetadata metadata = new DoajPlanMetadata(2000, null, 100);

        // when
        boolean hasToken = metadata.hasSessionToken();

        // then
        assertThat(hasToken).isFalse();
    }

    @Test
    @DisplayName("hasSessionToken 应该在 scrollId 为空白时返回 false")
    void should_return_false_when_scroll_id_is_blank() {
        // given
        DoajPlanMetadata metadata = new DoajPlanMetadata(2000, "  ", 100);

        // when
        boolean hasToken = metadata.hasSessionToken();

        // then
        assertThat(hasToken).isFalse();
    }

    @Test
    @DisplayName("toString 应该返回包含关键信息的字符串")
    void should_return_informative_string_representation() {
        // given
        DoajPlanMetadata metadata = new DoajPlanMetadata(
            2000,
            "DXF1ZXJ5QW5kRmV0Y2gBAAAAAAAAAbMWZ1hOaW10cxRlU3NJLU44U0EtOA==",
            100
        );

        // when
        String result = metadata.toString();

        // then
        assertThat(result).contains("DoajPlanMetadata");
        assertThat(result).contains("totalCount=2000");
        assertThat(result).contains("pageSize=100");
        assertThat(result).contains("hasScrollId=true");
    }

    @Test
    @DisplayName("应该正确处理只包含空格的 scroll ID")
    void should_handle_whitespace_only_scroll_id() {
        // given
        int totalCount = 2000;
        String scrollId = "   ";
        int pageSize = 100;

        // when
        DoajPlanMetadata metadata = new DoajPlanMetadata(totalCount, scrollId, pageSize);

        // then
        assertThat(metadata.scrollId()).isEqualTo(scrollId);
        assertThat(metadata.hasSessionToken()).isFalse();
    }
}
