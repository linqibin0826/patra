# 代码规范

1. 遵循 Google Java 开发规范（格式化、命名、注释、类组织等）
2. 变量、类、接口命名必须准确反映意图和抽象层次，抽象概念使用抽象命名（如 Repository、Service、Port），具体实现使用具体命名（如 PubMedRepository、MeshImportService、PubMedDataSourceAdapter），禁止模棱两可的命名（如 Manager、Helper、Util 等作为业务类名）
3. JavaDoc 使用 `///` 风格（而非传统的 `/** */`），内容使用 markdown 语法（而非 HTML 标签）。方法名和类型已经说清楚的方法可以不写；REST 接口方法必须写（注释会进 API 文档）。怎么写见下面的「注释标准」
4. 禁止在代码中使用全类名（Fully Qualified Name），必须使用 `import` 语句导入类型（特例：仅当类名冲突时使用全类名消歧义）
5. 优先使用 Lombok 注解（`@Getter`、`@Setter`、`@Data`、`@Builder`、`@AllArgsConstructor`、`@NoArgsConstructor` 等）生成 getter/setter/constructor/toString/equals/hashCode，仅在需要自定义逻辑时才手动编写

## 注释标准

前后端通用。读者没有 spec 和对话的背景；PR 转 ready 之前由 `comment-reviewer` 按这份标准审。

1. 类注释先说它是干什么的，不只列步骤
2. 写代码说不出来的东西：为什么这样做、依赖什么前提；不复述方法名和代码
3. 项目内部的词要解释，或换成普通的话
4. 含义不明的核心字段要写注释
5. Issue 号和 spec 可以引用，但离开它们也要读得懂
6. 改代码时同步改注释

## Record 类设计规范

### 工厂方法 vs @Builder

- **参数 ≤ 4 个**：使用静态工厂方法 `of()`，禁止使用 `@Builder`
- **参数 ≥ 5 个**：使用 `@Builder`，禁止同时提供 `of()` 方法（API 一致性）
- 工厂方法统一命名为 `of()`，语义化场景可用 `success()`、`failure()` 等

```java
// ✅ 简单 Record：使用 of()
public record PublishResult(String storageKey, int publishedCount) {
  public static PublishResult of(String storageKey, int publishedCount) {
    return new PublishResult(storageKey, publishedCount);
  }
}

// ✅ 复杂 Record：使用 @Builder
@Builder
public record PublicationDataReadyEvent(
    Long taskId, Long runId, ProvenanceCode provenanceCode,
    List<String> storageKeys, Integer totalPublicationCount,
    Integer successBatchCount, Integer failedBatchCount, Long timestamp) {}
```

### 防御性拷贝

Record 包含集合字段时，必须在紧凑构造器中进行防御性拷贝：

```java
public record ValidationResult(boolean isValid, List<String> errors) {
  public ValidationResult {
    errors = errors != null ? List.copyOf(errors) : List.of();
  }
}
```

## README 文档规范

- README 记录模块的**架构概览和核心设计**，不需要列举每个 API 端点
- API 端点文档由 OpenAPI/Swagger 自动生成

## 不可变集合规范

1. **空集合**：使用 `List.of()`、`Map.of()`、`Set.of()`，禁止 `Collections.emptyXxx()`
2. **防御性拷贝**：使用 `List.copyOf()`、`Map.copyOf()`、`Set.copyOf()`
3. **只读视图**：仅当需要返回内部集合的实时视图时使用 `Collections.unmodifiableXxx()`

```java
// ✅ 空集合
return List.of();

// ✅ 防御性拷贝（创建独立副本）
this.items = List.copyOf(items);

// ✅ 只读视图（反映内部集合的实时状态）
public Set<String> getRegisteredIds() {
  return Collections.unmodifiableSet(this.registeredIds);
}
```
