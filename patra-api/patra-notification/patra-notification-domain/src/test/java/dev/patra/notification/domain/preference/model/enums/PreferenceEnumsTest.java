package dev.patra.notification.domain.preference.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.preference.model.read.EmailMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// 档位与退订类别的解析：只认大写的名字，别的写法（小写、被截断、`null`）都不认。
class PreferenceEnumsTest {

  /// 三个档位的名字都认。
  @Test
  void email_mode_parses_exact_names() {
    assertThat(EmailMode.parse("OFF")).contains(EmailMode.OFF);
    assertThat(EmailMode.parse("INSTANT")).contains(EmailMode.INSTANT);
    assertThat(EmailMode.parse("DIGEST")).contains(EmailMode.DIGEST);
  }

  /// 小写、不认识的档位、空值都不认。
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"off", "WEEKLY", " OFF"})
  void email_mode_rejects_other_spellings(String raw) {
    assertThat(EmailMode.parse(raw)).isEmpty();
  }

  /// 四个退订类别的名字都认。
  @Test
  void unsubscribe_category_parses_exact_names() {
    assertThat(UnsubscribeCategory.parse("REPLY")).contains(UnsubscribeCategory.REPLY);
    assertThat(UnsubscribeCategory.parse("MENTION")).contains(UnsubscribeCategory.MENTION);
    assertThat(UnsubscribeCategory.parse("DIGEST")).contains(UnsubscribeCategory.DIGEST);
    assertThat(UnsubscribeCategory.parse("ALL")).contains(UnsubscribeCategory.ALL);
  }

  /// 链接被截断成 `MEN`、写成小写、缺失：都不认。
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"MEN", "mention", "HELPFUL"})
  void unsubscribe_category_rejects_other_spellings(String raw) {
    assertThat(UnsubscribeCategory.parse(raw)).isEmpty();
  }

  /// 链接里只会出现 `REPLY`、`MENTION`、`DIGEST`；`ALL` 只在落地页上。
  @Test
  void only_all_is_absent_from_links() {
    assertThat(UnsubscribeCategory.REPLY.inLinks()).isTrue();
    assertThat(UnsubscribeCategory.MENTION.inLinks()).isTrue();
    assertThat(UnsubscribeCategory.DIGEST.inLinks()).isTrue();
    assertThat(UnsubscribeCategory.ALL.inLinks()).isFalse();
  }
}
