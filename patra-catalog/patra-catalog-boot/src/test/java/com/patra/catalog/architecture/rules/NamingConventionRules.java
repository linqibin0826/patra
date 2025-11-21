package com.patra.catalog.architecture.rules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.lang.ArchRule;

/**
 * 命名约定规则
 *
 * <p>验证 DDD 模式的命名和包结构约定：
 *
 * <ul>
 *   <li>Port 接口：必须在 domain.port 包，命名以 Port 或 Repository 结尾
 *   <li>Repository 实现：必须在 infra 层，命名以 Impl 或 Adapter 结尾
 *   <li>DO 类：必须在 infra.persistence.entity 包，命名以 DO 结尾
 *   <li>Aggregate：必须在 domain.model.aggregate 包，命名以 Aggregate 结尾
 *   <li>Orchestrator：必须在 app.usecase 包，命名以 Orchestrator 或 Coordinator 结尾
 * </ul>
 *
 * <p>统一的命名约定有助于：
 *
 * <ul>
 *   <li>快速识别类的职责
 *   <li>维护代码的一致性
 *   <li>新开发者快速理解架构
 * </ul>
 *
 * @author linqibin
 * @since 2025-01-10
 */
public final class NamingConventionRules {

  private NamingConventionRules() {
    // 工具类，禁止实例化
  }

  /**
   * 规则 7: Port 接口必须在 domain.port 包
   *
   * <p>Port 接口是 Domain 层定义的契约，用于与外部世界交互：
   *
   * <ul>
   *   <li>输出端口（Driven Port）：Repository、ExternalService 等
   *   <li>命名模式：*Port、*Repository
   * </ul>
   *
   * <p><b>正确示例：</b>
   *
   * <pre>
   * // ✅ 正确位置和命名
   * package com.patra.catalog.domain.port;
   *
   * public interface MeshDataRepository { ... }
   * public interface PatraRegistryPort { ... }
   * public interface DataImportPort { ... }
   * </pre>
   *
   * <p><b>错误示例：</b>
   *
   * <pre>
   * // ❌ 错误：Port 接口在 app 层
   * package com.patra.catalog.app.port;
   * public interface MeshDataRepository { ... }
   *
   * // ❌ 错误：命名不符合约定
   * package com.patra.catalog.domain.port;
   * public interface MeshDataService { ... }  // 应该用 Port 或 Repository 结尾
   * </pre>
   */
  public static final ArchRule ports_should_reside_in_domain_port_package =
      classes()
          .that()
          .resideInAPackage("..domain.port..")
          .and()
          .areInterfaces()
          .and()
          .haveSimpleNameNotContaining("package-info") // 排除文档类
          .should()
          .haveSimpleNameEndingWith("Port")
          .orShould()
          .haveSimpleNameEndingWith("Repository")
          .as("Port 接口必须在 domain.port 包，命名以 Port 或 Repository 结尾")
          .because("统一的命名约定便于识别 Port 接口");

  /**
   * 规则 8: Port 接口实现必须在 infra 层
   *
   * <p>Port 接口的实现类必须位于 infra 层：
   *
   * <ul>
   *   <li>位置：infra.persistence.repository 或 infra.integration
   *   <li>实现：domain.port 包中定义的接口
   * </ul>
   *
   * <p><b>正确示例：</b>
   *
   * <pre>
   * // ✅ 正确：Port 实现在 infra 层
   * package com.patra.catalog.infra.persistence.repository;
   *
   * {@literal @}Repository
   * public class MeshDataRepositoryMpImpl implements MeshDataRepository {
   *     // MyBatis-Plus 实现
   * }
   * </pre>
   *
   * <p><b>错误示例：</b>
   *
   * <pre>
   * // ❌ 错误：Port 实现在 domain 层
   * package com.patra.catalog.domain.repository;
   * public class MeshDataRepositoryImpl implements MeshDataRepository { ... }
   *
   * // ❌ 错误：Port 实现在 app 层
   * package com.patra.catalog.app.repository;
   * public class MeshDataRepositoryImpl implements MeshDataRepository { ... }
   * </pre>
   */
  public static final ArchRule repository_impls_should_reside_in_infra =
      classes()
          .that()
          .resideInAPackage("..catalog..") // 限制在 catalog 模块
          .and()
          .areNotInterfaces()
          .and()
          .implement(
              com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage(
                  "..domain.port.."))
          .should()
          .resideInAPackage("..infra..")
          .as("Domain Port 接口的实现类必须在 infra 层")
          .because("Infra 层负责实现 Domain 层定义的 Port 接口（六边形架构依赖倒置原则）");

  /**
   * 规则 9: DO 类必须在 infra.persistence.entity 包
   *
   * <p>DO（Data Object）是数据库表的映射实体，仅用于持久化层：
   *
   * <ul>
   *   <li>位置：infra.persistence.entity
   *   <li>命名模式：*DO
   *   <li>职责：与 MyBatis-Plus 映射
   * </ul>
   *
   * <p><b>正确示例：</b>
   *
   * <pre>
   * // ✅ 正确：DO 类在 infra.persistence.entity 包
   * package com.patra.catalog.infra.persistence.entity;
   *
   * {@literal @}TableName("mesh_data")
   * public class MeshDataDO {
   *     {@literal @}TableId
   *     private Long id;
   *     // ...
   * }
   * </pre>
   *
   * <p><b>错误示例：</b>
   *
   * <pre>
   * // ❌ 错误：DO 类在 domain 层
   * package com.patra.catalog.domain.model;
   * public class MeshDataDO { ... }
   *
   * // ❌ 错误：DO 类在 app 层
   * package com.patra.catalog.app.dto;
   * public class MeshDataDO { ... }
   * </pre>
   */
  public static final ArchRule do_classes_should_reside_in_persistence_entity =
      classes()
          .that()
          .haveSimpleNameEndingWith("DO")
          .should()
          .resideInAPackage("..infra.persistence.entity..")
          .as("DO 类必须在 infra.persistence.entity 包")
          .because("DO 类是持久化层的概念，不应泄露到其他层（CHK-ARCH-004）");

  /**
   * 规则 10: Aggregate 必须在 domain.model.aggregate 包
   *
   * <p>Aggregate（聚合根）是 DDD 的核心概念，必须位于 domain 层：
   *
   * <ul>
   *   <li>位置：domain.model.aggregate
   *   <li>命名模式：*Aggregate
   *   <li>职责：业务不变量、状态机、领域逻辑
   * </ul>
   *
   * <p><b>正确示例：</b>
   *
   * <pre>
   * // ✅ 正确：Aggregate 在 domain.model.aggregate 包
   * package com.patra.catalog.domain.model.aggregate;
   *
   * public class MeshImportAggregate {
   *     private MeshImportId id;
   *     private ImportStatus status;
   *
   *     public void startImport() {
   *         // 业务不变量检查
   *         // 状态机转换
   *     }
   * }
   * </pre>
   *
   * <p><b>错误示例：</b>
   *
   * <pre>
   * // ❌ 错误：Aggregate 在 app 层
   * package com.patra.catalog.app.model;
   * public class MeshImportAggregate { ... }
   *
   * // ❌ 错误：Aggregate 在 infra 层
   * package com.patra.catalog.infra.model;
   * public class MeshImportAggregate { ... }
   * </pre>
   */
  public static final ArchRule aggregates_should_reside_in_domain_model_aggregate =
      classes()
          .that()
          .haveSimpleNameEndingWith("Aggregate")
          .should()
          .resideInAPackage("..domain.model.aggregate..")
          .as("Aggregate 必须在 domain.model.aggregate 包")
          .because("Aggregate 是 DDD 的核心概念，属于 Domain 层");

  /**
   * 规则 11: Orchestrator/Coordinator 必须在 app.usecase 包
   *
   * <p>Orchestrator 和 Coordinator 是应用层的编排器，负责：
   *
   * <ul>
   *   <li>用例流程编排
   *   <li>事务边界管理
   *   <li>多个 Domain 聚合的协调
   * </ul>
   *
   * <p><b>命名模式：</b>
   *
   * <ul>
   *   <li>*Orchestrator：主编排器（如 MeshImportOrchestrator）
   *   <li>*Coordinator：辅助协调器（如 DataSyncCoordinator）
   * </ul>
   *
   * <p><b>正确示例：</b>
   *
   * <pre>
   * // ✅ 正确：Orchestrator 在 app.usecase 包
   * package com.patra.catalog.app.usecase.mesh;
   *
   * {@literal @}Service
   * {@literal @}Transactional
   * public class MeshImportOrchestrator {
   *     public MeshImportResult importData(MeshImportCommand command) {
   *         // 编排多个 Domain 聚合和服务
   *     }
   * }
   * </pre>
   *
   * <p><b>错误示例：</b>
   *
   * <pre>
   * // ❌ 错误：Orchestrator 在 domain 层
   * package com.patra.catalog.domain.service;
   * public class MeshImportOrchestrator { ... }
   *
   * // ❌ 错误：Orchestrator 在 adapter 层
   * package com.patra.catalog.adapter.scheduler;
   * public class MeshImportOrchestrator { ... }
   * </pre>
   */
  public static final ArchRule orchestrators_should_reside_in_app_usecase =
      classes()
          .that()
          .haveSimpleNameEndingWith("Orchestrator")
          .or()
          .haveSimpleNameEndingWith("Coordinator")
          .should()
          .resideInAPackage("..app.usecase..")
          .as("Orchestrator/Coordinator 必须在 app.usecase 包")
          .because("编排器是应用层概念，负责用例流程编排和事务边界（CHK-ARCH-003）");
}
