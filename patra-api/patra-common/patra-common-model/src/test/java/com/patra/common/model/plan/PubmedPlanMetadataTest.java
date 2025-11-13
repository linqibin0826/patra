package com.patra.common.model.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PubmedPlanMetadata 单元测试
 *
 * <p>测试重点：
 * <ul>
 *   <li>有效构造：totalCount + webEnv + queryKey
 *   <li>无 session token 构造：totalCount only
 *   <li>业务约束：webEnv 和 queryKey 必须同时存在或同时为空
 *   <li>hasSessionToken() 方法
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@DisplayName("PubmedPlanMetadata 单元测试")
class PubmedPlanMetadataTest {

    @Test
    @DisplayName("应该成功创建包含 session token 的元数据")
    void should_create_metadata_with_session_token() {
        // given
        int totalCount = 1000;
        String webEnv = "MCID_674c8b5a5e8a9b1234567890";
        String queryKey = "1";

        // when
        PubmedPlanMetadata metadata = new PubmedPlanMetadata(totalCount, webEnv, queryKey);

        // then
        assertThat(metadata.totalCount()).isEqualTo(totalCount);
        assertThat(metadata.webEnv()).isEqualTo(webEnv);
        assertThat(metadata.queryKey()).isEqualTo(queryKey);
        assertThat(metadata.dataSourceType()).isEqualTo("pubmed");
        assertThat(metadata.hasSessionToken()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建不包含 session token 的元数据")
    void should_create_metadata_without_session_token() {
        // given
        int totalCount = 1000;

        // when
        PubmedPlanMetadata metadata = new PubmedPlanMetadata(totalCount, null, null);

        // then
        assertThat(metadata.totalCount()).isEqualTo(totalCount);
        assertThat(metadata.webEnv()).isNull();
        assertThat(metadata.queryKey()).isNull();
        assertThat(metadata.dataSourceType()).isEqualTo("pubmed");
        assertThat(metadata.hasSessionToken()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建空白 session token 的元数据")
    void should_create_metadata_with_blank_session_token() {
        // given
        int totalCount = 1000;

        // when
        PubmedPlanMetadata metadata = new PubmedPlanMetadata(totalCount, "", "");

        // then
        assertThat(metadata.totalCount()).isEqualTo(totalCount);
        assertThat(metadata.webEnv()).isEmpty();
        assertThat(metadata.queryKey()).isEmpty();
        assertThat(metadata.hasSessionToken()).isFalse();
    }

    @Test
    @DisplayName("当只提供 webEnv 时应该抛出异常")
    void should_throw_exception_when_only_webenv_provided() {
        // given
        int totalCount = 1000;
        String webEnv = "MCID_674c8b5a5e8a9b1234567890";

        // when & then
        assertThatThrownBy(() -> new PubmedPlanMetadata(totalCount, webEnv, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("webEnv 和 queryKey 必须同时存在或同时为空");
    }

    @Test
    @DisplayName("当只提供 queryKey 时应该抛出异常")
    void should_throw_exception_when_only_querykey_provided() {
        // given
        int totalCount = 1000;
        String queryKey = "1";

        // when & then
        assertThatThrownBy(() -> new PubmedPlanMetadata(totalCount, null, queryKey))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("webEnv 和 queryKey 必须同时存在或同时为空");
    }

    @Test
    @DisplayName("当 webEnv 为空但 queryKey 有值时应该抛出异常")
    void should_throw_exception_when_webenv_blank_but_querykey_has_value() {
        // given
        int totalCount = 1000;
        String queryKey = "1";

        // when & then
        assertThatThrownBy(() -> new PubmedPlanMetadata(totalCount, "", queryKey))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("webEnv 和 queryKey 必须同时存在或同时为空");
    }

    @Test
    @DisplayName("当 queryKey 为空但 webEnv 有值时应该抛出异常")
    void should_throw_exception_when_querykey_blank_but_webenv_has_value() {
        // given
        int totalCount = 1000;
        String webEnv = "MCID_674c8b5a5e8a9b1234567890";

        // when & then
        assertThatThrownBy(() -> new PubmedPlanMetadata(totalCount, webEnv, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("webEnv 和 queryKey 必须同时存在或同时为空");
    }

    @Test
    @DisplayName("当 totalCount 为 0 时应该成功创建")
    void should_create_metadata_when_total_count_is_zero() {
        // given
        int totalCount = 0;

        // when
        PubmedPlanMetadata metadata = new PubmedPlanMetadata(totalCount, null, null);

        // then
        assertThat(metadata.totalCount()).isZero();
        assertThat(metadata.hasSessionToken()).isFalse();
    }

    @Test
    @DisplayName("hasSessionToken 应该在有非空 webEnv 时返回 true")
    void should_return_true_when_has_non_blank_webenv() {
        // given
        PubmedPlanMetadata metadata = new PubmedPlanMetadata(
            1000,
            "MCID_674c8b5a5e8a9b1234567890",
            "1"
        );

        // when
        boolean hasToken = metadata.hasSessionToken();

        // then
        assertThat(hasToken).isTrue();
    }

    @Test
    @DisplayName("hasSessionToken 应该在 webEnv 为 null 时返回 false")
    void should_return_false_when_webenv_is_null() {
        // given
        PubmedPlanMetadata metadata = new PubmedPlanMetadata(1000, null, null);

        // when
        boolean hasToken = metadata.hasSessionToken();

        // then
        assertThat(hasToken).isFalse();
    }

    @Test
    @DisplayName("hasSessionToken 应该在 webEnv 为空白时返回 false")
    void should_return_false_when_webenv_is_blank() {
        // given
        PubmedPlanMetadata metadata = new PubmedPlanMetadata(1000, "  ", "  ");

        // when
        boolean hasToken = metadata.hasSessionToken();

        // then
        assertThat(hasToken).isFalse();
    }

    @Test
    @DisplayName("toString 应该返回包含关键信息的字符串")
    void should_return_informative_string_representation() {
        // given
        PubmedPlanMetadata metadata = new PubmedPlanMetadata(
            1000,
            "MCID_674c8b5a5e8a9b1234567890",
            "1"
        );

        // when
        String result = metadata.toString();

        // then
        assertThat(result).contains("PubmedPlanMetadata");
        assertThat(result).contains("totalCount=1000");
        assertThat(result).contains("hasWebEnv=true");
    }
}
