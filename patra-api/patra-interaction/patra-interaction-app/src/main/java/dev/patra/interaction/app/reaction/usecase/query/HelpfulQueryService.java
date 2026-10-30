package dev.patra.interaction.app.reaction.usecase.query;

import dev.patra.interaction.domain.reaction.port.read.HelpfulReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 有帮助的公开读：主页的获赞，按用户 ID 取，不要会话，不查 identity（注销用户的主页本来就打不开）。
@Component
@RequiredArgsConstructor
public class HelpfulQueryService {

  private final HelpfulReadPort reads;

  /// 用户的获赞，没有记录为 0。
  ///
  /// @param userId 用户
  /// @return 获赞
  public long helpfulReceived(long userId) {
    return reads.helpfulReceived(userId);
  }
}
