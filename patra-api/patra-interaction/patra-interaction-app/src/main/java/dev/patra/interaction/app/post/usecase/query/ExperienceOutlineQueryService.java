package dev.patra.interaction.app.post.usecase.query;

import dev.patra.interaction.domain.post.exception.InvalidPostRequestException;
import dev.patra.interaction.domain.post.model.read.ExperienceOutline;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.port.read.PostReadPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 按帖子 ID 批量取经验结果（内部接口 `/_internal/posts/outlines`，PAP-143）：通知服务在提醒列表与提醒邮件里写经验
/// 的结果行。已删除的也返回：删除事件送到之前，提醒还可能在。
@Component
@RequiredArgsConstructor
public class ExperienceOutlineQueryService {

  /// 一次最多多少个 ID。
  public static final int MAX_IDS = 100;

  private final PostReadPort posts;

  /// 批量取结果。
  ///
  /// @param postIds 帖子 ID，最多 {@value #MAX_IDS} 个（按去重前的长度算），重复的保留第一次出现
  /// @return 结果，按去重后的请求顺序；查不到的省略
  /// @throws InvalidPostRequestException 缺列表、含 `null`、超过上限
  public List<ExperienceOutlineView> outlines(List<Long> postIds) {
    if (postIds == null || postIds.size() > MAX_IDS || postIds.stream().anyMatch(Objects::isNull)) {
      throw new InvalidPostRequestException("postIds 必须是 0 ~ " + MAX_IDS + " 个非空 ID");
    }
    Set<Long> ids = new LinkedHashSet<>(postIds);
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<PostId, ExperienceOutline> found =
        posts.findOutlines(ids.stream().map(PostId::of).toList());
    return ids.stream()
        .filter(id -> found.containsKey(PostId.of(id)))
        .map(id -> ExperienceOutlineView.of(id, found.get(PostId.of(id))))
        .toList();
  }
}
