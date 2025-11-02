/**
 * 共享的跨领域值对象。
 *
 * <p>包含在多个领域概念中使用的值对象:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.model.vo.shared.IdempotentKey} - 用于防止重复的幂等键
 *   <li>{@link com.patra.ingest.domain.model.vo.shared.LeaseInfo} - 用于分布式任务协调的租约信息
 *   <li>{@link com.patra.ingest.domain.model.vo.shared.NamespaceKey} - 用于多租户的命名空间键
 *   <li>{@link com.patra.ingest.domain.model.vo.shared.SliceSpec} - 用于计划分区的切片规范
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.domain.model.vo.shared;
