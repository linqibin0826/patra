package dev.patra.interaction.domain.discussion.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/// @ 的识别与改写（spec 5.4.1、5.4.3）：近似 CommonMark 的行内规则，跳过转义、行内代码、链接与网址；伪造的标记一律
/// 转义；存储形式与编辑原文能互相往返。
class MentionSyntaxTest {

  /// `@` 前面是开头、空白、标点或符号时才是提及。
  @Test
  void should_find_mentions_after_start_space_or_punctuation() {
    assertThat(MentionSyntax.candidates("@chen-yu 你好，@Zhou_Min。(@wang-li) \"@li-hua\" 😀@abc"))
        .containsExactly("chen-yu", "Zhou_Min", "wang-li", "li-hua", "abc");
  }

  /// 前面是字母、数字、`_`、`@` 的不是提及（邮箱、`你好@x` 都不算）。
  @Test
  void should_skip_when_preceded_by_letter_digit_underscore_or_at() {
    assertThat(MentionSyntax.candidates("a@bob 你好@bob 1@bob _@bob @@bob mail@x.com")).isEmpty();
  }

  /// 名字贪婪取、去掉末尾的连接符，再按用户名语法（3 ~ 30 个字符、连接符不连续、不全是数字）筛。
  @Test
  void should_trim_trailing_connectors_and_check_syntax() {
    assertThat(MentionSyntax.candidates("@chen-yu- @ab @abc @123456 @a--b @" + "x".repeat(31)))
        .containsExactly("chen-yu", "abc");
  }

  /// 转义的 `@`、行内代码、链接、网址里都不识别。
  @Test
  void should_ignore_escapes_code_links_and_urls() {
    assertThat(
            MentionSyntax.candidates(
                "\\@bob `@bob` ``x @bob`` [@bob](https://x.org/@bob) https://x.org/@bob @carol"))
        .containsExactly("carol");
  }

  /// `@` 后面紧跟网址时，网址优先。
  @Test
  void should_not_treat_url_scheme_after_at_as_mention() {
    assertThat(MentionSyntax.candidates("@https://x.org/@bob 和 @HTTP://y.org")).isEmpty();
  }

  /// 没有闭合、或者跨了空行的反引号按普通字符算。
  @Test
  void should_treat_unclosed_or_paragraph_crossing_backticks_as_text() {
    assertThat(MentionSyntax.candidates("`@bob")).containsExactly("bob");
    assertThat(MentionSyntax.candidates("`a\n\n@carol`")).containsExactly("carol");
  }

  /// 候选按原文去重，大小写不同算两个原文。
  @Test
  void should_dedupe_by_original_text() {
    assertThat(MentionSyntax.candidates("@Bob @bob @Bob")).containsExactly("Bob", "bob");
  }

  /// 解析到的原文每一处都换成标记，解析不到的原样。
  @Test
  void should_replace_every_occurrence_of_resolved_names() {
    assertThat(
            MentionSyntax.toStored(
                "@bob 和 @Bob，@carol", n -> n.equalsIgnoreCase("bob") ? 7L : null))
        .isEqualTo("<@7> 和 <@7>，@carol");
  }

  /// 客户端发来的标记在代码之外一律转义（含链接里、转义的反斜杠之后）。
  @Test
  void should_escape_forged_tokens_outside_code() {
    assertThat(MentionSyntax.toStored("<@1> `<@2>` [x](<@3>) \\<@4> \\\\<@5>", n -> null))
        .isEqualTo("\\<@1> `<@2>` [x](\\<@3>) \\<@4> \\\\\\<@5>");
  }

  /// 没有提及的正文原样不变。
  @Test
  void should_keep_text_without_mentions_identical() {
    String text = "纯文本 a@b.com `code` https://x.org/@a [链接](https://y.org)\n> 引用";
    assertThat(MentionSyntax.toStored(text, n -> 1L)).isEqualTo(text);
  }

  /// 存储形式里有效标记的 ID：去重、按第一次出现；转义的与代码里的不算。
  @Test
  void should_list_token_ids_in_first_appearance_order() {
    assertThat(MentionSyntax.mentionedIds("<@7> x <@8> <@7> \\<@9> `<@10>`"))
        .containsExactly(7L, 8L);
  }

  /// 编辑原文：标记换回 `@用户名`，拿不到用户名的写成 `@已注销用户`。
  @Test
  void should_turn_tokens_back_into_usernames() {
    assertThat(MentionSyntax.toSource("hi <@7> and <@8>", id -> id == 7L ? "chen-yu" : null))
        .isEqualTo("hi @chen-yu and @已注销用户");
  }

  /// 存储形式 → 编辑原文 → 存储形式，原样往返。
  @Test
  void should_round_trip_source_to_stored() {
    String stored = "见 <@7>-，谢谢";
    String source = MentionSyntax.toSource(stored, id -> "chen-yu");

    assertThat(source).isEqualTo("见 @chen-yu-，谢谢");
    assertThat(MentionSyntax.toStored(source, n -> "chen-yu".equals(n) ? 7L : null))
        .isEqualTo(stored);
  }

  /// 塞满 `@`、反引号、方括号的长正文也在线性时间里扫完。
  @Test
  void should_scan_pathological_input_quickly() {
    String text = "@ab`".repeat(1250) + "``x".repeat(400) + "[".repeat(2000);
    assertTimeoutPreemptively(
        Duration.ofSeconds(1),
        () -> {
          MentionSyntax.candidates(text);
          MentionSyntax.toStored(text, n -> 1L);
        });
  }
}
