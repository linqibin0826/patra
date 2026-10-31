package dev.patra.interaction.app.subject.usecase.query;

import dev.patra.interaction.domain.subject.exception.InvalidSubjectRequestException;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 按登记表 ID 批量取对象视图（内部接口 `/_internal/subjects/views`，PAP-143）：通知服务读提醒列表、拼提醒邮件时
/// 现查链接 key、标题、期刊、是否还在与所有者。只查本地登记表，不解析来源。
@Component
@RequiredArgsConstructor
public class SubjectViewQueryService {

  /// 一次最多多少个 ID。
  public static final int MAX_IDS = 100;

  private final SubjectReadPort subjects;

  /// 批量取视图。
  ///
  /// @param subjectIds 登记表 ID，最多 {@value #MAX_IDS} 个（按去重前的长度算），重复的保留第一次出现
  /// @return 视图，按去重后的请求顺序；查不到的省略
  /// @throws InvalidSubjectRequestException 缺列表、含 `null`、超过上限
  public List<SubjectView> views(List<Long> subjectIds) {
    if (subjectIds == null
        || subjectIds.size() > MAX_IDS
        || subjectIds.stream().anyMatch(Objects::isNull)) {
      throw new InvalidSubjectRequestException("subjectIds 必须是 0 ~ " + MAX_IDS + " 个非空 ID");
    }
    Set<Long> ids = new LinkedHashSet<>(subjectIds);
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<SubjectId, SubjectView> found =
        subjects.findByIds(ids.stream().map(SubjectId::of).toList());
    return ids.stream().map(id -> found.get(SubjectId.of(id))).filter(Objects::nonNull).toList();
  }
}
