package dev.patra.catalog.app.portal.usecase.query;

import dev.patra.catalog.domain.portal.model.enums.CitationStyle;
import dev.patra.catalog.domain.portal.model.read.CitationEntry;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import dev.patra.catalog.domain.portal.port.citation.CitationFormatterPort;
import dev.patra.catalog.domain.portal.port.read.CitationRecordReadPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 复制引用查询服务（spec 第 7 节）：去重后读一次，按请求顺序交给格式化端口。
///
/// 编号样式的序号即请求顺序；APA 的字母序重排发生在格式化端口里。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class PortalCitationQueryService {

  private final CitationRecordReadPort readPort;
  private final CitationFormatterPort formatter;

  /// 生成引用。
  ///
  /// @param ids 文献主键（调用方已校验 1 ~ 100 个正整数）
  /// @param style 样式
  /// @return 引用条目；不存在与软删的跳过，全部不存在时为空列表
  @Transactional(readOnly = true)
  public List<CitationEntry> format(List<Long> ids, CitationStyle style) {
    List<Long> keys = List.copyOf(new LinkedHashSet<>(ids));
    Map<Long, CitationRecord> found =
        readPort.findByIds(keys).stream()
            .collect(Collectors.toMap(CitationRecord::id, Function.identity()));
    List<CitationRecord> ordered = keys.stream().map(found::get).filter(Objects::nonNull).toList();
    if (ordered.isEmpty()) {
      return List.of();
    }
    return formatter.format(style, ordered);
  }
}
