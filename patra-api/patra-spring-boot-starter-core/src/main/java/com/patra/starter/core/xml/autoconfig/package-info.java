/**
 * XML 序列化自动配置包。
 *
 * <p>本包提供基于 Jackson 的 XML 序列化器可选自动配置,用于处理 XML 格式的数据交换场景。
 * 仅在 {@code jackson-dataformat-xml} 依赖存在时自动激活。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>条件性配置 {@link com.fasterxml.jackson.dataformat.xml.XmlMapper}
 *   <li>共享 Jackson 全局配置(与 JSON 序列化保持一致)
 *   <li>支持 XML 与 Java 对象的双向转换
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.core.xml.autoconfig.XmlAutoConfiguration} - XmlMapper 条件自动配置类
 * </ul>
 *
 * <h2>激活条件</h2>
 *
 * <p>需要在 classpath 中包含 {@code jackson-dataformat-xml} 依赖:
 *
 * <pre>{@code
 * <dependency>
 *     <groupId>com.fasterxml.jackson.dataformat</groupId>
 *     <artifactId>jackson-dataformat-xml</artifactId>
 * </dependency>
 * }</pre>
 *
 * <h2>应用场景</h2>
 *
 * <ul>
 *   <li><strong>数据采集</strong> - 处理第三方 API 返回的 XML 响应(如 PubMed XML)
 *   <li><strong>遗留系统集成</strong> - 与仅支持 XML 的外部系统交互
 *   <li><strong>配置解析</strong> - 读取 XML 配置文件
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Service
 * public class PubMedXmlParser {
 *     private final XmlMapper xmlMapper;
 *
 *     public PubMedArticle parseXml(String xmlContent) throws JsonProcessingException {
 *         // 使用统一配置的 XmlMapper
 *         return xmlMapper.readValue(xmlContent, PubMedArticle.class);
 *     }
 *
 *     public String toXml(PubMedArticle article) throws JsonProcessingException {
 *         return xmlMapper.writeValueAsString(article);
 *     }
 * }
 * }</pre>
 *
 * <h2>配置共享</h2>
 *
 * <p>XmlMapper 继承 ObjectMapper 的全局配置(日期格式、命名策略等),确保 JSON 和 XML 序列化行为一致。
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.core.xml.autoconfig;
