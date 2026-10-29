package dev.patra.catalog.domain.portal.port.citation;

import dev.patra.catalog.domain.portal.model.enums.CitationStyle;
import dev.patra.catalog.domain.portal.model.read.CitationEntry;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.util.List;

/// 引用格式化端口：按样式把引用记录渲染成引用文本。
///
/// @author linqibin
/// @since 0.1.0
public interface CitationFormatterPort {

  /// 渲染一组引用。
  ///
  /// 编号样式按入参顺序编号；APA 等按样式自己的顺序（作者字母序）返回。
  ///
  /// @param style 样式
  /// @param records 引用记录，按请求顺序
  /// @return 引用条目；空入参返回空列表
  List<CitationEntry> format(CitationStyle style, List<CitationRecord> records);
}
