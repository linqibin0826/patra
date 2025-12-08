package com.patra.starter.test.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tngtech.archunit.lang.ArchRule;

/// MyBatis-Plus 架构规则。
///
/// 提供与 MyBatis-Plus 使用相关的架构约束，确保批量插入等操作遵循最佳实践。
///
/// ### 规则概要
///
/// 1. **禁止 ServiceImpl 继承**：RepositoryAdapter 不应继承 ServiceImpl，应使用 `Db.saveBatch()`
/// 2. **批量插入规范**：数据量 > 100 条必须使用 `Db.saveBatch()` 批量插入，禁止循环 insert
///
/// ### 使用方式
///
/// ```java
/// class IngestArchitectureTest {
///
///     @ArchTest
///     static final ArchRule noServiceImplInRepository =
// MyBatisArchRules.noServiceImplInRepository();
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class MyBatisArchRules {

  private MyBatisArchRules() {
    // 工具类禁止实例化
  }

  /// 禁止 RepositoryAdapter 继承 ServiceImpl。
  ///
  /// ServiceImpl 的 saveBatch() 方法底层是循环 INSERT，性能较差。
  /// 应使用 `Db.saveBatch()` 静态方法进行批量插入，配合 `rewriteBatchedStatements=true` JDBC 参数。
  ///
  /// ### 违规示例
  ///
  /// ```java
  /// // ❌ 违规：继承 ServiceImpl
  /// public class MeshQualifierRepositoryAdapter
  ///     extends ServiceImpl<MeshQualifierMapper, MeshQualifierDO>
  ///     implements MeshQualifierRepository {
  ///
  ///     public void saveBatch(List<MeshQualifierAggregate> qualifiers) {
  ///         super.saveBatch(dataObjects);  // 底层是循环 INSERT
  ///     }
  /// }
  /// ```
  ///
  /// ### 正确示例
  ///
  /// ```java
  /// // ✅ 正确：使用 Db.saveBatch() 进行批量插入
  /// @RequiredArgsConstructor
  /// public class MeshQualifierRepositoryAdapter implements MeshQualifierRepository {
  ///
  ///     public void saveBatch(List<MeshQualifierAggregate> qualifiers) {
  ///         List<MeshQualifierDO> dataObjects = qualifiers.stream()
  ///             .map(converter::toDataObject)
  ///             .toList();
  ///         Db.saveBatch(dataObjects);  // 自动 ID 回填和审计字段填充
  ///     }
  /// }
  /// ```
  ///
  /// @return ArchRule 规则实例
  public static ArchRule noServiceImplInRepository() {
    return noClasses()
        .that()
        .haveSimpleNameEndingWith("RepositoryAdapter")
        .should()
        .beAssignableTo(ServiceImpl.class)
        .as("RepositoryAdapter 禁止继承 ServiceImpl")
        .because(
            "ServiceImpl.saveBatch() 底层是循环 INSERT，性能差。"
                + "应使用 Db.saveBatch() 进行批量插入，配合 rewriteBatchedStatements=true 实现高效批量操作");
  }

  /// 禁止 infra 层类继承 ServiceImpl。
  ///
  /// 这是一个更宽泛的规则，禁止 infra 层的任何类继承 ServiceImpl。
  ///
  /// @return ArchRule 规则实例
  public static ArchRule noServiceImplInInfraLayer() {
    return noClasses()
        .that()
        .resideInAPackage("..infra..")
        .should()
        .beAssignableTo(ServiceImpl.class)
        .as("Infra 层禁止继承 ServiceImpl")
        .because("ServiceImpl 是 MyBatis-Plus 的 Service 层实现，" + "在六边形架构中应使用 Mapper 接口直接操作数据库");
  }
}
