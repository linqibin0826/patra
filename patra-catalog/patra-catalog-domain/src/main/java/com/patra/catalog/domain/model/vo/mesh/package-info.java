/// MeSH 主题词值对象包。
///
/// 包含 MeSH 主题词相关的值对象（Value Object），封装不可变的业务概念。
///
/// ## 职责
///
/// - **业务概念封装**：将 MeSH 主题词的复杂业务概念封装为不可变对象
///   - **格式验证**：确保值对象的格式符合 NLM 官方规范
///   - **类型安全**：通过强类型避免字符串魔法值
///   - **不可变性**：使用 record 或 final 字段确保值对象不可变
///
/// ## 核心组件
///
/// ### 标识符值对象
///
/// - {@link com.patra.catalog.domain.model.vo.mesh.MeshUI} - MeSH 唯一标识符
///
/// - 封装 Descriptor UI、Qualifier UI、Concept UI、Term UI
///       - 支持新旧两种格式（2013年前后的格式变更）
///       - 格式验证：D/Q + 6或9位数字，M + 7或9位数字，T + 6或9位数字
///       - 类型判断：isDescriptor()、isQualifier()、isConcept()、isTerm()
///
/// ### 关联关系值对象
///
/// - {@link com.patra.catalog.domain.model.vo.mesh.AllowableQualifier} - 允许的限定词
///
/// - 表示主题词允许使用的限定词及其缩写（如 "immunology" / "IM"）
///   - {@link com.patra.catalog.domain.model.vo.mesh.PharmacologicalAction} - 药理作用
///
/// - 表示主题词的药理作用信息（仅药物类主题词）
///   - {@link com.patra.catalog.domain.model.vo.mesh.SeeRelatedDescriptor} - 相关主题词引用
///
/// - 表示主题词的相关引用（"另见"关系）
///   - {@link com.patra.catalog.domain.model.vo.mesh.ConceptRelation} - 概念关系
///
/// - 表示概念之间的关系（如 narrower、broader、related）
///
/// ## 设计原则
///
/// - **不可变性**：使用 record 或 final 字段，值对象创建后不能修改
///   - **自我验证**：在构造器中验证格式和业务规则
///   - **值相等性**：通过属性值判断相等性，而非对象引用
///   - **无副作用**：值对象的方法不能修改状态
///
/// ## 值对象 vs 实体
///
/// | 特性 | 值对象（Value Object） | 实体（Entity） |
/// |------|----------------------|---------------|
/// | 标识符 | 无 ID，通过属性判断相等性 | 有 ID，通过 ID 判断相等性 |
/// | 可变性 | 不可变（修改需创建新实例） | 可变（状态可修改） |
/// | 生命周期 | 可以在不同聚合间共享 | 由聚合根管理 |
/// | 示例 | MeshUI、AllowableQualifier | MeshConcept、MeshEntryTerm |
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：创建 MeSH UI 值对象
/// MeshUI descriptorUi = MeshUI.of("D000001"); // 主题词 UI
/// MeshUI qualifierUi = MeshUI.of("Q000123");  // 限定词 UI
/// MeshUI conceptUi = MeshUI.of("M0000001");   // 概念 UI
///
/// // 类型判断
/// assert descriptorUi.isDescriptor();
/// assert qualifierUi.isQualifier();
/// assert conceptUi.isConcept();
///
/// // 示例 2：使用工厂方法创建特定类型 UI
/// MeshUI descriptorUi = MeshUI.descriptorOf(1);     // D000001
/// MeshUI qualifierUi = MeshUI.qualifierOf(123);     // Q000123
/// MeshUI conceptUi = MeshUI.conceptOf(1);           // M0000001
///
/// // 示例 3：值对象的不可变性
/// MeshUI ui1 = MeshUI.of("D000001");
/// MeshUI ui2 = MeshUI.of("D000001");
/// assert ui1.equals(ui2); // 值相等性
/// assert ui1 != ui2;      // 不同对象引用
///
/// // 示例 4：AllowableQualifier 值对象
/// AllowableQualifier qualifier = AllowableQualifier.of(
///     MeshUI.qualifierOf(123),
///     "immunology",
///     "IM"
/// );
/// log.info("限定词：{} ({})", qualifier.getName(), qualifier.getAbbreviation());
///
/// // 示例 5：在聚合根中使用值对象
/// public class MeshDescriptorAggregate {
///     private MeshUI descriptorUi; // 强类型值对象，而非 String
///     private List<AllowableQualifier> allowableQualifiers; // 值对象集合
///
///     public void addAllowableQualifier(MeshUI qualifierUi, String name, String abbreviation) {
///         AllowableQualifier qualifier = AllowableQualifier.of(qualifierUi, name, abbreviation);
///         this.allowableQualifiers.add(qualifier);
///     }
/// }
///
/// // 示例 6：值对象的格式验证
/// try {
///     MeshUI invalidUi = MeshUI.of("INVALID"); // 抛出 IllegalArgumentException
/// } catch (IllegalArgumentException e) {
///     log.error("MeSH UI 格式无效：{}", e.getMessage());
/// }
/// ```
///
/// ## Record vs Class
///
/// **推荐使用 record**（当值对象只包含数据时）：
///
/// ```java
/// // ✅ 使用 record（简洁）
/// public record MeshUI(String ui) implements Serializable {
///     // 紧凑构造器：验证逻辑
///     public MeshUI {
///         Assert.notBlank(ui, "MeSH UI 不能为空");
///         Assert.isTrue(ui.matches(PATTERN), "格式无效");
///     }
/// }
///
/// // 可选：使用 class（当需要额外逻辑时）
/// @Getter
/// public class AllowableQualifier implements Serializable {
///     private final MeshUI qualifierUi;
///     private final String name;
///     private final String abbreviation;
///
///     // 私有构造器 + 工厂方法
///     private AllowableQualifier(MeshUI qualifierUi, String name, String abbreviation) {
///         this.qualifierUi = qualifierUi;
///         this.name = name;
///         this.abbreviation = abbreviation;
///     }
///
///     public static AllowableQualifier of(MeshUI qualifierUi, String name, String abbreviation) {
///         Assert.notNull(qualifierUi, "限定词 UI 不能为空");
///         Assert.isTrue(qualifierUi.isQualifier(), "UI 必须是限定词类型");
///         return new AllowableQualifier(qualifierUi, name, abbreviation);
///     }
/// }
/// ```
///
/// ## 架构位置
///
/// **Domain 层 - 值对象**：
///
/// - 属于领域模型的核心组成部分
/// - 被聚合根、实体、Repository 使用
/// - 不依赖其他层（纯 Java 对象）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.domain.model.vo.mesh;
