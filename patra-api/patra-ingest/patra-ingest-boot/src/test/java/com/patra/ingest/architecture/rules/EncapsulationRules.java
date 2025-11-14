package com.patra.ingest.architecture.rules;

import static com.tngtech.archunit.core.domain.JavaModifier.PUBLIC;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;

/**
 * 封装规则
 *
 * <p>验证六边形架构的封装约束：
 *
 * <ul>
 *   <li>DO 类不能被 infra 层外部访问（防止持久化细节泄露）
 *   <li>Port 接口必须是 public（作为 Domain 层的公开契约）
 *   <li>Domain Event 必须在 domain.event 包（事件驱动架构）
 * </ul>
 *
 * <p><b>架构约束检查点：</b>
 *
 * <ul>
 *   <li>【CHK-ARCH-004】永不暴露 DO（DO 实体不能离开基础设施层）
 * </ul>
 *
 * @author linqibin
 * @since 2025-01-10
 */
public final class EncapsulationRules {

  private EncapsulationRules() {
    // 工具类，禁止实例化
  }

  /**
   * 规则 12: DO 类不能被 infra 层外部访问
   *
   * <p>DO（Data Object）是持久化层的内部实现细节，不应暴露给其他层：
   *
   * <ul>
   *   <li>DO 仅在 infra.persistence 包内部使用
   *   <li>DO 与 Domain 实体通过 Converter 转换
   *   <li>其他层（app/adapter/domain）不能直接依赖 DO
   * </ul>
   *
   * <p><b>原因：</b>
   *
   * <ol>
   *   <li>隔离持久化细节（表结构变化不影响业务逻辑）
   *   <li>Domain 实体与数据库实体解耦
   *   <li>避免贫血模型（DO 只是数据容器）
   *   <li>便于切换持久化方案（如从 MyBatis 切换到 JPA）
   * </ol>
   *
   * <p><b>错误示例：</b>
   *
   * <pre>
   * // ❌ 错误：App 层直接使用 DO
   * package com.patra.ingest.app.usecase;
   *
   * import com.patra.ingest.infra.persistence.entity.PlanDO;  // ❌ 不允许
   *
   * public class PlanOrchestrator {
   *     public void process(PlanDO plan) { ... }  // ❌ 泄露 DO
   * }
   * </pre>
   *
   * <p><b>正确示例：</b>
   *
   * <pre>
   * // ✅ 正确：App 层使用 Domain 实体
   * package com.patra.ingest.app.usecase;
   *
   * import com.patra.ingest.domain.model.aggregate.PlanAggregate;  // ✅ Domain 实体
   *
   * public class PlanOrchestrator {
   *     public void process(PlanAggregate plan) { ... }  // ✅ 使用 Domain 实体
   * }
   *
   * // ✅ 正确：Infra 层内部转换
   * package com.patra.ingest.infra.persistence.converter;
   *
   * {@literal @}Mapper
   * public interface PlanConverter {
   *     PlanAggregate toDomain(PlanDO planDO);  // DO → Domain
   *     PlanDO toDO(PlanAggregate plan);        // Domain → DO
   * }
   * </pre>
   */
  public static final ArchRule data_objects_should_not_be_accessed_outside_infra =
      noClasses()
          .that()
          .resideOutsideOfPackage("..infra..")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("DO")
          .as("DO 类不能被 infra 层外部访问")
          .because("DO 是持久化层的内部实现细节，不应泄露到其他层（六边形架构铁律 CHK-ARCH-004）");

  /**
   * 规则 13: Port 接口必须是 public
   *
   * <p>Port 接口是 Domain 层向外部暴露的契约，必须是 public：
   *
   * <ul>
   *   <li>Domain 层定义 Port 接口（输出端口）
   *   <li>Infra 层实现 Port 接口
   *   <li>App 层通过 Port 接口调用 Infra 实现
   * </ul>
   *
   * <p><b>原因：</b>
   *
   * <ol>
   *   <li>Port 接口是跨层调用的桥梁
   *   <li>需要在不同模块（-domain/-app/-infra）间共享
   *   <li>符合依赖倒置原则（DIP）
   * </ol>
   *
   * <p><b>正确示例：</b>
   *
   * <pre>
   * // ✅ 正确：Port 接口是 public
   * package com.patra.ingest.domain.port;
   *
   * public interface PlanRepository {  // ✅ public 接口
   *     void save(Plan plan);
   *     Optional&lt;Plan&gt; findById(PlanId id);
   * }
   * </pre>
   *
   * <p><b>错误示例：</b>
   *
   * <pre>
   * // ❌ 错误：Port 接口不是 public
   * package com.patra.ingest.domain.port;
   *
   * interface PlanRepository {  // ❌ 包私有，其他模块无法访问
   *     void save(Plan plan);
   * }
   * </pre>
   */
  public static final ArchRule ports_should_be_public =
      classes()
          .that()
          .resideInAPackage("..domain.port..")
          .and()
          .areInterfaces()
          .and()
          .haveSimpleNameNotContaining("package-info") // 排除文档类
          .should()
          .haveModifier(PUBLIC)
          .as("Port 接口必须是 public")
          .because("Port 接口是 Domain 层的公开契约，需要跨模块访问");

  /**
   * 规则 14: Domain Event 必须在 domain.event 包
   *
   * <p>Domain Event（领域事件）是 DDD 事件驱动架构的核心：
   *
   * <ul>
   *   <li>位置：domain.event 包
   *   <li>命名模式：*Event
   *   <li>职责：表示领域中发生的重要业务事件
   * </ul>
   *
   * <p><b>作用：</b>
   *
   * <ol>
   *   <li>解耦聚合之间的依赖
   *   <li>实现最终一致性
   *   <li>支持事件溯源
   *   <li>触发副作用（如发送通知、更新缓存）
   * </ol>
   *
   * <p><b>正确示例：</b>
   *
   * <pre>
   * // ✅ 正确：Event 在 domain.event 包
   * package com.patra.ingest.domain.event;
   *
   * public record TaskQueuedEvent(
   *     String taskId,
   *     String planId,
   *     Instant queuedAt
   * ) implements DomainEvent {
   * }
   * </pre>
   *
   * <p><b>错误示例：</b>
   *
   * <pre>
   * // ❌ 错误：Event 在 app 层
   * package com.patra.ingest.app.event;
   * public record TaskQueuedEvent(...) { }
   *
   * // ❌ 错误：Event 在 domain.model 包
   * package com.patra.ingest.domain.model;
   * public record TaskQueuedEvent(...) { }
   * </pre>
   *
   * <p><b>例外：</b> CursorEvent 是事件溯源模式的审计实体，不是领域事件，因此被排除。
   */
  public static final ArchRule events_should_reside_in_domain_event =
      classes()
          .that()
          .haveSimpleNameEndingWith("Event")
          .and()
          .resideInAPackage("..domain..")
          .and()
          .haveSimpleNameNotEndingWith("CursorEvent") // 排除事件溯源实体
          .should()
          .resideInAPackage("..domain.event..")
          .as("Domain Event 必须在 domain.event 包")
          .because("领域事件是 DDD 事件驱动架构的核心，应统一管理");
}
