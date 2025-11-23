/// 期刊相关值对象包。
///
/// 包含期刊聚合相关的值对象，封装 ISSN 等期刊标识信息。
///
/// ## 职责
///
/// - **期刊标识**：封装 ISSN（International Standard Serial Number）标识符
///   - **格式验证**：确保 ISSN 格式符合国际标准
///   - **类型区分**：区分印刷版 ISSN（pISSN）和电子版 ISSN（eISSN）
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.domain.model.vo.venue.IssnInfo} - ISSN 信息值对象
///
/// - 格式：1234-5678（8位数字，用连字符分隔）
///       - 类型：pISSN（印刷版）、eISSN（电子版）、lISSN（链接版）
///       - 校验码：最后一位是校验码（使用模 11 算法）
///
/// ## 设计原则
///
/// - **格式标准化**：严格遵循 ISO 3297 ISSN 标准
///   - **不可变性**：ISSN 创建后不可修改
///   - **自我验证**：在构造器中验证格式和校验码
///
/// ## 使用示例
///
/// ```java
/// // 创建 ISSN 信息值对象
/// IssnInfo issnInfo = IssnInfo.of("1234-5678", IssnType.PRINT);
/// ```
///
/// ## 架构位置
///
/// **Domain 层 - 值对象**：
///
/// - 被 {@link com.patra.catalog.domain.model.aggregate.VenueAggregate} 使用
/// - 不依赖其他层（纯 Java 对象）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.domain.model.vo.venue;
