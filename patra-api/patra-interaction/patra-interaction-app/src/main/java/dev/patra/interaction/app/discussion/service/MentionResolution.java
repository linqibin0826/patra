package dev.patra.interaction.app.discussion.service;

import dev.patra.interaction.domain.discussion.exception.InvalidCommentException;
import dev.patra.interaction.domain.discussion.model.read.MentionTarget;
import dev.patra.interaction.domain.discussion.policy.DiscussionLimits;
import dev.patra.interaction.domain.discussion.policy.MentionSyntax;
import dev.patra.interaction.domain.discussion.port.mention.MentionDirectoryPort;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// @ 的解析（spec 5.4.2）：分两步，本地识别（{@link #draft}）在任何远程调用之前，向 identity 解析（{@link #resolve}）在
/// 事务之外。
@Component
@RequiredArgsConstructor
public class MentionResolution {

  private final MentionDirectoryPort directory;

  /// 本地识别 @ 候选。
  ///
  /// @param text 规范化后的原文
  /// @return 草稿
  /// @throws InvalidCommentException 候选超过 100 个
  public MentionDraft draft(String text) {
    List<String> candidates = MentionSyntax.candidates(text);
    if (candidates.size() > DiscussionLimits.MAX_MENTION_CANDIDATES) {
      throw InvalidCommentException.tooManyMentions();
    }
    return MentionDraft.of(text, candidates);
  }

  /// 向 identity 解析并换成存储形式：正常账号、或 `carriedOver` 里的人（编辑时当前版本已有的提及）换成标记。
  ///
  /// @param draft 草稿
  /// @param carriedOver 当前版本里已有的提及；发表时为空
  /// @return 存储形式与提及
  /// @throws InvalidCommentException 提及超过 10 人
  public ResolvedBody resolve(MentionDraft draft, Set<Long> carriedOver) {
    Map<String, MentionTarget> targets =
        draft.candidates().isEmpty() ? Map.of() : directory.resolve(draft.candidates());
    String stored =
        MentionSyntax.toStored(
            draft.text(),
            name -> {
              MentionTarget target = targets.get(name);
              boolean mention =
                  target != null
                      && (target.state() == AccountState.ACTIVE
                          || carriedOver.contains(target.userId()));
              return mention ? target.userId() : null;
            });
    List<Long> mentioned = MentionSyntax.mentionedIds(stored);
    if (mentioned.size() > DiscussionLimits.MAX_MENTIONS) {
      throw InvalidCommentException.tooManyMentions();
    }
    return ResolvedBody.of(stored, mentioned);
  }
}
