package dev.linqibin.patra.starter.expr.compiler.function;

import dev.linqibin.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// PubMed 特定函数，返回用于日期过滤的适当 `datetype` 值。 目前默认返回 "pdat"（发布日期）。
///
/// 未来增强：此函数可能会扩展以根据操作类型、端点或快照中的规则上下文在 "pdat"（发布日期） 和 "edat"（条目日期）之间进行选择。
///
/// 参见: docs/expr/03-compiler-bridge-internals.md §3.3.2, docs/expr/04-provider-pubmed.md §4.3.2
///
/// @since 0.1.0
public class PubmedDatetypeFunction implements RenderFunction {

  private static final Logger log = LoggerFactory.getLogger(PubmedDatetypeFunction.class);
  private static final String CODE = "PUBMED_DATETYPE";
  private static final String DEFAULT_DATETYPE = "pdat";

  /// {@inheritDoc}
  @Override
  public String code() {
    return CODE;
  }

  /// {@inheritDoc}
  @Override
  public String apply(Map<String, String> placeholders, ProvenanceSnapshot snapshot) {
    // 从占位符中读取 fieldKey 以确定正确的 datetype
    String fieldKey = placeholders.get("{{field}}");
    String datetype;

    // 将字段语义映射到 PubMed datetype 参数
    if ("entrez_date".equals(fieldKey)) {
      datetype = "edat"; // 条目日期 - 记录添加到 PubMed 的时间
    } else if ("publication_date".equals(fieldKey)) {
      datetype = "pdat"; // 发布日期 - 文章发布的时间
    } else {
      // 默认为 pdat 以保持与未知字段的向后兼容性
      datetype = DEFAULT_DATETYPE;
      log.warn("PUBMED_DATETYPE 的日期字段 '{}' 未知，默认为 '{}'", fieldKey, DEFAULT_DATETYPE);
    }

    // 变异占位符映射，以便模板如 {{datetype}} 正确解析
    placeholders.put("{{datetype}}", datetype);
    log.debug("PUBMED_DATETYPE for fieldKey='{}' 返回: {}", fieldKey, datetype);
    return datetype;
  }
}
