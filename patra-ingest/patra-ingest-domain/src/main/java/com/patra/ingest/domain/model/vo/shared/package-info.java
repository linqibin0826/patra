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
 * <p>测试 CI 优化效果：
 *
 * <ul>
 *   <li>验证变更检测是否正确识别 patra-ingest 模块
 *   <li>验证增量构建是否只构建变更模块及其依赖
 *   <li>验证缓存是否命中（pom.xml 未修改）
 *   <li>验证 Setup job 耗时是否 < 2-3 分钟
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.domain.model.vo.shared;
