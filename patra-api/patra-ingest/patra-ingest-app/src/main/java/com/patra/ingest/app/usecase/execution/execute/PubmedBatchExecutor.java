package com.patra.ingest.app.usecase.execution.execute;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.Batch;
import com.patra.ingest.domain.model.vo.BatchResult;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.model.vo.StandardLiterature;
import com.patra.ingest.domain.port.LiteraturePublisherPort;
import com.patra.starter.provenance.common.config.BatchingConfig;
import com.patra.starter.provenance.common.config.HttpConfig;
import com.patra.starter.provenance.common.config.PaginationConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.config.RateLimitConfig;
import com.patra.starter.provenance.common.config.RetryConfig;
import com.patra.starter.provenance.common.config.WindowOffsetConfig;
import com.patra.starter.provenance.pubmed.PubMedClient;
import com.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import com.patra.starter.provenance.pubmed.model.request.EPostRequest;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import com.patra.starter.provenance.pubmed.model.response.EPostResponse;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import com.patra.starter.provenance.pubmed.model.response.PubmedArticle;
import com.patra.starter.provenance.pubmed.request.PubMedESearchRequestAssembler;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Batch executor for PubMed provenance.
 *
 * <p>Pipeline per batch:
 *
 * <ol>
 *   <li>ESearch to obtain PMIDs for the current slice.
 *   <li>EFetch to retrieve detailed article payloads.
 *   <li>Convert articles to {@link StandardLiterature}.
 *   <li>Publish the standardized payload via {@link LiteraturePublisherPort}.
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PubmedBatchExecutor implements BatchExecutor {

  private static final String PROVENANCE_DB = "pubmed";
  private static final PubMedESearchRequestAssembler ASSEMBLER =
      new PubMedESearchRequestAssembler();

  /**
   * Threshold for switching to EPost strategy.
   *
   * <p>NCBI recommends using EPost when fetching >200 records to avoid URL length limitations.
   */
  private static final int EPOST_THRESHOLD = 200;

  private final PubMedClient pubMedClient;
  private final LiteraturePublisherPort literaturePublisherPort;
  private final PubmedArticleConverter articleConverter;

  @Override
  public ProvenanceCode getProvenanceCode() {
    return ProvenanceCode.PUBMED;
  }

  @Override
  public BatchResult execute(ExecutionContext context, Batch batch) {
    Objects.requireNonNull(context, "execution context must not be null");
    Objects.requireNonNull(batch, "batch must not be null");

    int batchNo = batch.batchNo();
    long runId = context.runId();
    String provenance = context.provenanceCode();
    String queryHash = safeHash(batch.query());

    log.info(
        "pubmed batch start runId={} batchNo={} provenance={} queryHash={}",
        runId,
        batchNo,
        provenance,
        queryHash);

    try {
      ProvenanceConfig config = toProvenanceConfig(context.configSnapshot());

      // Step 1: retrieve PMIDs via ESearch
      log.debug(
          "executing PubMed ESearch for batch runId={} batchNo={} queryHash={}",
          runId,
          batchNo,
          queryHash);
      List<String> pmids = executeSearch(batch, config);
      if (CollectionUtils.isEmpty(pmids)) {
        log.info(
            "pubmed batch empty result runId={} batchNo={} queryHash={}",
            runId,
            batchNo,
            queryHash);
        LiteraturePublisherPort.PublishResult publishResult =
            publish(Collections.emptyList(), context, batchNo);
        return BatchResult.success(
            batchNo, publishResult.publishedCount(), null, publishResult.storageKey());
      }

      // Step 2: fetch detailed articles via EFetch
      log.debug(
          "fetching {} articles from PubMed for batch runId={} batchNo={}",
          pmids.size(),
          runId,
          batchNo);
      List<StandardLiterature> literature =
          fetchArticles(pmids, config).stream()
              .map(articleConverter::toStandardLiterature)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      // Step 3: publish payload and record storage key
      LiteraturePublisherPort.PublishResult publishResult = publish(literature, context, batchNo);

      log.info(
          "pubmed batch done runId={} batchNo={} fetchedCount={} storageKey={}",
          runId,
          batchNo,
          publishResult.publishedCount(),
          publishResult.storageKey());

      return BatchResult.success(
          batchNo, publishResult.publishedCount(), null, publishResult.storageKey());

    } catch (Exception ex) {
      log.error(
          "pubmed batch failed runId={} batchNo={} provenance={} queryHash={}",
          runId,
          batchNo,
          provenance,
          queryHash,
          ex);
      return BatchResult.failure(batchNo, ex.getMessage());
    }
  }

  private List<String> executeSearch(Batch batch, ProvenanceConfig config) {
    JsonNode params = batch.params();
    JsonNode mergedParams = mergeQueryIntoParams(batch.query(), params);
    log.debug("building ESearch request with params batchNo={}", batch.batchNo());
    ESearchRequest request = ASSEMBLER.buildList(mergedParams);
    ESearchResponse response =
        config != null ? pubMedClient.esearch(request, config) : pubMedClient.esearch(request);
    if (response == null || response.result() == null) {
      log.debug("ESearch returned empty result for batch batchNo={}", batch.batchNo());
      return List.of();
    }
    List<String> pmids = response.result().idList();
    log.debug("ESearch returned {} PMIDs for batch batchNo={}", pmids.size(), batch.batchNo());
    return pmids;
  }

  private JsonNode mergeQueryIntoParams(String query, JsonNode params) {
    if (StringUtils.hasText(query)) {
      return params == null
          ? JsonHelpersFactory.objectNodeWithTerm(query)
          : JsonHelpersFactory.copyWithTerm(params, query);
    }
    return params;
  }

  /**
   * Fetch articles from PubMed using intelligent routing strategy.
   *
   * <p>Routes based on ID count:
   *
   * <ul>
   *   <li>≤200 IDs: Direct EFetch (simple, single API call)
   *   <li>>200 IDs: EPost + WebEnv (avoids URL length limits, NCBI best practice)
   * </ul>
   *
   * @param pmids list of PubMed identifiers
   * @param config provenance configuration
   * @return list of fetched articles
   */
  private List<PubmedArticle> fetchArticles(List<String> pmids, ProvenanceConfig config) {
    if (pmids.size() <= EPOST_THRESHOLD) {
      log.debug("Using direct EFetch for {} PMIDs", pmids.size());
      return fetchArticlesDirectly(pmids, config);
    } else {
      log.info("Using EPost+WebEnv for {} PMIDs (threshold: {})", pmids.size(), EPOST_THRESHOLD);
      return fetchArticlesViaEPost(pmids, config);
    }
  }

  /**
   * Fetch articles directly via EFetch (for small batches ≤200 PMIDs).
   *
   * <p>This is the original approach: concatenate IDs into a comma-separated string and pass
   * directly to EFetch. Gateway will automatically use POST if URL exceeds length limit.
   *
   * @param pmids list of PubMed identifiers
   * @param config provenance configuration
   * @return list of fetched articles
   */
  private List<PubmedArticle> fetchArticlesDirectly(List<String> pmids, ProvenanceConfig config) {
    String idParam = String.join(",", pmids);
    log.debug("executing direct EFetch for {} PMIDs", pmids.size());
    EFetchRequest request = new EFetchRequest(PROVENANCE_DB, idParam);
    EFetchResponse response =
        config != null ? pubMedClient.efetch(request, config) : pubMedClient.efetch(request);
    if (response == null || response.articles() == null) {
      log.debug("EFetch returned empty result for {} PMIDs", pmids.size());
      return List.of();
    }
    log.debug("EFetch returned {} articles", response.articles().size());
    return response.articles();
  }

  /**
   * Fetch articles via EPost + WebEnv (for large batches >200 PMIDs).
   *
   * <p>NCBI recommended approach for large ID lists:
   *
   * <ol>
   *   <li>Upload IDs to History Server via EPost → get WebEnv token
   *   <li>Fetch articles via EFetch using WebEnv (no ID list in URL)
   * </ol>
   *
   * @param pmids list of PubMed identifiers
   * @param config provenance configuration
   * @return list of fetched articles
   */
  private List<PubmedArticle> fetchArticlesViaEPost(List<String> pmids, ProvenanceConfig config) {
    // Step 1: Upload ID list to History Server
    log.debug("executing EPost to upload {} PMIDs to History Server", pmids.size());
    String idParam = String.join(",", pmids);
    EPostRequest postReq = new EPostRequest(PROVENANCE_DB, idParam, null, null, null);
    EPostResponse postResp =
        config != null ? pubMedClient.epost(postReq, config) : pubMedClient.epost(postReq);

    if (!postResp.isValid()) {
      log.error(
          "EPost returned invalid WebEnv/QueryKey: webEnv={}, queryKey={}",
          postResp.webEnv(),
          postResp.queryKey());
      throw new RuntimeException(
          "EPost failed to return valid WebEnv for " + pmids.size() + " PMIDs");
    }

    log.debug(
        "EPost success: WebEnv={}, QueryKey={}, count={}",
        postResp.getTruncatedWebEnv(),
        postResp.queryKey(),
        postResp.count());

    // Step 2: Fetch articles using WebEnv (ID list is empty!)
    log.debug("executing EFetch with WebEnv for {} PMIDs", pmids.size());
    EFetchRequest fetchReq =
        new EFetchRequest(
            PROVENANCE_DB,
            "", // ID留空，使用WebEnv
            "xml", // retmode
            "abstract", // rettype
            0, // retstart
            pmids.size(), // retmax（一次性取全部）
            postResp.webEnv(), // WebEnv令牌
            postResp.queryKey(), // QueryKey
            null,
            null,
            null // apiKey, tool, email
            );

    EFetchResponse response =
        config != null ? pubMedClient.efetch(fetchReq, config) : pubMedClient.efetch(fetchReq);
    // TODO for the time being, we'll be accessing the stream-limiting starter.
    try {
      TimeUnit.MILLISECONDS.sleep(600L);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    if (response == null || response.articles() == null) {
      return List.of();
    }
    return response.articles();
  }

  private LiteraturePublisherPort.PublishResult publish(
      List<StandardLiterature> literature, ExecutionContext context, int batchNo) {
    LiteraturePublisherPort.PublishContext publishContext =
        LiteraturePublisherPort.PublishContext.builder()
            .runId(context.runId())
            .batchNo(batchNo)
            .provenanceCode(context.provenanceCode())
            .build();
    return literaturePublisherPort.publish(literature, publishContext);
  }

  private String safeHash(String query) {
    if (!StringUtils.hasText(query)) {
      return "null";
    }
    return Integer.toHexString(query.hashCode());
  }

  private ProvenanceConfig toProvenanceConfig(ProvenanceConfigSnapshot snapshot) {
    if (snapshot == null || snapshot.provenance() == null) {
      return null;
    }
    String baseUrl = snapshot.provenance().baseUrlDefault();
    if (!StringUtils.hasText(baseUrl)) {
      return null;
    }

    try {
      return new ProvenanceConfig(
          baseUrl.trim(),
          toHttpConfig(snapshot.http()),
          toPaginationConfig(snapshot.pagination()),
          toWindowOffsetConfig(snapshot.windowOffset()),
          toBatchingConfig(snapshot.batching()),
          toRetryConfig(snapshot.retry()),
          toRateLimitConfig(snapshot.rateLimit()));
    } catch (IllegalArgumentException ex) {
      log.warn(
          "failed to build provenance override provenanceId={} message={}",
          snapshot.provenance().id(),
          ex.getMessage());
      return null;
    }
  }

  private HttpConfig toHttpConfig(ProvenanceConfigSnapshot.HttpConfig source) {
    if (source == null) {
      return null;
    }
    return new HttpConfig(
        JsonHelpersFactory.parseHeaders(source.defaultHeadersJson()),
        source.timeoutConnectMillis(),
        source.timeoutReadMillis(),
        source.timeoutTotalMillis());
  }

  private PaginationConfig toPaginationConfig(ProvenanceConfigSnapshot.PaginationConfig source) {
    if (source == null) {
      return null;
    }
    if (source.pageSizeValue() == null && source.maxPagesPerExecution() == null) {
      return null;
    }
    return new PaginationConfig(source.pageSizeValue(), source.maxPagesPerExecution());
  }

  private WindowOffsetConfig toWindowOffsetConfig(
      ProvenanceConfigSnapshot.WindowOffsetConfig source) {
    if (source == null) {
      return null;
    }
    return new WindowOffsetConfig(
        source.windowModeCode(),
        source.windowSizeValue(),
        source.windowSizeUnitCode(),
        source.lookbackValue(),
        source.lookbackUnitCode(),
        source.overlapValue(),
        source.overlapUnitCode(),
        source.offsetTypeCode(),
        source.maxIdsPerWindow());
  }

  private BatchingConfig toBatchingConfig(ProvenanceConfigSnapshot.BatchingConfig source) {
    if (source == null) {
      return null;
    }
    if (source.detailFetchBatchSize() == null && source.maxIdsPerRequest() == null) {
      return null;
    }
    return new BatchingConfig(source.detailFetchBatchSize(), source.maxIdsPerRequest());
  }

  private RetryConfig toRetryConfig(ProvenanceConfigSnapshot.RetryConfig source) {
    if (source == null) {
      return null;
    }
    if (source.maxRetryTimes() == null && source.initialDelayMillis() == null) {
      return null;
    }
    return new RetryConfig(source.maxRetryTimes(), source.initialDelayMillis());
  }

  private RateLimitConfig toRateLimitConfig(ProvenanceConfigSnapshot.RateLimitConfig source) {
    if (source == null) {
      return null;
    }
    if (source.maxConcurrentRequests() == null && source.perCredentialQpsLimit() == null) {
      return null;
    }
    return new RateLimitConfig(source.maxConcurrentRequests(), source.perCredentialQpsLimit());
  }

  /**
   * Utility helpers for building JSON nodes without leaking ObjectMapper from here.
   *
   * <p>Intentionally scoped within the executor to keep dependencies minimal.
   */
  private static final class JsonHelpersFactory {

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    private JsonHelpersFactory() {}

    private static JsonNode objectNodeWithTerm(String term) {
      com.fasterxml.jackson.databind.node.ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("term", term);
      return node;
    }

    private static JsonNode copyWithTerm(JsonNode params, String term) {
      if (params != null && params.isObject()) {
        com.fasterxml.jackson.databind.node.ObjectNode node =
            ((com.fasterxml.jackson.databind.node.ObjectNode) params).deepCopy();
        node.put("term", term);
        return node;
      }
      return objectNodeWithTerm(term);
    }

    private static java.util.Map<String, String> parseHeaders(String headersJson) {
      if (!StringUtils.hasText(headersJson)) {
        return java.util.Map.of();
      }
      try {
        return OBJECT_MAPPER.readValue(
            headersJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
      } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
        return java.util.Map.of();
      }
    }
  }
}
