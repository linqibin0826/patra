package com.patra.catalog.infra.persistence.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 作者聚合根转换器。
/// 
/// **职责**：
/// 
/// - AuthorAggregate ↔ AuthorDO 转换
///   - 值对象(Orcid、DedupKey)与基本类型的转换
///   - 去重相关字段的转换
/// 
/// @author linqibin
/// @since 0.1.0
@Component
@RequiredArgsConstructor
public class AuthorConverter {
  // 待实现转换方法
}
