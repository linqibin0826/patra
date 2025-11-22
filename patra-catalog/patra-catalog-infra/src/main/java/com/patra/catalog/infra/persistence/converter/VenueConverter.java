package com.patra.catalog.infra.persistence.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 出版载体聚合根转换器。
///
/// **职责**：
///
/// - VenueAggregate ↔ VenueDO 转换
///   - VenueInstance ↔ VenueInstanceDO 转换
///   - 值对象(VO)与基本类型的转换
///
/// @author linqibin
/// @since 0.1.0
@Component
@RequiredArgsConstructor
public class VenueConverter {
  // 待实现转换方法
}
