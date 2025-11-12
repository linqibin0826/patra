package com.patra.starter.provenance.pubmed.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.common.model.CanonicalLiterature;
import com.patra.ingest.domain.model.DataType;
import com.patra.starter.provenance.boot.ProvenanceProperties;
import com.patra.starter.provenance.common.config.BatchingConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.common.processor.DataProcessor;
import com.patra.starter.provenance.common.processor.ProcessResult;
import com.patra.starter.provenance.common.processor.ProcessResult.ProcessStatus;
import com.patra.starter.provenance.common.processor.ProviderContext;
import com.patra.starter.provenance.common.processor.ValidationResult;
import com.patra.starter.provenance.common.provider.BatchExecutionParams;
import com.patra.starter.provenance.common.provider.ProviderRequest;
import com.patra.starter.provenance.pubmed.PubMedClient;
import com.patra.starter.provenance.pubmed.converter.PubmedArticleConverter;
import com.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import com.patra.starter.provenance.pubmed.model.request.EPostRequest;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import com.patra.starter.provenance.pubmed.model.response.EPostResponse;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import com.patra.starter.provenance.pubmed.model.response.PubmedArticle;
import com.patra.starter.provenance.pubmed.request.PubMedESearchRequestAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PubMed 文献数据处理器
 *
 * <p>实现DataProcessor<CanonicalLiterature>接口，负责从PubMed获取和处理文献数据。
 *
 * <p><strong>核心职责</strong>：
 * <ul>
 *   <li>ESearch：执行搜索获取PMID列表</li>
 *   <li>EPost（可选）：当ID数量超过阈值时，上传ID列表获取WebEnv</li>
 *   <li>EFetch：批量获取文章详细元数据</li>
 *   <li>转换：将PubMed XML响应转换为CanonicalLiterature</li>
 *   <li>验证：验证转换后的文献数据</li>
 * </ul>
 *
 * <p><strong>架构位置</strong>：
 * <pre>
 * DataSourceProvider (PubmedDataSourceProvider)
 *     ↓ 委托
 * DataProcessor (PubmedLiteratureProcessor) ← [本类]
 *     ↓ 调用
 * PubMedClient → NCBI E-utilities API
 * </pre>
 *
 * @author Patra Architecture Team
 * @since v2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PubmedLiteratureProcessor implements DataProcessor<CanonicalLiterature> {

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
    public DataType getDataType() {
        return DataType.LITERATURE;
    }

    @Override
    public ProcessResult<CanonicalLiterature> process(
            ProviderRequest request,
            ProviderContext context) {
        long start = System.currentTimeMillis();
        int batchNo = request.metadata().batchNo();
        String operation = request.operationCode();

        log.info("PubMed Literature Processor start: operation={} batchNo={}", operation, batchNo);

        try {
            // 1. 获取配置
            ProvenanceConfig config = properties.mergeWithRuntime(PROVENANCE_CODE, request.config());

            // 2. 构建搜索参数
            JsonNode searchParams = buildSearchParams(request);
            ESearchRequest searchRequest = ESEARCH_ASSEMBLER.buildList(searchParams);

            // 3. 执行搜索
            ESearchResponse searchResponse = pubMedClient.esearch(searchRequest, config);
            List<String> pmids = extractPmids(searchResponse);

            if (pmids.isEmpty()) {
                log.info(
                        "PubMed Literature Processor empty result: operation={} batchNo={} duration={}ms",
                        operation,
                        batchNo,
                        System.currentTimeMillis() - start);
                return ProcessResult.success(List.of(), extractCursorToken(searchResponse));
            }

            // 4. 获取文章数据
            List<PubmedArticle> articles = fetchArticles(pmids, config);

            // 5. 转换为标准格式
            ConversionOutcome outcome = convertArticles(articles);
            String nextCursor = extractCursorToken(searchResponse);

            // 6. 构建结果
            ProcessResult<CanonicalLiterature> result =
                    outcome.failedPmids().isEmpty()
                            ? ProcessResult.success(outcome.literatures(), nextCursor)
                            : ProcessResult.partialSuccess(
                                    outcome.literatures(),
                                    nextCursor,
                                    buildConversionWarning(outcome.failedPmids()));

            log.info(
                    "PubMed Literature Processor success: operation={} batchNo={} fetched={} attempted={} duration={}ms",
                    operation,
                    batchNo,
                    result.data().size(),
                    outcome.attempted(),
                    System.currentTimeMillis() - start);

            return result;

        } catch (ProvenanceClientException ex) {
            log.warn(
                    "PubMed Literature Processor client error: operation={} batchNo={} status={} message={}",
                    operation,
                    batchNo,
                    ex.getStatusCode(),
                    ex.getMessage(),
                    ex);
            return ProcessResult.failure(classifyErrorMessage(ex));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("PubMed Literature Processor interrupted: operation={} batchNo={}", operation, batchNo, ex);
            return ProcessResult.failure("PubMed processor interrupted");
        } catch (Exception ex) {
            log.error("PubMed Literature Processor unexpected error: operation={} batchNo={}", operation, batchNo, ex);
            return ProcessResult.failure("Unexpected PubMed error: " + ex.getMessage());
        }
    }

    @Override
    public ValidationResult validate(CanonicalLiterature data) {
        if (data == null) {
            return ValidationResult.invalid("文献数据不能为null");
        }

        List<String> errors = new ArrayList<>();

        // 验证必填字段
        if (!StringUtils.hasText(data.getPmid())) {
            errors.add("PMID不能为空");
        }
        if (!StringUtils.hasText(data.getTitle())) {
            errors.add("标题不能为空");
        }

        if (!errors.isEmpty()) {
            return ValidationResult.invalid(String.join("; ", errors));
        }

        return ValidationResult.valid();
    }

    @Override
    public CanonicalLiterature transform(Object rawData) {
        if (rawData == null) {
            throw new IllegalArgumentException("原始数据不能为null");
        }

        if (!(rawData instanceof PubmedArticle)) {
            throw new IllegalArgumentException(
                    "不支持的数据类型: " + rawData.getClass().getName() + ", 期望: PubmedArticle");
        }

        PubmedArticle article = (PubmedArticle) rawData;
        return converter.toCanonicalLiterature(article);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 构建搜索参数
     */
    private JsonNode buildSearchParams(ProviderRequest request) {
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

    /**
     * 提取PMID列表
     */
    private List<String> extractPmids(ESearchResponse response) {
        if (response == null || response.result() == null) {
            return List.of();
        }
        List<String> idList = response.result().idList();
        return CollectionUtils.isEmpty(idList) ? List.of() : idList;
    }

    /**
     * 提取游标令牌
     */
    private String extractCursorToken(ESearchResponse response) {
        if (response == null || response.result() == null) {
            return null;
        }
        return response.result().webEnv();
    }

    /**
     * 获取文章数据（根据数量选择直接EFetch或通过EPost）
     */
    private List<PubmedArticle> fetchArticles(List<String> pmids, ProvenanceConfig config)
            throws InterruptedException {
        int threshold = resolveEpostThreshold(config);
        if (pmids.size() <= threshold) {
            return fetchArticlesDirectly(pmids, config);
        }
        return fetchArticlesViaEPost(pmids, config);
    }

    /**
     * 解析EPost阈值
     */
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

    /**
     * 直接通过EFetch获取文章
     */
    private List<PubmedArticle> fetchArticlesDirectly(List<String> pmids, ProvenanceConfig config) {
        String idParam = String.join(",", pmids);
        log.debug("PubMed direct EFetch start: count={}", pmids.size());
        EFetchRequest request = new EFetchRequest(PROVENANCE_CODE, idParam);
        EFetchResponse response = pubMedClient.efetch(request, config);
        List<PubmedArticle> articles = response != null ? response.articles() : List.of();
        log.debug("PubMed direct EFetch completed: returned={}", articles.size());
        return articles;
    }

    /**
     * 通过EPost获取文章
     */
    private List<PubmedArticle> fetchArticlesViaEPost(List<String> pmids, ProvenanceConfig config)
            throws InterruptedException {
        log.info("PubMed EPost strategy triggered: count={}", pmids.size());

        // 1. EPost: 上传ID列表
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

        // 2. EFetch: 使用WebEnv获取数据
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

        // Gentle delay per NCBI recommendation
        TimeUnit.MILLISECONDS.sleep(600L);

        List<PubmedArticle> articles = response != null ? response.articles() : List.of();
        log.debug("PubMed EPost EFetch completed: returned={}", articles.size());
        return articles;
    }

    /**
     * 转换文章列表为标准格式
     */
    private ConversionOutcome convertArticles(List<PubmedArticle> articles) {
        if (CollectionUtils.isEmpty(articles)) {
            return new ConversionOutcome(List.of(), 0, List.of());
        }

        List<CanonicalLiterature> literatures = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int attempted = 0;

        for (PubmedArticle article : articles) {
            if (article == null) {
                continue;
            }
            attempted++;
            try {
                CanonicalLiterature converted = converter.toCanonicalLiterature(article);
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
        return new ConversionOutcome(List.copyOf(literatures), attempted, List.copyOf(failures));
    }

    /**
     * 构建转换警告消息
     */
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

    /**
     * 分类客户端异常消息
     */
    private String classifyErrorMessage(ProvenanceClientException ex) {
        Integer status = ex.getStatusCode();
        if (status == null) {
            return "PubMed client error: " + ex.getMessage();
        }
        if (status == 429 || status == 503 || status == 502 || status >= 500) {
            return "PubMed service unavailable (status=%d)".formatted(status);
        }
        if (status == 401 || status == 403) {
            return "PubMed authentication failure (status=%d)".formatted(status);
        }
        if (status >= 400 && status < 500) {
            return "PubMed request rejected (status=%d)".formatted(status);
        }
        return "PubMed unexpected response (status=%d)".formatted(status);
    }

    /**
     * 记录转换指标
     */
    private void recordConversionMetrics(int successCount, int failureCount) {
        if (metrics == null) {
            return;
        }
        metrics.recordConversionMetrics(ProvenanceCode.PUBMED, successCount, failureCount);
    }

    /**
     * 转换结果记录
     */
    private record ConversionOutcome(
            List<CanonicalLiterature> literatures,
            int attempted,
            List<String> failedPmids) {}
}
