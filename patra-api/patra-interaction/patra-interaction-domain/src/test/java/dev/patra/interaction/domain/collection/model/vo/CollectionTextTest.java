package dev.patra.interaction.domain.collection.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.interaction.domain.collection.exception.InvalidCollectionFieldsException;
import dev.patra.tool.core.error.field.FieldViolation;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/// 收藏夹名称、简介与备注的文本规则：去首尾空白、字素与字节上限、拒绝控制字符。
class CollectionTextTest {

  /// 首尾的不换行空格、全角空格都去掉。
  @Test
  void should_strip_spaces_around_name() {
    assertThat(CollectionName.parse("  组会 　").value()).isEqualTo("组会");
  }

  /// 去完空白为空的名称是必填错误。
  @Test
  void should_reject_blank_name() {
    assertViolation(() -> CollectionName.parse("  "), "name", "REQUIRED");
    assertViolation(() -> CollectionName.parse(null), "name", "REQUIRED");
  }

  /// 上限按字素计：100 个汉字可以、101 个不行；100 个 ZWJ 家庭 emoji 是 100 字素，但超了字节上限。
  @Test
  void should_count_graphemes_not_chars() {
    String family = "👨‍👩‍👧";
    assertThat(CollectionName.parse("组".repeat(100)).value()).hasSize(100);
    assertViolation(() -> CollectionName.parse(family.repeat(100)), "name", "TOO_LONG");
    assertViolation(() -> CollectionName.parse("组".repeat(101)), "name", "TOO_LONG");
  }

  /// 一个字素里塞几百个组合符，按字节上限拒绝。
  @Test
  void should_cap_bytes() {
    assertViolation(() -> CollectionName.parse("e" + "́".repeat(400)), "name", "TOO_LONG");
  }

  /// 名称是单行，换行算非法字符。
  @Test
  void should_reject_line_break_in_name() {
    assertViolation(() -> CollectionName.parse("a\nb"), "name", "INVALID_CHARACTER");
  }

  /// 备注保留中间的换行。
  @Test
  void should_allow_line_feed_in_note() {
    assertThat(ItemNote.parse("第一行\n第二行")).get().extracting(ItemNote::value).isEqualTo("第一行\n第二行");
  }

  /// 空白备注等于没有备注。
  @Test
  void should_treat_blank_note_as_absent() {
    assertThat(ItemNote.parse(" \n ")).isEmpty();
    assertThat(ItemNote.parse(null)).isEmpty();
  }

  /// 简介最多 500 字素。
  @Test
  void should_reject_long_description() {
    assertThat(CollectionDescription.parse("字".repeat(500))).isPresent();
    assertViolation(() -> CollectionDescription.parse("字".repeat(501)), "description", "TOO_LONG");
  }

  /// 备注里的控制字符拒绝。
  @Test
  void should_reject_control_char_in_note() {
    assertViolation(() -> ItemNote.parse("a\u0007"), "note", "INVALID_CHARACTER");
  }

  /// 断言抛出恰好一条字段错误。
  ///
  /// @param call 调用
  /// @param field 字段
  /// @param code 原因码
  private static void assertViolation(ThrowingCallable call, String field, String code) {
    InvalidCollectionFieldsException ex =
        catchThrowableOfType(InvalidCollectionFieldsException.class, call);
    assertThat(ex).isNotNull();
    assertThat(ex.getFieldViolations())
        .extracting(FieldViolation::field, FieldViolation::code)
        .containsExactly(tuple(field, code));
  }
}
