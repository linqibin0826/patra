package dev.patra.interaction.app.discussion.service;

import dev.patra.interaction.domain.discussion.exception.InvalidDiscussionRequestException;
import dev.patra.interaction.domain.subject.exception.InvalidSubjectKeyException;
import dev.patra.interaction.domain.subject.exception.SubjectCapabilityNotSupportedException;
import dev.patra.interaction.domain.subject.exception.SubjectNotFoundException;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 讨论的对象（spec 5.1）：本地校验请求里的类型、能力与 key；读只查本地登记表，不触发登记；失效的对象 404。
@Component
@RequiredArgsConstructor
public class DiscussionSubjects {

  private final SubjectReadPort subjects;

  /// 校验类型、能力与 key 的格式（不发远程调用）。
  ///
  /// @param typeSlug 对外类型名
  /// @param key 对外 key
  /// @return 类型
  /// @throws InvalidDiscussionRequestException 未知类型
  /// @throws SubjectCapabilityNotSupportedException 这种对象不能讨论
  /// @throws InvalidSubjectKeyException key 格式不对
  public SubjectType requireDiscussable(String typeSlug, String key) {
    SubjectType type =
        SubjectType.fromSlug(typeSlug)
            .orElseThrow(() -> new InvalidDiscussionRequestException("不支持的对象类型"));
    if (!type.supports(SubjectCapability.DISCUSS)) {
      throw new SubjectCapabilityNotSupportedException(type, SubjectCapability.DISCUSS);
    }
    type.parseKey(key);
    return type;
  }

  /// 读：按对外 key 查本地登记。
  ///
  /// @param type 类型
  /// @param key 对外 key
  /// @return 视图；还没人讨论过（未登记）为空
  /// @throws SubjectNotFoundException 已失效（经验已删除、文献已下线）
  public Optional<SubjectView> findForRead(SubjectType type, String key) {
    SubjectId id = subjects.findIdsByKeys(type, List.of(key)).get(key);
    if (id == null) {
      return Optional.empty();
    }
    return Optional.of(requireActive(id));
  }

  /// 按本地 ID 取正常的对象。
  ///
  /// @param subjectId 本地 ID
  /// @return 视图
  /// @throws SubjectNotFoundException 已失效
  /// @throws IllegalStateException 登记表里没有（评论引用的对象一定登记过）
  public SubjectView requireActive(SubjectId subjectId) {
    SubjectView view = subjects.findByIds(List.of(subjectId)).get(subjectId);
    if (view == null) {
      throw new IllegalStateException("登记表里没有这个对象：" + subjectId);
    }
    if (view.state() == SubjectState.UNAVAILABLE) {
      throw SubjectNotFoundException.unavailable(view.type(), view.key());
    }
    return view;
  }
}
