package dev.patra.interaction.app.reaction.service;

import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.reaction.exception.HelpfulTargetNotFoundException;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.model.vo.TargetProfile;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 把有帮助的目标解析成画像（spec §3.2）：评论经讨论的读端口取作者与状态、再经登记表取所在对象；经验只读登记表里它
/// 自己那一行（所有者、是否匿名、状态），不经投稿经验子域。只读本地库，事务内外都能调。
@Component
@RequiredArgsConstructor
public class ReactionTargets {

  private final DiscussionReadPort discussions;
  private final SubjectReadPort subjects;

  /// 解析目标。查到但现在不能点（占位、已删除、所在对象失效）照常返回，由调用方判。
  ///
  /// @param target 目标
  /// @return 画像
  /// @throws HelpfulTargetNotFoundException 评论不存在、经验没有登记
  public TargetProfile require(HelpfulTarget target) {
    return switch (target.type()) {
      case COMMENT -> commentProfile(target);
      case POST -> postProfile(target);
    };
  }

  /// 评论：正常且所在对象正常才能点；所在对象的所有者匿名、且就是评论作者时算匿名。所在对象在登记表里查不到（数据
  /// 异常）当作不能点。
  ///
  /// @param target 目标
  /// @return 画像
  private TargetProfile commentProfile(HelpfulTarget target) {
    CommentRecord comment =
        discussions.findById(target.id()).orElseThrow(HelpfulTargetNotFoundException::new);
    SubjectView subject = subjects.findByIds(List.of(comment.subjectId())).get(comment.subjectId());
    boolean available =
        !comment.isDeleted() && subject != null && subject.state() == SubjectState.ACTIVE;
    SubjectOwner owner = subject == null ? null : subject.owner();
    boolean anonymous = owner != null && owner.anonymous() && owner.userId() == comment.authorId();
    return TargetProfile.of(target, comment.authorId(), available, anonymous);
  }

  /// 经验：登记行的所有者就是作者，登记行正常才能点。
  ///
  /// @param target 目标
  /// @return 画像
  private TargetProfile postProfile(HelpfulTarget target) {
    String key = Long.toString(target.id());
    SubjectId id = subjects.findIdsByKeys(SubjectType.POST, List.of(key)).get(key);
    SubjectView post = id == null ? null : subjects.findByIds(List.of(id)).get(id);
    if (post == null || post.owner() == null) {
      throw new HelpfulTargetNotFoundException();
    }
    return TargetProfile.of(
        target,
        post.owner().userId(),
        post.state() == SubjectState.ACTIVE,
        post.owner().anonymous());
  }
}
