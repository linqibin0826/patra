package com.patra.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * 文献数据传输对象。
 *
 * <p>用于跨服务通信的文献数据传输对象。此DTO代表patra-ingest和patra-catalog服务之间的契约, 在DDD术语中称为"发布语言",由patra-catalog服务拥有。
 *
 * <p>设计要点:
 *
 * <ul>
 *   <li>使用Lombok @Builder实现不可变性
 *   <li>版本化契约(未来:添加@Schema版本注解)
 *   <li>数据完整性的验证约束
 *   <li>向后兼容性:新字段添加时带有默认值
 * </ul>
 *
 * @param title 文献文章的主标题
 * @param abstractText 总结文献内容的摘要文本
 * @param authors 对此文献做出贡献的作者列表
 * @param journal 发表此文献的期刊元数据
 * @param identifiers 外部标识符映射(pmid、doi、pmc等)
 * @param publicationDate 文献发表日期
 * @param keywords 与此文献关联的关键词或MeSH术语
 * @param language 文献内容的语言代码(ISO 639-1)
 * @param publicationTypes 出版物类型分类(例如:期刊文章、综述)
 * @author linqibin
 * @since 0.1.0
 */
@Builder
public record LiteratureDTO(
    @NotBlank String title,
    String abstractText,
    @Valid List<AuthorDTO> authors,
    @Valid JournalDTO journal,
    Map<String, String> identifiers,
    LocalDate publicationDate,
    List<String> keywords,
    String language,
    List<String> publicationTypes) {}
