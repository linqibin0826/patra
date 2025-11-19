package com.patra.catalog.domain.port;

/**
 * 文献聚合根仓储接口（领域层定义，基础设施层实现）。
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>接口在 Domain 层定义，确保领域层独立
 *   <li>实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
 *   <li>以聚合为单位加载和保存，维护聚合一致性
 *   <li>方法返回领域对象，而非 DO（数据对象）
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PublicationPort {
  // 方法待添加
}
