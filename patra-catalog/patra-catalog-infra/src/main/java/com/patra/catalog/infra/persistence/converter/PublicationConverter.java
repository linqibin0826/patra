package com.patra.catalog.infra.persistence.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 文献聚合根转换器。
 *
 * <p><b>职责</b>：
 *
 * <ul>
 *   <li>PublicationAggregate ↔ PublicationDO 转换
 *   <li>PublicationMetadata、Abstract 等关联实体的转换
 *   <li>值对象(VO)与基本类型的转换
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@RequiredArgsConstructor
public class PublicationConverter {
  // 待实现转换方法
}
