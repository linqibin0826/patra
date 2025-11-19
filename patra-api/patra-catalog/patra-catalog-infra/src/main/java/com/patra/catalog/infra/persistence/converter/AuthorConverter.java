package com.patra.catalog.infra.persistence.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 作者聚合根转换器。
 *
 * <p><b>职责</b>：
 *
 * <ul>
 *   <li>AuthorAggregate ↔ AuthorDO 转换
 *   <li>值对象(Orcid、DedupKey)与基本类型的转换
 *   <li>去重相关字段的转换
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@RequiredArgsConstructor
public class AuthorConverter {
  // 待实现转换方法
}
