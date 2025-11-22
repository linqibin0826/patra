package com.patra.catalog.infra.persistence.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 文献聚合根转换器。
/// 
/// **职责**：
/// 
/// - PublicationAggregate ↔ PublicationDO 转换
///   - PublicationMetadata、Abstract 等关联实体的转换
///   - 值对象(VO)与基本类型的转换
/// 
/// @author linqibin
/// @since 0.1.0
@Component
@RequiredArgsConstructor
public class PublicationConverter {
  // 待实现转换方法
}
