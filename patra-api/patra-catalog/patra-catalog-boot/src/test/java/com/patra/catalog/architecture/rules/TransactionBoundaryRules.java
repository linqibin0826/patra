package com.patra.catalog.architecture.rules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;

/// 事务边界规则
///
/// 验证事务边界的正确位置：
///
/// - @Transactional 只能在 app 层（应用层编排器）
///   - Domain 层严禁使用 @Transactional（保持纯净）
///
/// **架构约束检查点：**
///
/// - 【CHK-ARCH-003】事务边界在 Orchestrator（应用层）
///
/// **事务管理原则：**
///
/// @author linqibin
/// @since 0.1.0
public final class TransactionBoundaryRules {

  private TransactionBoundaryRules() {
    // 工具类，禁止实例化
  }

  /// 规则 15: @Transactional 只能在 app 层
  ///
  /// 事务边界应该在应用层的编排器中定义：
  ///
  /// - Orchestrator：主编排器，通常需要事务（如 MeshImportOrchestrator）
  ///   - Coordinator：辅助协调器，可能需要事务（如 DataSyncCoordinator）
  ///   - UseCase：用例实现类，按需使用事务
  ///
  /// **原因：**
  ///
  /// **正确示例：**
  ///
  /// ```
  ///
  /// // ✅ 正确：Orchestrator 在 app 层使用事务
  /// package com.patra.catalog.app.usecase.mesh;
  ///
  /// {@literal @}Service
  /// public class MeshImportOrchestrator {
  ///
  ///     {@literal @}Transactional  // ✅ 事务边界在 app 层
  ///     public MeshImportResult importData(MeshImportCommand command) {
  ///         // 1. 验证导入参数
  ///         // 2. 加载表配置
  ///         // 3. 批量导入数据
  ///         // 4. 更新进度
  ///         // 5. 发布事件到 Outbox
  ///         // 以上操作要么全部成功，要么全部回滚
  ///     }
  /// }
  ///
  /// ```
  ///
  /// **错误示例：**
  ///
  /// ```
  ///
  /// // ❌ 错误：Domain 层使用事务
  /// package com.patra.catalog.domain.service;
  ///
  /// {@literal @}Transactional  // ❌ 不允许
  /// public class MeshDataService {
  ///     public void importData() { ... }
  /// }
  ///
  /// // ❌ 错误：Adapter 层使用事务
  /// package com.patra.catalog.adapter.scheduler;
  ///
  /// {@literal @}Transactional  // ❌ 不推荐，应该委托给 app 层
  /// public void importJob() { ... }
  ///
  /// ```
  ///
  /// **注意事项：**
  ///
  /// - Adapter 层（如 Controller、Job）应该是瘦层，只负责触发 app 层用例
  ///   - 如果 Adapter 层需要事务，应该委托给 app 层的 Orchestrator
  ///   - Infra 层的 Repository 实现不应该有 @Transactional，事务由调用方管理
  ///
  public static final ArchRule transactions_only_in_app_layer =
      classes()
          .that()
          .areAnnotatedWith(Transactional.class)
          .should()
          .resideInAPackage("..app..")
          .as("@Transactional 只能在 app 层")
          .because("事务边界应该在应用层的用例编排器中定义（六边形架构原则 CHK-ARCH-003）");

  /// 规则 16: Domain 层严禁使用 @Transactional
  ///
  /// Domain 层必须保持纯净，不能依赖 Spring 的 @Transactional 注解：
  ///
  /// - Domain 层只包含业务逻辑
  ///   - 事务是技术实现细节，属于基础设施关注点
  ///   - Domain 层不应该知道事务的存在
  ///
  /// **原因：**
  ///
  /// **错误示例：**
  ///
  /// ```
  ///
  /// // ❌ 错误：Domain 层使用 @Transactional
  /// package com.patra.catalog.domain.service;
  ///
  /// import org.springframework.transaction.annotation.Transactional;
  ///
  /// public class MeshDataService {
  ///     {@literal @}Transactional  // ❌ 违反 Domain 层纯净性
  ///     public void importData() {
  ///         // 业务逻辑
  ///     }
  /// }
  ///
  /// ```
  ///
  /// **正确示例：**
  ///
  /// ```
  ///
  /// // ✅ 正确：Domain 层纯 Java 方法
  /// package com.patra.catalog.domain.service;
  ///
  /// public class MeshDataService {
  ///     // ✅ 无 Spring 注解，纯业务逻辑
  ///     public MeshData importData(TableConfig config, List<Record> records) {
  ///         // 业务不变量检查
  ///         // 数据转换
  ///         // 返回领域对象
  ///         return meshData;
  ///     }
  /// }
  ///
  /// // ✅ 事务由 app 层管理
  /// package com.patra.catalog.app.usecase;
  ///
  /// {@literal @}Service
  /// public class MeshImportOrchestrator {
  ///     {@literal @}Transactional  // ✅ 事务在这里
  ///     public void orchestrate() {
  ///         MeshData data = meshDataService.importData(...);  // 调用 Domain 层
  ///         meshDataRepository.save(data);                    // 持久化
  ///     }
  /// }
  ///
  /// ```
  public static final ArchRule domain_should_not_use_transactions =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .beAnnotatedWith(Transactional.class)
          .as("Domain 层严禁使用 @Transactional")
          .because("Domain 层必须保持纯净，事务是技术关注点，应该在 app 层管理（CHK-ARCH-001）");
}
