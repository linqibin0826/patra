package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ReservedUsernames 单元测试：同形归一、去分隔符、三组清单。
@DisplayName("ReservedUsernames 单元测试")
class ReservedUsernamesTest {

  @Test
  @DisplayName("同形归一只映射 0、1、3、5，并去掉下划线和连字符")
  void should_fold_homoglyphs_and_separators() {
    assertThat(ReservedUsernames.fold("ad_m-1n")).isEqualTo("admin");
    assertThat(ReservedUsernames.fold("0fficial")).isEqualTo("official");
    assertThat(ReservedUsernames.fold("ncb1")).isEqualTo("ncbi");
    assertThat(ReservedUsernames.fold("5upport")).isEqualTo("support");
    assertThat(ReservedUsernames.fold("3pmc")).isEqualTo("epmc");
    assertThat(ReservedUsernames.fold("hello")).isEqualTo("hello");
  }

  @Test
  @DisplayName("三组清单各命中一个，匹配前先折叠")
  void should_contain_words_from_all_three_groups() {
    assertThat(ReservedUsernames.contains("settings")).isTrue();
    assertThat(ReservedUsernames.contains("admin")).isTrue();
    assertThat(ReservedUsernames.contains("pubmed")).isTrue();
    assertThat(ReservedUsernames.contains("deleted-user")).isTrue();
    assertThat(ReservedUsernames.contains("adm1n")).isTrue();
  }

  @Test
  @DisplayName("整词比较：多一个字符就不命中")
  void should_match_whole_words_only() {
    assertThat(ReservedUsernames.contains("admin2")).isFalse();
    assertThat(ReservedUsernames.contains("hello")).isFalse();
    assertThat(ReservedUsernames.contains("patra-dev")).isFalse();
  }
}
