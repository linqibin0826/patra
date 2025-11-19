package com.patra.catalog.infra.persistence.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MeSH 主题词聚合根转换器。
 *
 * <p><b>职责</b>：
 *
 * <ul>
 *   <li>MeshDescriptorAggregate ↔ MeshDescriptorDO 转换
 *   <li>MeshTreeNumber、MeshEntryTerm、MeshConcept ↔ DO 转换
 *   <li>值对象(VO)与基本类型的转换
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@RequiredArgsConstructor
public class MeshDescriptorConverter {
  // 待实现转换方法
}
