package dev.patra.notification.infra.kernel.interaction;

import dev.patra.interaction.api.post.dto.ExperienceOutlineResp;
import dev.patra.interaction.api.post.dto.ExperienceOutlinesReq;
import dev.patra.interaction.api.post.endpoint.PostEndpoint;
import dev.patra.interaction.api.subject.dto.SubjectViewResp;
import dev.patra.interaction.api.subject.dto.SubjectViewsReq;
import dev.patra.interaction.api.subject.endpoint.SubjectEndpoint;
import dev.patra.notification.domain.kernel.exception.InteractionUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import dev.patra.notification.domain.kernel.model.read.ExperienceOutline;
import dev.patra.notification.domain.kernel.model.read.SubjectView;
import dev.patra.notification.domain.kernel.port.interaction.SubjectViewPort;
import dev.patra.tool.core.error.remote.RemoteCallException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/// 组合 interaction 的两个内部接口：先取对象视图，其中是经验的再按帖子 ID（视图里的 `key`）取结果，合成一个视图。
///
/// 不认识的对象类型跳过（PAP-141 契约：枚举会加值）。三种失败——回了错误响应、连不上或超时、注册中心里没有实例——都
/// 翻成 {@link InteractionUnavailableException}，由调用方决定降级还是传播。
@Slf4j
@Component
@RequiredArgsConstructor
public class SubjectViewAdapter implements SubjectViewPort {

  private final SubjectEndpoint subjects;
  private final PostEndpoint posts;

  /// 批量取对象视图。
  ///
  /// @param subjectIds 登记表 ID
  /// @return 登记表 ID → 视图
  @Override
  public Map<Long, SubjectView> findViews(Collection<Long> subjectIds) {
    if (subjectIds.isEmpty() || subjectIds.size() > MAX_SUBJECTS) {
      throw new IllegalArgumentException("一次查 1 ~ " + MAX_SUBJECTS + " 个对象：" + subjectIds.size());
    }
    List<SubjectViewResp> views =
        call("对象视图", () -> subjects.views(SubjectViewsReq.of(List.copyOf(subjectIds))).subjects());
    Map<Long, ExperienceOutline> outlines = outlinesOf(views);
    Map<Long, SubjectView> result = new LinkedHashMap<>();
    for (SubjectViewResp view : views) {
      Optional<SubjectType> type = SubjectType.parse(view.type());
      if (type.isEmpty()) {
        continue;
      }
      result.put(
          view.subjectId(),
          SubjectView.builder()
              .subjectId(view.subjectId())
              .type(type.get())
              .key(view.key())
              .available(view.available())
              .title(view.title())
              .journal(view.journal())
              .abbreviation(view.abbreviation())
              .publishedDate(view.publishedDate())
              .ownerId(view.ownerId())
              .ownerAnonymous(view.ownerAnonymous())
              .experience(postIdOf(view).map(outlines::get).orElse(null))
              .build());
    }
    return Map.copyOf(result);
  }

  /// 视图里的经验按帖子 ID 取结果；没有经验时不调。
  ///
  /// @param views 对象视图
  /// @return 帖子 ID → 结果
  private Map<Long, ExperienceOutline> outlinesOf(List<SubjectViewResp> views) {
    List<Long> postIds =
        views.stream().map(SubjectViewAdapter::postIdOf).flatMap(Optional::stream).toList();
    if (postIds.isEmpty()) {
      return Map.of();
    }
    return call("经验结果", () -> posts.outlines(ExperienceOutlinesReq.of(postIds)).outlines()).stream()
        .collect(
            Collectors.toUnmodifiableMap(
                ExperienceOutlineResp::postId,
                o ->
                    ExperienceOutline.of(
                        o.firstDecision(), o.finalOutcome(), o.firstRoundDays(), o.totalDays())));
  }

  /// 经验的帖子 ID：类型是经验、key 是数字时才有。
  ///
  /// @param view 对象视图
  /// @return 帖子 ID
  private static Optional<Long> postIdOf(SubjectViewResp view) {
    if (!SubjectType.POST.name().equals(view.type()) || view.key() == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.parseLong(view.key()));
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }

  /// 调一次代理，把三种失败翻成依赖不可用。
  ///
  /// @param what 调的是哪个接口（日志用）
  /// @param call 调用
  /// @param <T> 返回类型
  /// @return 返回值
  private static <T> T call(String what, Supplier<T> call) {
    try {
      return call.get();
    } catch (RemoteCallException ex) {
      log.warn(
          "interaction {}接口返回错误: httpStatus={}, errorCode={}",
          what,
          ex.getHttpStatus(),
          ex.getErrorCode());
      throw new InteractionUnavailableException(ex);
    } catch (ResourceAccessException | IllegalStateException ex) {
      log.warn("interaction {}接口不可用: cause={}", what, ex.getClass().getSimpleName());
      throw new InteractionUnavailableException(ex);
    }
  }
}
