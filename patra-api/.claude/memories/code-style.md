# 代码规范

1. 遵循 Google Java 开发规范（格式化、命名、注释、类组织等）
2. 变量、类、接口命名必须准确反映意图和抽象层次，抽象概念使用抽象命名（如 Repository、Service、Port），具体实现使用具体命名（如 PubMedRepository、MeshImportService、PubMedDataSourceAdapter），禁止模棱两可的命名（如 Manager、Helper、Util 等作为业务类名）
3. 所有方法（任何访问级别）必须编写 JavaDoc 注释 ，使用 `///` 风格（而非传统的 `/** */`），内容使用 markdown 语法（而非 HTML 标签）
4. 禁止在代码中使用全类名（Fully Qualified Name），必须使用 `import` 语句导入类型（特例：仅当类名冲突时使用全类名消歧义）
5. 优先使用 Lombok 注解（`@Getter`、`@Setter`、`@Data`、`@Builder`、`@AllArgsConstructor`、`@NoArgsConstructor` 等）生成 getter/setter/constructor/toString/equals/hashCode，仅在需要自定义逻辑时才手动编写
