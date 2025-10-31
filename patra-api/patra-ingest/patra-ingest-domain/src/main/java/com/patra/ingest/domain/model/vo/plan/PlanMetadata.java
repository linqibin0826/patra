package com.patra.ingest.domain.model.vo.plan;

/**
 * Planning metadata returned from the data source.
 *
 * <p>Encapsulates the information required to break a collection job into batches while allowing
 * the execute stage to reuse upstream caches (e.g., PubMed WebEnv).
 *
 * @param totalCount total number of matching records
 * @param webEnv PubMed History Server token (nullable)
 * @param queryKey PubMed query key paired with the WebEnv token (nullable)
 */
public record PlanMetadata(int totalCount, String webEnv, String queryKey) {

  public PlanMetadata {
    if (totalCount < 0) {
      throw new IllegalArgumentException("totalCount must be >= 0");
    }

    boolean hasWebEnv = webEnv != null && !webEnv.isBlank();
    boolean hasQueryKey = queryKey != null && !queryKey.isBlank();
    if (hasWebEnv != hasQueryKey) {
      throw new IllegalArgumentException(
          "webEnv and queryKey must either both be present or both absent");
    }
  }

  /**
   * Create empty metadata representing no available results.
   *
   * @return empty metadata
   */
  public static PlanMetadata empty() {
    return new PlanMetadata(0, null, null);
  }

  /**
   * Whether the metadata contains a WebEnv handle that can be reused during execution.
   *
   * @return true if both WebEnv and QueryKey are present
   */
  public boolean hasWebEnv() {
    return webEnv != null && !webEnv.isBlank();
  }
}
