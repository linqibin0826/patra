package com.patra.starter.expr.compiler.function;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PubMed-specific function that returns the appropriate {@code datetype} value for date filtering.
 * Currently returns "pdat" (publication date) as the default.
 *
 * <p>Future Enhancement: This function may be extended to select between "pdat" (publication date)
 * and "edat" (entry date) based on operation type, endpoint, or rule context from the snapshot.
 *
 * <p>See: docs/expr/03-compiler-bridge-internals.md §3.3.2, docs/expr/04-provider-pubmed.md §4.3.2
 *
 * @since 1.0.0
 */
public class PubmedDatetypeFunction implements RenderFunction {

  private static final Logger log = LoggerFactory.getLogger(PubmedDatetypeFunction.class);
  private static final String CODE = "PUBMED_DATETYPE";
  private static final String DEFAULT_DATETYPE = "pdat";

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public String apply(Map<String, String> placeholders, ProvenanceSnapshot snapshot) {
    // Read fieldKey from placeholders to determine the correct datetype
    String fieldKey = placeholders.get("{{field}}");
    String datetype;

    // Map field semantics to PubMed datetype parameter
    if ("entrez_date".equals(fieldKey)) {
      datetype = "edat"; // Entry Date - when the record was added to PubMed
    } else if ("publication_date".equals(fieldKey)) {
      datetype = "pdat"; // Publication Date - when the article was published
    } else {
      // Default to pdat for backward compatibility with unknown fields
      datetype = DEFAULT_DATETYPE;
      log.warn(
          "Unknown date field '{}' for PUBMED_DATETYPE, defaulting to '{}'",
          fieldKey,
          DEFAULT_DATETYPE);
    }

    // Mutate placeholder map so templates like {{datetype}} resolve correctly
    placeholders.put("{{datetype}}", datetype);
    log.debug("PUBMED_DATETYPE for fieldKey='{}' returning: {}", fieldKey, datetype);
    return datetype;
  }
}
