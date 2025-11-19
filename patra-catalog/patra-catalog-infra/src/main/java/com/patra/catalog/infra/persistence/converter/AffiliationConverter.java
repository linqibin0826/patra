package com.patra.catalog.infra.persistence.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 机构聚合根转换器。
 *
 * <p><b>职责</b>：
 *
 * <ul>
 *   <li>AffiliationAggregate ↔ AffiliationDO 转换
 *   <li>值对象(RorId、GridId、DedupKey)与基本类型的转换
 *   <li>地理位置和层次结构字段的转换
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@RequiredArgsConstructor
public class AffiliationConverter {
  // 待实现转换方法
}
