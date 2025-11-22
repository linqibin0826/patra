package com.patra.ingest.architecture.rules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;

/// Domain 层纯净性规则
///
/// Domain 层是六边形架构的核心，必须保持纯净：
///
/// - 零 Spring 框架依赖（完全禁止 org.springframework.*）
///   - 允许 Jackson 依赖（com.fasterxml.jackson.*）
///   - 不依赖其他业务层（-app/-infra/-adapter/-boot）
///   - 仅允许依赖：JDK、Lombok、Hutool、patra-common
///
/// **架构约束检查点：**
///
/// - 【CHK-ARCH-001】Domain 层必须是纯 Java（无框架依赖）
///
/// @author linqibin
/// @since 0.1.0
public final class DomainLayerRules {

  private DomainLayerRules() {
    // 工具类，禁止实例化
  }

  /// 规则 2: Domain 层完全禁止 Spring 框架依赖
  ///
  /// Domain 层不能依赖 Spring 的任何包，包括但不限于：
  ///
  /// - org.springframework.stereotype.*（@Component、@Service 等）
  ///   - org.springframework.transaction.*（@Transactional）
  ///   - org.springframework.beans.*（@Autowired）
  ///   - org.springframework.context.*（ApplicationContext）
  ///   - org.springframework.data.*（Spring Data）
  ///
  /// **原因：**
  ///
  /// **错误示例：**
  ///
  /// ```
  ///
  /// // ❌ 错误：Domain 层使用 Spring 注解
  /// {@literal @}Service
  /// public class PlanService { ... }
  ///
  /// // ❌ 错误：Domain 层使用 @Transactional
  /// {@literal @}Transactional
  /// public void createPlan() { ... }
  ///
  /// ```
  ///
  /// **正确示例：**
  ///
  /// ```
  ///
  /// // ✅ 正确：纯 Java 类
  /// public class PlanAggregate {
  ///     private final PlanId id;
  ///     private PlanStatus status;
  ///
  ///     public void start() {
  ///         // 业务逻辑
  ///     }
  /// }
  ///
  /// ```
  public static final ArchRule domain_should_not_depend_on_spring =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..")
          .as("Domain 层完全禁止 Spring 框架依赖")
          .because("Domain 层必须是纯 Java，保持框架无关性（六边形架构铁律 CHK-ARCH-001）");

  /// 规则 3: Domain 层允许的依赖白名单
  ///
  /// Domain 层仅允许依赖以下包：
  ///
  /// - java.* - JDK 标准库
  ///   - lombok.* - Lombok 注解（@Getter、@Builder 等）
  ///   - cn.hutool.* - Hutool 工具类
  ///   - com.fasterxml.jackson.* - Jackson 序列化（允许）
  ///   - com.patra.common.* - 项目通用库
  ///   - com.patra.ingest.domain.* - Domain 层内部
  ///
  /// **Jackson 依赖说明：** Domain 层允许使用 Jackson 注解（如 @JsonProperty、@JsonIgnore）用于：
  ///
  /// - 领域事件序列化
  ///   - 值对象 JSON 映射
  ///   - 快照持久化
  ///
  /// **不允许的依赖：**
  ///
  /// - org.springframework.* - Spring 框架
  ///   - com.baomidou.mybatisplus.* - MyBatis-Plus
  ///   - javax.persistence.* / jakarta.persistence.* - JPA
  ///   - com.patra.ingest.infra.* - 基础设施层
  ///   - com.patra.ingest.app.* - 应用层
  ///
  public static final ArchRule domain_allowed_dependencies =
      classes()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              "java..", // JDK 标准库
              "lombok..", // Lombok 注解
              "cn.hutool..", // Hutool 工具类
              "com.fasterxml.jackson..", // Jackson 序列化（允许）
              "com.patra.common..", // 项目通用库
              "com.patra.ingest.domain.." // Domain 层内部
              )
          .as("Domain 层仅允许依赖：JDK、Lombok、Hutool、Jackson、patra-common")
          .because("限制依赖范围确保 Domain 层纯净（CHK-ARCH-001）");

  /// 规则 1: Domain 层不依赖其他业务层
  ///
  /// Domain 层不能依赖以下业务层：
  ///
  /// - com.patra.ingest.app.* - 应用层
  ///   - com.patra.ingest.infra.* - 基础设施层
  ///   - com.patra.ingest.adapter.* - 适配器层
  ///   - com.patra.ingest.boot.* - 启动层
  ///
  /// **原因：**
  ///
  /// **错误示例：**
  ///
  /// ```
  ///
  /// // ❌ 错误：Domain 层依赖 Infra 层
  /// package com.patra.ingest.domain.service;
  ///
  /// import com.patra.ingest.infra.persistence.entity.PlanDO;  // ❌ 不允许
  ///
  /// public class PlanService {
  ///     private PlanDO plan;  // ❌ 直接依赖 DO
  /// }
  ///
  /// ```
  ///
  /// **正确示例：**
  ///
  /// ```
  ///
  /// // ✅ 正确：Domain 层定义接口，Infra 层实现
  /// package com.patra.ingest.domain.port;
  ///
  /// public interface PlanRepository {
  ///     void save(Plan plan);
  /// }
  ///
  /// ```
  public static final ArchRule domain_should_not_depend_on_other_layers =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..app..", "..infra..", "..adapter..", "..boot..")
          .as("Domain 层不依赖其他业务层（app/infra/adapter/boot）")
          .because("Domain 层是依赖图的最底层，其他层通过 DIP 依赖 Domain（CHK-ARCH-002）");
}
