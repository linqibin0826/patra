package dev.patra.catalog.domain.kernel.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `SuggestQuery` 单元测试：归一化、匹配模式判定、长度与 limit 边界。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("SuggestQuery 单元测试")
class SuggestQueryTest {

  @Test
  @DisplayName("NFKC 归一、去首尾空白、连续空白折成一个空格")
  void should_normalize_nfkc_strip_and_collapse_whitespace() {
    // 全角空格 U+3000 按码点构造：gjf 会把源码里的 unicode 转义改写成肉眼看不见的字面字符
    String raw = Character.toString(0x3000) + "ＰＵＭＣＨ  协和\t医院 ";

    assertThat(SuggestQuery.of(raw, 10).text()).isEqualTo("PUMCH 协和 医院");
  }

  @Test
  @DisplayName("含汉字、假名、谚文的查询走子串模式")
  void should_use_substring_mode_for_han_kana_and_hangul() {
    assertThat(SuggestQuery.of("协和", 10).mode()).isEqualTo(MatchMode.SUBSTRING);
    assertThat(SuggestQuery.of("北京 Hospital", 10).mode()).isEqualTo(MatchMode.SUBSTRING);
    assertThat(SuggestQuery.of("ソウル", 10).mode()).isEqualTo(MatchMode.SUBSTRING);
    assertThat(SuggestQuery.of("서울", 10).mode()).isEqualTo(MatchMode.SUBSTRING);
  }

  @Test
  @DisplayName("拉丁文字的查询走前缀与词首模式")
  void should_use_word_prefix_mode_for_latin_text() {
    assertThat(SuggestQuery.of("Mayo", 10).mode()).isEqualTo(MatchMode.WORD_PREFIX);
    assertThat(SuggestQuery.of("Ärzte", 10).mode()).isEqualTo(MatchMode.WORD_PREFIX);
  }

  @Test
  @DisplayName("长度按码点算，2 ~ 100 之外拒绝")
  void should_count_length_in_code_points_between_2_and_100() {
    assertThat(SuggestQuery.of("协和", 10).text()).isEqualTo("协和");
    assertThat(SuggestQuery.of("a".repeat(100), 10).text()).hasSize(100);
    assertThatIllegalArgumentException().isThrownBy(() -> SuggestQuery.of("协", 10));
    assertThatIllegalArgumentException().isThrownBy(() -> SuggestQuery.of("a".repeat(101), 10));
    assertThatIllegalArgumentException().isThrownBy(() -> SuggestQuery.of("   ", 10));
    assertThatIllegalArgumentException().isThrownBy(() -> SuggestQuery.of(null, 10));
  }

  @Test
  @DisplayName("limit 只收 1 ~ 20")
  void should_reject_limit_outside_1_to_20() {
    assertThat(SuggestQuery.of("Mayo", 1).limit()).isEqualTo(1);
    assertThat(SuggestQuery.of("Mayo", 20).limit()).isEqualTo(20);
    assertThatIllegalArgumentException().isThrownBy(() -> SuggestQuery.of("Mayo", 0));
    assertThatIllegalArgumentException().isThrownBy(() -> SuggestQuery.of("Mayo", 21));
  }
}
