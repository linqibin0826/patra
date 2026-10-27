package dev.patra.interaction.adapter.post.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.post.exception.DuplicateSubmissionException;
import dev.patra.interaction.domain.post.exception.PostForbiddenException;
import dev.patra.interaction.domain.post.model.vo.PostId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// 重复投稿日期的错误响应带上已有经验的 ID（字符串，与其他 ID 一致），门户给「查看那条经验」。
class PostProblemFieldContributorTest {

  private final PostProblemFieldContributor contributor = new PostProblemFieldContributor();

  /// 重复：放 `existingPostId`。
  @Test
  void should_add_existing_post_id_as_string() {
    Map<String, Object> fields = new HashMap<>();

    contributor.contribute(fields, new DuplicateSubmissionException(PostId.of(1234567890123L)));

    assertThat(fields).containsExactly(Map.entry("existingPostId", "1234567890123"));
  }

  /// 别的异常：什么都不加。
  @Test
  void should_ignore_other_exceptions() {
    Map<String, Object> fields = new HashMap<>();

    contributor.contribute(fields, new PostForbiddenException());

    assertThat(fields).isEmpty();
  }
}
