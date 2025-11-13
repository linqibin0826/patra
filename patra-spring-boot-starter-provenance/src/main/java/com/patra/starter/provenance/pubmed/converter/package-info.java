/**
 * PubMed 转换器包。
 *
 * <p>负责将 PubMed 响应对象转换为 CanonicalLiterature 标准模型。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>解析 PubMed XML/JSON 响应
 *   <li>提取文献元数据（标题、作者、期刊、摘要等）
 *   <li>转换为 {@code CanonicalLiterature} 标准模型
 *   <li>处理不完整或缺失的字段
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link PubmedLiteratureConverter} - PubMed 文章转换器
 * </ul>
 *
 * <h2>转换逻辑</h2>
 *
 * <pre>
 * PubmedLiterature (PubMed 模型)
 * ├── PMID → CanonicalLiterature.pmid
 * ├── Article
 * │   ├── ArticleTitle → title
 * │   ├── Abstract → abstract
 * │   ├── AuthorList → authors
 * │   └── Journal
 * │       ├── Title → journal
 * │       └── JournalIssue
 * │           ├── Volume → volume
 * │           └── PubDate → publicationDate
 * └── PubmedData
 *     └── ArticleIdList
 *         ├── DOI → doi
 *         └── PMC → pmcid
 * </pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * PubmedLiteratureConverter converter = new PubmedLiteratureConverter();
 * PubmedLiterature article = ...; // 从 EFetch 响应获取
 * CanonicalLiterature literature = converter.convert(article);
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.pubmed.converter;
