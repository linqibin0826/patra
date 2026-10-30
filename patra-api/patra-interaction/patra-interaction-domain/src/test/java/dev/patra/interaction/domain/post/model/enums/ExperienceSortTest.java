package dev.patra.interaction.domain.post.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.post.exception.InvalidPostRequestException;
import org.junit.jupiter.api.Test;

/// 期刊页经验列表的排序参数：默认最新投稿，只认 `latest`、`helpful`。
class ExperienceSortTest {

  /// 不传或 `latest` 为最新投稿，`helpful` 为最有帮助。
  @Test
  void should_parse_known_values() {
    assertThat(ExperienceSort.fromParam(null)).isEqualTo(ExperienceSort.LATEST);
    assertThat(ExperienceSort.fromParam("")).isEqualTo(ExperienceSort.LATEST);
    assertThat(ExperienceSort.fromParam("latest")).isEqualTo(ExperienceSort.LATEST);
    assertThat(ExperienceSort.fromParam("helpful")).isEqualTo(ExperienceSort.HELPFUL);
  }

  /// 未知值与大写都拒绝。
  @Test
  void should_reject_unknown_values() {
    assertThatThrownBy(() -> ExperienceSort.fromParam("HELPFUL"))
        .isInstanceOf(InvalidPostRequestException.class);
    assertThatThrownBy(() -> ExperienceSort.fromParam("hot"))
        .isInstanceOf(InvalidPostRequestException.class);
  }
}
