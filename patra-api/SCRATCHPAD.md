# SCRATCHPAD.md - 工作记忆

> **状态**：✅ 已完成
> **任务名称**：改造逻辑删除
> **开始时间**：2026-01-11
> **完成时间**：2026-01-11
> **更新者**：Claude

---

## 🎯 当前任务

**目标**：将自定义的逻辑删除实现改造为 JPA 原生的 `@SoftDelete` 注解，使用 `SoftDeleteType.TIMESTAMP` 策略

**进度**：
- [x] 分析现有实现与 JPA @SoftDelete 的差异
- [x] 设计改造方案
- [x] 修改 SoftDeletableJpaEntity 基类
- [x] 修改 SoftDeletableChildJpaEntity 基类
- [x] 处理 SoftDeletable 接口（已删除）
- [x] 更新 Repository 层的软删除方法（移除 softDeleteById，直接使用 delete）
- [x] 更新测试
- [x] 编译验证
- [x] 架构测试验证
- [x] 更新 README.md 文档

---

## 📋 关键决策

| 日期 | 决策 | 原因 |
|------|------|------|
| 2026-01-11 | 使用 `@SoftDelete(strategy = SoftDeleteType.TIMESTAMP)` | Hibernate 7.x 原生支持时间戳策略，无需自定义 Converter |
| 2026-01-11 | 删除 `SoftDeletable` 接口 | `@SoftDelete` 由 Hibernate 自动管理 `deleted_at` 列，不暴露实体字段 |
| 2026-01-11 | 移除 `softDeleteById()` 方法 | 直接调用 `repository.delete()` 或 `entityManager.remove()`，Hibernate 自动转换为 UPDATE |

---

## ✅ 已解决问题

| 问题 | 解决方案 |
|------|----------|
| `SoftDeletable` 接口是否保留？ | **删除**。`@SoftDelete` 不需要实体字段，Hibernate 自动管理 `deleted_at` 列 |
| `softDeleteById()` 方法如何改造？ | **移除**。直接使用 `repository.delete(entity)` 或 `repository.deleteById(id)` |
| 如何查询已删除的记录？ | 使用 **Native Query** 绕过 Hibernate 自动过滤：`@Query(value = "SELECT * FROM xxx WHERE deleted_at IS NOT NULL", nativeQuery = true)` |

---

## 📁 变更文件汇总

**已删除**：
- `patra-spring-boot-starter-jpa/src/main/java/com/patra/starter/jpa/entity/SoftDeletable.java`
- `patra-spring-boot-starter-jpa/src/test/java/com/patra/starter/jpa/entity/SoftDeletableTest.java`

**已修改**：
- `patra-spring-boot-starter-jpa/src/main/java/com/patra/starter/jpa/entity/SoftDeletableJpaEntity.java` - 添加 `@SoftDelete` 注解
- `patra-spring-boot-starter-jpa/src/main/java/com/patra/starter/jpa/entity/SoftDeletableChildJpaEntity.java` - 添加 `@SoftDelete` 注解
- `patra-spring-boot-starter-jpa/src/test/java/com/patra/starter/jpa/entity/SoftDeletableJpaEntityTest.java` - 更新测试
- `patra-spring-boot-starter-jpa/src/test/java/com/patra/starter/jpa/entity/SoftDeletableChildJpaEntityTest.java` - 更新测试
- `patra-spring-boot-starter-jpa/README.md` - 更新文档
- `.claude/rules/tech/jpa.md` - 更新技术规范
- 多个 JpaMapper 文件 - 移除 `deletedAt` 字段映射

---

## 🧠 技术要点备忘

### @SoftDelete 使用方式

```java
@MappedSuperclass
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public abstract class SoftDeletableJpaEntity extends BaseJpaEntity {
    // 无需 deletedAt 字段，Hibernate 自动管理
}
```

### 软删除操作

```java
// 软删除（Hibernate 自动转换为 UPDATE deleted_at = CURRENT_TIMESTAMP）
repository.delete(entity);
repository.deleteById(id);
entityManager.remove(entity);

// 查询自动排除已删除记录
repository.findAll(); // WHERE deleted_at IS NULL
```

### 查询已删除记录

```java
@Query(value = "SELECT * FROM cat_venue WHERE deleted_at IS NOT NULL", nativeQuery = true)
List<VenueEntity> findDeleted();
```

---

> **下一步**：可使用 `/new-task` 清理此工作记忆并开始新任务
