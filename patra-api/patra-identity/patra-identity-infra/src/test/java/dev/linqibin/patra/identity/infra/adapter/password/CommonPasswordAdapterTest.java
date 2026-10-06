package dev.linqibin.patra.identity.infra.adapter.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// CommonPasswordAdapter 单元测试。
@DisplayName("CommonPasswordAdapter 单元测试")
class CommonPasswordAdapterTest {

  private final CommonPasswordAdapter testList =
      new CommonPasswordAdapter("password/test-common-passwords.txt.gz");

  @ParameterizedTest
  @ValueSource(strings = {"abc12345", "123456", "spaced-entry", "fullwidth"})
  @DisplayName("条目按比较键加载：转小写、去空白、全角转半角，短条目也保留")
  void should_load_entries_as_comparison_keys(String key) {
    assertThat(testList.isCommon(key)).isTrue();
  }

  @Test
  @DisplayName("空行不会变成一个空的条目")
  void should_skip_blank_lines() {
    assertThat(testList.isCommon("")).isFalse();
  }

  @Test
  @DisplayName("不在名单里的返回 false")
  void should_return_false_for_unknown_key() {
    assertThat(testList.isCommon("correct horse battery")).isFalse();
  }

  @Test
  @DisplayName("默认加载 Django 的名单")
  void should_load_django_list_by_default() {
    CommonPasswordAdapter django = new CommonPasswordAdapter();

    assertThat(django.isCommon(PlainPassword.comparisonKeyOf("PASSWORD"))).isTrue();
    assertThat(django.isCommon("123456")).isTrue();
    assertThat(django.isCommon("qwerty")).isTrue();
    assertThat(django.isCommon("correct horse battery staple 2026")).isFalse();
  }

  @Test
  @DisplayName("名单文件不存在时启动失败")
  void should_fail_when_list_is_missing() {
    assertThatThrownBy(() -> new CommonPasswordAdapter("password/missing.txt.gz"))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("password/missing.txt.gz");
  }
}
