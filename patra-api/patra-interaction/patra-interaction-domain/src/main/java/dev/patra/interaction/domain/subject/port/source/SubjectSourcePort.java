package dev.patra.interaction.domain.subject.port.source;

import dev.patra.interaction.domain.subject.exception.SubjectSourceUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SourceRecord;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/// 一种对象的来源策略：每个 {@link SubjectType} 恰好一个实现。
///
/// 只返回「存在且带身份锚」的结果；来源软删的照样返回（`SourceState.DELETED`），由调用方判断。
public interface SubjectSourcePort {

  /// 负责的对象种类。
  ///
  /// @return 对象种类
  SubjectType type();

  /// 按来源内部 ID 解析一个对象（首次互动）。
  ///
  /// @param sourceId 来源内部 ID
  /// @return 来源记录；不存在或没有身份锚时为空
  /// @throws SubjectSourceUnavailableException 来源不可用
  Optional<SourceRecord> resolveByKey(long sourceId);

  /// 按身份锚批量解析（对账）。
  ///
  /// @param keys 身份锚，1 ~ 100 个
  /// @return 存在的对象，按身份锚索引；不在结果里的即不存在
  /// @throws SubjectSourceUnavailableException 来源不可用
  Map<NaturalKey, SourceRecord> resolveByNaturalKeys(Collection<NaturalKey> keys);
}
