/// 文献相关值对象包。
///
/// 包含文献聚合相关的值对象，封装文献标识符、语言信息等不可变概念。
///
/// ## 职责
///
/// - **文献标识**：封装 DOI、PMID、PMC ID 等文献唯一标识符
///   - **语言信息**：封装文献的语言代码和显示名称
///   - **格式验证**：确保标识符格式符合国际标准
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.domain.model.vo.publication.PublicationIdentifiers} - 文献标识符值对象
///
/// - DOI（Digital Object Identifier）：10.1234/example
///       - PMID（PubMed ID）：整数
///       - PMC ID（PubMed Central ID）：PMC + 整数
///   - {@link com.patra.catalog.domain.model.vo.publication.LanguageInfo} - 语言信息值对象
///
/// - 语言代码：ISO 639-1（如 "en"、"zh"）
///       - 显示名称：英文名称（如 "English"、"Chinese"）
///
/// ## 设计原则
///
/// - **格式标准化**：严格遵循 DOI、PMID、ISO 639-1 等国际标准
///   - **不可变性**：标识符和语言信息创建后不可修改
///   - **自我验证**：在构造器中验证格式
///
/// ## 使用示例
///
/// ```java
/// // 创建文献标识符值对象
/// PublicationIdentifiers identifiers = PublicationIdentifiers.builder()
///     .doi("10.1234/example")
///     .pmid(12345678L)
///     .pmcId("PMC1234567")
///     .build();
///
/// // 创建语言信息值对象
/// LanguageInfo languageInfo = LanguageInfo.of("en", "English");
/// ```
///
/// ## 架构位置
///
/// **Domain 层 - 值对象**：
///
/// - 被 {@link com.patra.catalog.domain.model.aggregate.PublicationAggregate} 使用
/// - 不依赖其他层（纯 Java 对象）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.domain.model.vo.publication;
