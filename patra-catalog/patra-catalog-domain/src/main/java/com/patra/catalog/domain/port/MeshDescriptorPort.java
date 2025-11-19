package com.patra.catalog.domain.port;

/**
 * MeSH 主题词聚合根仓储接口(领域层定义,基础设施层实现)。
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>接口在Domain层定义,确保领域层独立
 *   <li>实现在Infrastructure层,遵循依赖倒置原则(DIP)
 *   <li>聚合内实体(TreeNumber/EntryTerm/Concept)分开加载
 *   <li>提供树形查询、全文检索等专门方法
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface MeshDescriptorPort {
  // 方法待添加
}
