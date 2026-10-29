package dev.patra.catalog.domain.fulltext.model.vo.body;

import java.util.List;
import lombok.Builder;

/// 解析后的全文正文（存进 `cat_publication_fulltext_body.body` 的 JSON，工程 spec 第 6.2 节）。
///
/// 前端类型与 zod 校验以此为准（PAP-88），改字段要两边同时改。
///
/// @param schemaVersion 结构版本，当前为 `SCHEMA_VERSION`
/// @param blocks `<body>` 里第一个 `<sec>` 之前的直接内容
/// @param sections 正文章节
/// @param back 文末附加（致谢、脚注、附录、数据可用性等）
/// @param references 参考文献
/// @param wordCount 字数（不计参考文献、公式、表格单元格、代码块）
/// @author linqibin
/// @since 0.11.0
@Builder
public record FullTextBody(
    int schemaVersion,
    List<Block> blocks,
    List<Section> sections,
    List<Section> back,
    List<Reference> references,
    int wordCount) {

  /// 当前结构版本。
  public static final int SCHEMA_VERSION = 1;

  /// 防御性拷贝，`null` 视为空。
  public FullTextBody {
    blocks = blocks != null ? List.copyOf(blocks) : List.of();
    sections = sections != null ? List.copyOf(sections) : List.of();
    back = back != null ? List.copyOf(back) : List.of();
    references = references != null ? List.copyOf(references) : List.of();
  }
}
