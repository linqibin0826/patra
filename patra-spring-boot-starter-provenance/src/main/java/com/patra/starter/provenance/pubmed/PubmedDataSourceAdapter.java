package com.patra.starter.provenance.pubmed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.common.model.StandardLiterature;
import com.patra.starter.provenance.boot.ProvenanceProperties;
import com.patra.starter.provenance.common.adapter.AdapterRequest;
import com.patra.starter.provenance.common.adapter.AdapterResult;
import com.patra.starter.provenance.common.adapter.BatchExecutionParams;
import com.patra.starter.provenance.common.adapter.DataSourceAdapter;
import com.patra.starter.provenance.common.config.BatchingConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.pubmed.converter.PubmedArticleConverter;
import com.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import com.patra.starter.provenance.pubmed.model.request.EPostRequest;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import com.patra.starter.provenance.pubmed.model.response.EPostResponse;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import com.patra.starter.provenance.pubmed.model.response.PubmedArticle;
import com.patra.starter.provenance.pubmed.request.PubMedESearchRequestAssembler;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * PubMed implementation of {@link DataSourceAdapter}.
 *
 * <p>Encapsulates search, fetch, and conversion logic while respecting configuration precedence:
 * runtime snapshot &gt; source overrides &gt; shared defaults.
 */
@Slf4j
@RequiredArgsConstructor
public class PubmedDataSourceAdapter implements DataSourceAdapter {

  private static final String PROVENANCE_CODE = "pubmed";
  private static final int DEFAULT_EPOST_THRESHOLD = 200;
  private static final int WARNING_ID_SAMPLE_LIMIT = 5;

  private static final PubMedESearchRequestAssembler ESEARCH_ASSEMBLER =
      new PubMedESearchRequestAssembler();

  private final PubMedClient pubMedClient;
  private final PubmedArticleConverter converter;
  private final ProvenanceProperties properties;
  @Nullable private final ProvenanceMetrics metrics;

  @Override
  public String getProvenanceCode() {
    return PROVENANCE_CODE;
  }

  @Override
  public AdapterResult fetchData(AdapterRequest request) {
    long start = System.currentTimeMillis();
    int batchNo = request.metadata().batchNo();
    String operation = request.operationCode();

    log.info("PubMed adapter start: operation={} batchNo={}", operation, batchNo);

    try {
      ProvenanceConfig config = properties.mergeWithRuntime(PROVENANCE_CODE, request.config());
      JsonNode searchParams = buildSearchParams(request);
      ESearchRequest searchRequest = ESEARCH_ASSEMBLER.buildList(searchParams);

      ESearchResponse searchResponse = pubMedClient.esearch(searchRequest, config);
      List<String> pmids = extractPmids(searchResponse);

      if (pmids.isEmpty()) {
        log.info(
            "PubMed adapter empty result: operation={} batchNo={} duration={}ms",
            operation,
            batchNo,
            System.currentTimeMillis() - start);
        return AdapterResult.success(List.of(), null);
      }

      List<PubmedArticle> articles = fetchArticles(pmids, config);
      FetchOutcome outcome = convertArticles(articles);
      String nextCursor = extractCursorToken(searchResponse);

      AdapterResult result =
          outcome.failedPmids().isEmpty()
              ? AdapterResult.success(outcome.literatures(), nextCursor)
              : AdapterResult.partialSuccess(
                  outcome.literatures(),
                  nextCursor,
                  buildConversionWarning(outcome.failedPmids()),
                  outcome.attempted());

      log.info(
          "PubMed adapter success: operation={} batchNo={} fetched={} attempted={} duration={}ms",
          operation,
          batchNo,
          result.fetchedCount(),
          outcome.attempted(),
          System.currentTimeMillis() - start);
      return result;
    } catch (ProvenanceClientException ex) {
      AdapterResult failure = classifyClientException(ex);
      log.warn(
          "PubMed adapter client error: operation={} batchNo={} status={} message={}",
          operation,
          batchNo,
          ex.getStatusCode(),
          ex.getMessage(),
          ex);
      return failure;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.error("PubMed adapter interrupted: operation={} batchNo={}", operation, batchNo, ex);
      return AdapterResult.retriableFailure("PubMed adapter interrupted");
    } catch (Exception ex) {
      if (isTimeout(ex)) {
        log.warn(
            "PubMed adapter timeout detected: operation={} batchNo={} message={}",
            operation,
            batchNo,
            ex.getMessage());
        return AdapterResult.retriableFailure("PubMed request timeout: " + ex.getMessage());
      }
      log.error("PubMed adapter unexpected error: operation={} batchNo={}", operation, batchNo, ex);
      return AdapterResult.nonRetriableFailure("Unexpected PubMed error: " + ex.getMessage());
    }
  }

  private JsonNode buildSearchParams(AdapterRequest request) {
    // executionParams.params() contains complete batch execution parameters (incl. pagination)
    BatchExecutionParams exec = request.executionParams();
    JsonNode params = exec.params();
    String query = exec.query();

    if (!StringUtils.hasText(query)) {
      return params;
    }

    ObjectNode node;
    if (params != null && params.isObject()) {
      node = ((ObjectNode) params).deepCopy();
    } else {
      node = JsonMapperHolder.getObjectMapper().createObjectNode();
    }
    node.put("term", query);
    return node;
  }

  private List<String> extractPmids(ESearchResponse response) {
    if (response == null || response.result() == null) {
      return List.of();
    }
    List<String> idList = response.result().idList();
    return CollectionUtils.isEmpty(idList) ? List.of() : idList;
  }

  private String extractCursorToken(ESearchResponse response) {
    if (response == null || response.result() == null) {
      return null;
    }
    return response.result().webEnv();
  }

  private List<PubmedArticle> fetchArticles(List<String> pmids, ProvenanceConfig config)
      throws InterruptedException {
    int threshold = resolveEpostThreshold(config);
    if (pmids.size() <= threshold) {
      return fetchArticlesDirectly(pmids, config);
    }
    return fetchArticlesViaEPost(pmids, config);
  }

  private int resolveEpostThreshold(ProvenanceConfig config) {
    BatchingConfig batching = config.batching();
    if (batching != null) {
      if (batching.epostThreshold() != null) {
        return batching.epostThreshold();
      }
      if (batching.maxIdsPerRequest() != null) {
        return batching.maxIdsPerRequest();
      }
    }
    return DEFAULT_EPOST_THRESHOLD;
  }

  private List<PubmedArticle> fetchArticlesDirectly(List<String> pmids, ProvenanceConfig config) {
    String idParam = String.join(",", pmids);
    log.debug("PubMed direct EFetch start: count={}", pmids.size());
    EFetchRequest request = new EFetchRequest(PROVENANCE_CODE, idParam);
    EFetchResponse response = pubMedClient.efetch(request, config);
    List<PubmedArticle> articles = response != null ? response.articles() : List.of();
    log.debug("PubMed direct EFetch completed: returned={}", articles.size());
    return articles;
  }

  private List<PubmedArticle> fetchArticlesViaEPost(List<String> pmids, ProvenanceConfig config)
      throws InterruptedException {
    log.info("PubMed EPost strategy triggered: count={}", pmids.size());
    String idParam = String.join(",", pmids);
    EPostRequest postRequest = new EPostRequest(PROVENANCE_CODE, idParam, null, null, null);
    EPostResponse postResponse = pubMedClient.epost(postRequest, config);
    if (postResponse == null || !postResponse.isValid()) {
      throw new ProvenanceClientException(
          PROVENANCE_CODE,
          "epost",
          "EPost returned invalid WebEnv or QueryKey for " + pmids.size() + " PMIDs");
    }
    log.debug(
        "PubMed EPost success: webEnv={} queryKey={}",
        postResponse.getTruncatedWebEnv(),
        postResponse.queryKey());

    EFetchRequest fetchRequest =
        new EFetchRequest(
            PROVENANCE_CODE,
            "",
            "xml",
            "abstract",
            0,
            pmids.size(),
            postResponse.webEnv(),
            postResponse.queryKey(),
            null,
            null,
            null);
    EFetchResponse response = pubMedClient.efetch(fetchRequest, config);

    // Gentle delay per NCBI recommendation to avoid bursting after EPost.
    TimeUnit.MILLISECONDS.sleep(600L);

    List<PubmedArticle> articles = response != null ? response.articles() : List.of();
    log.debug("PubMed EPost EFetch completed: returned={}", articles.size());
    return articles;
  }

  private FetchOutcome convertArticles(List<PubmedArticle> articles) {
    if (CollectionUtils.isEmpty(articles)) {
      return new FetchOutcome(List.of(), 0, List.of());
    }
    List<StandardLiterature> literatures = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    int attempted = 0;
    for (PubmedArticle article : articles) {
      if (article == null) {
        continue;
      }
      attempted++;
      try {
        StandardLiterature converted = converter.toStandardLiterature(article);
        if (converted != null) {
          literatures.add(converted);
        }
      } catch (Exception ex) {
        failures.add(article.pmid());
        log.error(
            "Failed to convert PubMed article: pmid={} message={}",
            article.pmid(),
            ex.getMessage(),
            ex);
      }
    }
    int successCount = literatures.size();
    int failureCount = failures.size();
    if (attempted > 0) {
      log.info(
          "PubMed article conversion summary: attempted={} success={} failure={}",
          attempted,
          successCount,
          failureCount);
    }
    recordConversionMetrics(successCount, failureCount);
    return new FetchOutcome(List.copyOf(literatures), attempted, List.copyOf(failures));
  }

  private AdapterResult classifyClientException(ProvenanceClientException ex) {
    Integer status = ex.getStatusCode();
    if (status == null) {
      return AdapterResult.retriableFailure("PubMed client error: " + ex.getMessage());
    }
    if (status == 429 || status == 503 || status == 502 || status >= 500) {
      return AdapterResult.retriableFailure(
          "PubMed service unavailable (status=%d)".formatted(status));
    }
    if (status == 401 || status == 403) {
      return AdapterResult.nonRetriableFailure(
          "PubMed authentication failure (status=%d)".formatted(status));
    }
    if (status >= 400 && status < 500) {
      return AdapterResult.nonRetriableFailure(
          "PubMed request rejected (status=%d)".formatted(status));
    }
    return AdapterResult.retriableFailure(
        "PubMed unexpected response (status=%d)".formatted(status));
  }

  private String buildConversionWarning(List<String> failedPmids) {
    if (failedPmids.isEmpty()) {
      return null;
    }
    List<String> sample =
        failedPmids.subList(0, Math.min(failedPmids.size(), WARNING_ID_SAMPLE_LIMIT));
    if (failedPmids.size() <= WARNING_ID_SAMPLE_LIMIT) {
      return "Conversion failed for pmid(s): " + String.join(",", sample);
    }
    return "Conversion failed for pmid(s): "
        + String.join(",", sample)
        + " +"
        + (failedPmids.size() - sample.size())
        + " more";
  }

  private boolean isTimeout(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private record FetchOutcome(
      List<StandardLiterature> literatures, int attempted, List<String> failedPmids) {}

  private void recordConversionMetrics(int successCount, int failureCount) {
    if (metrics == null) {
      return;
    }
    metrics.recordConversionMetrics(ProvenanceCode.PUBMED, successCount, failureCount);
  }
}
