/// 作者相关值对象包。
///
/// 包含作者聚合相关的值对象，封装作者标识和名称等不可变概念。
///
/// ## 职责
///
/// - **作者标识**：封装 ORCID 等作者唯一标识符
///   - **名称表示**：封装作者姓名的结构化表示
///   - **格式验证**：确保标识符格式符合国际标准
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.domain.model.vo.author.Orcid} - ORCID 标识符值对象
///
/// - 格式：0000-0001-2345-6789（4组4位数字，用连字符分隔）
///       - 校验码：最后一位是校验码（使用 ISO/IEC 7064 算法）
///   - {@link com.patra.catalog.domain.model.vo.author.AuthorName} - 作者姓名值对象
///
/// - 结构化姓名（姓、名、中间名、后缀等）
///       - 支持国际化命名规则
///
/// ## 设计原则
///
/// - **格式标准化**：严格遵循 ORCID 官方格式规范
///   - **不可变性**：姓名和标识符创建后不可修改
///   - **自我验证**：在构造器中验证格式和业务规则
///
/// ## 使用示例
///
/// ```java
/// // 创建 ORCID 值对象
/// Orcid orcid = Orcid.of("0000-0001-2345-6789");
///
/// // 创建作者姓名值对象
/// AuthorName name = AuthorName.of("Smith", "John", "A.");
/// ```
///
/// ## 架构位置
///
/// **Domain 层 - 值对象**：
///
/// - 被 {@link com.patra.catalog.domain.model.aggregate.AuthorAggregate} 使用
/// - 不依赖其他层（纯 Java 对象）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.domain.model.vo.author;
