# 代码规范

1. 遵循 Google Java 开发规范（格式化、命名、注释、类组织等）
2. 变量、类、接口命名必须准确反映意图和抽象层次，抽象概念使用抽象命名（如 Repository、Service、Port），具体实现使用具体命名（如 PubMedRepository、MeshImportService、PubMedDataSourceAdapter），禁止模棱两可的命名（如 Manager、Helper、Util 等作为业务类名）
3. 所有方法（任何访问级别）必须编写 JavaDoc 注释 ，使用 `///` 风格（而非传统的 `/** */`），内容使用 markdown 语法（而非 HTML 标签）
4. 禁止在代码中使用全类名（Fully Qualified Name），必须使用 `import` 语句导入类型（特例：仅当类名冲突时使用全类名消歧义）
5. 优先使用 Lombok 注解（`@Getter`、`@Setter`、`@Data`、`@Builder`、`@AllArgsConstructor`、`@NoArgsConstructor` 等）生成 getter/setter/constructor/toString/equals/hashCode，仅在需要自定义逻辑时才手动编写

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
