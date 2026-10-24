package dev.patra.interaction.domain.subject.port.read;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.Collection;
import java.util.Map;

/// 给其他子域的登记表读端口：只查本地，不解析、不登记。
public interface SubjectReadPort {

  /// 一次最多查多少个 key（与门户批量接口一致）。
  int MAX_KEYS = 100;

  /// 一次最多查多少个 ID（一次导出的上限）。
  int MAX_IDS = 5000;

  /// 按对外 key 查已登记对象（「我的状态」）。
  ///
  /// @param type 对象种类
  /// @param keys 对外 key，最多 {@value #MAX_KEYS} 个
  /// @return key → 本地 ID；未登记或不合法的 key 不在结果里
  /// @throws IllegalArgumentException 超过上限
  Map<String, SubjectId> findIdsByKeys(SubjectType type, Collection<String> keys);

  /// 按本地 ID 取展示信息（文献库、收藏夹、导出、排序）。
  ///
  /// @param ids 本地 ID，最多 {@value #MAX_IDS} 个
  /// @return 本地 ID → 视图；查不到的不在结果里
  /// @throws IllegalArgumentException 超过上限
  Map<SubjectId, SubjectView> findByIds(Collection<SubjectId> ids);
}
