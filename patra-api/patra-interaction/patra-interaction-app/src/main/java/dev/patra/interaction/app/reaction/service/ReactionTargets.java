package dev.patra.interaction.app.reaction.service;

import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.post.model.read.ExperienceDigest;
import dev.patra.interaction.domain.post.port.read.PostReadPort;
import dev.patra.interaction.domain.reaction.exception.HelpfulTargetNotFoundException;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.model.vo.TargetProfile;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.port.markdown.PlainTextPort;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 把有帮助的目标解析成画像（spec §3.2）：评论经讨论的读端口取作者与状态、再经登记表取所在对象；经验只读登记表里它
/// 自己那一行（所有者、是否匿名、状态）。只读本地库，事务内外都能调。
///
/// 另给「有帮助已点」事件描述被点的内容（PAP-141）：所在对象、经验的结果（经投稿经验的读端口）、正文的纯文本。
@Component
@RequiredArgsConstructor
public class ReactionTargets {

  private final DiscussionReadPort discussions;
  private final SubjectReadPort subjects;
  private final PostReadPort posts;
  private final PlainTextPort plainText;

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

  /// 描述被点的内容，给「有帮助已点」事件。调用方已判过能点（目标在、所在对象在）。
  ///
  /// @param target 目标
  /// @return 事实
  /// @throws HelpfulTargetNotFoundException 评论或它所在的对象不存在、经验没有登记
  public TargetFacts describe(HelpfulTarget target) {
    return switch (target.type()) {
      case COMMENT -> commentFacts(target);
      case POST -> postFacts(target);
    };
  }

  /// 评论：所在对象与评论的纯文本（提及名单取自正文）。不读所在经验的结果：那是「在哪」，消费方按对象 ID 现查。
  ///
  /// @param target 目标
  /// @return 事实
  private TargetFacts commentFacts(HelpfulTarget target) {
    CommentRecord comment =
        discussions.findById(target.id()).orElseThrow(HelpfulTargetNotFoundException::new);
    SubjectView subject = subjects.findByIds(List.of(comment.subjectId())).get(comment.subjectId());
    if (subject == null) {
      throw new HelpfulTargetNotFoundException();
    }
    String text =
        comment.body() == null
            ? null
            : plainText.toPlainText(comment.body(), Set.copyOf(comment.mentionedUserIds())).value();
    return TargetFacts.of(subject, null, textOrNull(text));
  }

  /// 经验：它自己的登记对象、结果与正文的纯文本（经验不解析 @）。投稿经验里查不到这条（数据异常）时结果与纯文本为空，
  /// 不让点有帮助失败。
  ///
  /// @param target 目标
  /// @return 事实
  private TargetFacts postFacts(HelpfulTarget target) {
    SubjectView post = postView(target);
    Optional<ExperienceDigest> digest = posts.findDigest(target.id());
    String text =
        digest
            .map(ExperienceDigest::body)
            .map(body -> plainText.toPlainText(body, Set.of()).value())
            .orElse(null);
    return TargetFacts.of(
        post, digest.map(ExperienceDigest::outline).orElse(null), textOrNull(text));
  }

  /// 转出来没有文字（正文只有 Markdown 结构，如单独一个 `-`）记为空：消费方据「没有正文」退回写结果行。
  ///
  /// @param text 纯文本，可空
  /// @return 有文字时原样，否则 `null`
  private static String textOrNull(String text) {
    return text == null || text.isBlank() ? null : text;
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
    SubjectView post = postView(target);
    return TargetProfile.of(
        target,
        post.owner().userId(),
        post.state() == SubjectState.ACTIVE,
        post.owner().anonymous());
  }

  /// 经验在登记表里它自己那一行。
  ///
  /// @param target 目标（经验）
  /// @return 登记视图
  /// @throws HelpfulTargetNotFoundException 经验没有登记，或登记行没有所有者
  private SubjectView postView(HelpfulTarget target) {
    String key = Long.toString(target.id());
    SubjectId id = subjects.findIdsByKeys(SubjectType.POST, List.of(key)).get(key);
    SubjectView post = id == null ? null : subjects.findByIds(List.of(id)).get(id);
    if (post == null || post.owner() == null) {
      throw new HelpfulTargetNotFoundException();
    }
    return post;
  }
}
