/// 机构相关值对象包。
///
/// 包含机构聚合相关的值对象，封装机构标识符等不可变概念。
///
/// ## 职责
///
/// - **机构标识**：封装 ROR ID、GRID ID 等机构唯一标识符
///   - **格式验证**：确保标识符格式符合国际标准
///   - **类型安全**：通过强类型避免字符串魔法值
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.domain.model.vo.affiliation.RorId} - ROR（Research Organization Registry）ID 值对象
///
/// - 格式：https://ror.org/0abcdef12（9位字符）
///       - 用于唯一标识研究机构
///   - {@link com.patra.catalog.domain.model.vo.affiliation.GridId} - GRID（Global Research Identifier Database）ID 值对象
///
/// - 格式：grid.12345.67
///       - GRID 已被 ROR 替代，但仍在历史数据中使用
///
/// ## 设计原则
///
/// - **格式标准化**：严格遵循 ROR、GRID 官方格式规范
///   - **不可变性**：标识符创建后不可修改
///   - **自我验证**：在构造器中验证格式
///
/// ## 使用示例
///
/// ```java
/// // 创建 ROR ID 值对象
/// RorId rorId = RorId.of("https://ror.org/0abcdef12");
///
/// // 创建 GRID ID 值对象
/// GridId gridId = GridId.of("grid.12345.67");
/// ```
///
/// ## 架构位置
///
/// **Domain 层 - 值对象**：
///
/// - 被 {@link com.patra.catalog.domain.model.aggregate.AffiliationAggregate} 使用
/// - 不依赖其他层（纯 Java 对象）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.domain.model.vo.affiliation;
