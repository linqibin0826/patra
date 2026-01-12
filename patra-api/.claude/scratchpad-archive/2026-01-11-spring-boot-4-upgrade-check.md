# SCRATCHPAD.md - 工作记忆

> **状态**：✅ 已完成
> **任务名称**：Spring Boot 4.0.1 升级后检查
> **开始时间**：2026-01-11
> **完成时间**：2026-01-11
> **更新者**：Claude

---

## 🎯 当前任务

**目标**：检查 Spring Boot 3.5.7 → 4.0.1 升级后需要进行的迁移工作，确保项目正常运行

**进度**：
- [x] 检查编译状态（通过）
- [x] 分析 Spring Boot 4.x 重大变更
- [x] 检查废弃 API 使用情况
- [x] 运行测试验证功能
- [x] 必要的代码调整

---

## 📋 关键决策

| 日期 | 决策 | 原因 |
|------|------|------|
| 2026-01-11 | API 模块跳过测试执行 | 没有测试代码的模块配置 `<skipTests>true</skipTests>` 避免 surefire 报错 |
| 2026-01-11 | 软删除测试使用 Native SQL | `@SoftDelete` 不暴露 setter，测试中使用 Native Query 模拟已删除记录 |

---

## ✅ 已解决问题

| 问题 | 解决方案 |
|------|----------|
| maven-surefire-plugin 报错 `groups/excludedGroups require JUnit5` | 给没有测试的 API 模块添加 `<skipTests>true</skipTests>` |
| `setDeletedAt()` 方法不存在导致编译失败 | 测试改用 EntityManager Native SQL 设置 `deleted_at` |

---

## 🧠 Spring Boot 4.0 迁移检查清单

### ✅ 已确认无需修改

1. **@MockBean/@SpyBean**：项目已使用 `@MockitoBean`（Spring Boot 4.0 推荐）
2. **@JsonComponent**：项目未使用（无需迁移到 `@JacksonComponent`）
3. **@SpringBootTest + MockMvc**：项目未使用 MockMvc
4. **配置属性变更**：未使用 `spring.session.*` 或 `spring.data.mongodb.*`

### ⚠️ 已知警告（无需立即处理）

1. **Mockito agent 警告**：提示配置 Mockito agent，当前不影响测试运行
2. **ByteBuddy 警告**：`UsingUnsafe$Dispatcher$CreationAction` 提示，不影响功能

---

## 📁 变更文件汇总

**修改**：
- `patra-registry/patra-registry-api/pom.xml` - 添加 skipTests
- `patra-ingest/patra-ingest-api/pom.xml` - 添加 skipTests
- `patra-object-storage/patra-object-storage-api/pom.xml` - 添加 skipTests
- `patra-registry/patra-registry-infra/src/test/java/.../DictionaryRepositoryAdapterIT.java` - 软删除测试改用 Native SQL
