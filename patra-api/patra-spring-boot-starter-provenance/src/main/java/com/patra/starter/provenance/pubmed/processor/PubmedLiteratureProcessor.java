package com.patra.starter.provenance.pubmed.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.common.model.CanonicalLiterature;
import com.patra.common.model.DataType;
import com.patra.starter.provenance.boot.ProvenanceProperties;
import com.patra.starter.provenance.common.config.BatchingConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.common.processor.DataProcessor;
import com.patra.starter.provenance.common.processor.ProcessResult;
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
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

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
 * @since 0.1.0
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
            // 1. 准备处理上下文
            ProcessingContext ctx = prepareProcessingContext(request, operation, batchNo);

            // 2. 执行搜索并获取PMID列表
            SearchResult searchResult = executeSearch(ctx);
            if (searchResult.isEmpty()) {
                return createEmptyResult(operation, batchNo, start, searchResult.cursor());
            }

            // 3. 获取文章数据
            List<PubmedArticle> articles = fetchArticles(searchResult.pmids(), ctx.config());

            // 4. 转换为规范格式
            ConversionOutcome outcome = convertArticles(articles);

            // 5. 构建并返回结果
            return buildProcessResult(outcome, searchResult.cursor(), operation, batchNo, start);

        } catch (ProvenanceClientException ex) {
            return handleClientException(ex, operation, batchNo);
        } catch (InterruptedException ex) {
            return handleInterruptedException(ex, operation, batchNo);
        } catch (Exception ex) {
            return handleUnexpectedException(ex, operation, batchNo);
        }
    }

    @Override
    public ValidationResult validate(CanonicalLiterature data) {
        if (data == null) {
            return ValidationResult.failure("文献数据不能为null");
        }

        List<String> errors = new ArrayList<>();

        // 验证必填字段
        if (data.getIdentifiers() == null || StrUtil.isBlank(data.getIdentifiers().get("pmid"))) {
            errors.add("PMID不能为空");
        }
        if (StrUtil.isBlank(data.getTitle())) {
            errors.add("标题不能为空");
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        return ValidationResult.success();
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
     * 准备处理上下文
     */
    private ProcessingContext prepareProcessingContext(
            ProviderRequest request,
            String operation,
            int batchNo) {
        ProvenanceConfig config = properties.mergeWithRuntime(PROVENANCE_CODE, request.config());
        JsonNode searchParams = buildSearchParams(request);
        ESearchRequest searchRequest = ESEARCH_ASSEMBLER.buildList(searchParams);

        return new ProcessingContext(config, searchRequest, operation, batchNo);
    }

    /**
     * 处理上下文记录
     */
    private record ProcessingContext(
        ProvenanceConfig config,
        ESearchRequest searchRequest,
        String operation,
        int batchNo
    ) {}

    /**
     * 执行搜索并提取PMID列表
     */
    private SearchResult executeSearch(ProcessingContext ctx) {
        ESearchResponse response = pubMedClient.esearch(ctx.searchRequest(), ctx.config());
        List<String> pmids = extractPmids(response);
        String cursor = extractCursorToken(response);

        if (pmids.isEmpty()) {
            log.info("PubMed ESearch 未找到结果");
        } else {
            log.debug("PubMed ESearch 找到 {} 个 PMID", pmids.size());
        }

        return new SearchResult(pmids, cursor);
    }

    /**
     * 搜索结果记录
     */
    private record SearchResult(List<String> pmids, String cursor) {
        boolean isEmpty() {
            return pmids == null || pmids.isEmpty();
        }
    }

    /**
     * 创建空结果（未找到数据时）
     */
    private ProcessResult<CanonicalLiterature> createEmptyResult(
            String operation,
            int batchNo,
            long startTime,
            String cursor) {
        log.info(
            "PubMed Literature Processor empty result: operation={} batchNo={} duration={}ms",
            operation, batchNo, System.currentTimeMillis() - startTime);
        return ProcessResult.success(List.of(), cursor);
    }

    /**
     * 构建处理结果
     */
    private ProcessResult<CanonicalLiterature> buildProcessResult(
            ConversionOutcome outcome,
            String nextCursor,
            String operation,
            int batchNo,
            long startTime) {

        ProcessResult<CanonicalLiterature> result = outcome.failedPmids().isEmpty()
            ? ProcessResult.success(outcome.literatures(), nextCursor)
            : ProcessResult.partialSuccess(
                outcome.literatures(),
                nextCursor,
                buildConversionWarning(outcome.failedPmids()));

        log.info(
            "PubMed Literature Processor success: operation={} batchNo={} fetched={} attempted={} duration={}ms",
            operation, batchNo, result.data().size(), outcome.attempted(),
            System.currentTimeMillis() - startTime);

        return result;
    }

    /**
     * 处理客户端异常（HTTP错误、超时等）
     */
    private ProcessResult<CanonicalLiterature> handleClientException(
            ProvenanceClientException ex,
            String operation,
            int batchNo) {
        log.warn(
            "PubMed Literature Processor client error: operation={} batchNo={} status={} message={}",
            operation, batchNo, ex.getStatusCode(), ex.getMessage(), ex);
        return ProcessResult.failure(formatErrorMessage(ex));
    }

    /**
     * 处理中断异常（任务取消或超时）
     */
    private ProcessResult<CanonicalLiterature> handleInterruptedException(
            InterruptedException ex,
            String operation,
            int batchNo) {
        Thread.currentThread().interrupt();
        log.error(
            "PubMed Literature Processor interrupted: operation={} batchNo={}",
            operation, batchNo, ex);
        return ProcessResult.failure("PubMed processor interrupted");
    }

    /**
     * 处理未预期的异常（代码Bug或系统错误）
     */
    private ProcessResult<CanonicalLiterature> handleUnexpectedException(
            Exception ex,
            String operation,
            int batchNo) {
        log.error(
            "PubMed Literature Processor unexpected error: operation={} batchNo={}",
            operation, batchNo, ex);
        return ProcessResult.failure("Unexpected PubMed error: " + ex.getMessage());
    }

    /**
     * 构建搜索参数
     */
    private JsonNode buildSearchParams(ProviderRequest request) {
        BatchExecutionParams exec = request.executionParams();
        JsonNode params = exec.params();
        String query = exec.query();

        if (StrUtil.isBlank(query)) {
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
            return CollUtil.newArrayList();
        }
        List<String> idList = response.result().idList();
        return CollUtil.isEmpty(idList) ? CollUtil.newArrayList() : idList;
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
        String idParam = StrUtil.join(",", pmids);
        log.debug("PubMed direct EFetch start: count={}", pmids.size());
        EFetchRequest request = new EFetchRequest(PROVENANCE_CODE, idParam);
        EFetchResponse response = pubMedClient.efetch(request, config);
        List<PubmedArticle> articles = response != null ? response.articles() : CollUtil.newArrayList();
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
        String idParam = StrUtil.join(",", pmids);
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

        List<PubmedArticle> articles = response != null ? response.articles() : CollUtil.newArrayList();
        log.debug("PubMed EPost EFetch completed: returned={}", articles.size());
        return articles;
    }

    /**
     * 转换文章列表为标准格式
     */
    private ConversionOutcome convertArticles(List<PubmedArticle> articles) {
        if (CollUtil.isEmpty(articles)) {
            return new ConversionOutcome(CollUtil.newArrayList(), 0, CollUtil.newArrayList());
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
        int sampleSize = Math.min(failedPmids.size(), WARNING_ID_SAMPLE_LIMIT);
        List<String> sample = CollUtil.sub(failedPmids, 0, sampleSize);
        String message = StrUtil.format("Conversion failed for pmid(s): {}", StrUtil.join(",", sample));
        if (failedPmids.size() > WARNING_ID_SAMPLE_LIMIT) {
            message += StrUtil.format(" +{} more", failedPmids.size() - sampleSize);
        }
        return message;
    }

    /**
     * 格式化客户端异常消息
     *
     * <p>根据HTTP状态码将异常转换为用户友好的错误消息。
     *
     * @param ex 客户端异常
     * @return 格式化的错误消息
     */
    private String formatErrorMessage(ProvenanceClientException ex) {
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
