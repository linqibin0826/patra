package com.patra.ingest.app.usecase.execution.strategy.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.exception.BatchPlanningException;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.batch.BatchPlan;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.plan.PlanMetadata;
import com.patra.ingest.domain.port.PubmedSearchPort;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PubMed 专用批处理规划器,使用 ESearch 计数结果创建基于分页的批次。
 *
 * <p>核心职责:
 *
 * <ul>
 *   <li>调用 PubMed ESearch API 获取结果总数
 *   <li>根据总数和页面大小计算所需批次数
 *   <li>生成带有 retstart/retmax 参数的批次计划
 *   <li>支持 WebEnv 机制优化大结果集检索
 * </ul>
 *
 * <p>批次生成逻辑:
 *
 * <ol>
 *   <li>通过 ESearch 获取结果总数和 WebEnv(如果可用)
 *   <li>计算页面数: ceil(总数 / 页面大小)
 *   <li>验证页面数不超过 maxPages 限制
 *   <li>为每个页面生成一个批次,设置 retstart 和 retmax
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@Slf4j
public class PubmedBatchPlanner implements BatchPlanner {

  private final PubmedSearchPort searchPort;
  private final ObjectMapper objectMapper;
  private final int pubmedRetmaxLimit;

  /**
   * 构造函数,带强制配置验证。
   *
   * @param searchPort PubMed 搜索端口
   * @param objectMapper JSON 对象映射器
   * @param pubmedRetmaxLimit PubMed API retmax 限制(必须配置)
   * @throws IllegalArgumentException 如果 pubmedRetmaxLimit 不是正数
   */
  public PubmedBatchPlanner(
      PubmedSearchPort searchPort,
      ObjectMapper objectMapper,
      @Value("${patra.ingest.pubmed.retmax-limit:10000}") int pubmedRetmaxLimit) {
    // 验证 retmax 限制必须为正数
    if (pubmedRetmaxLimit <= 0) {
      throw new IllegalArgumentException(
          "patra.ingest.pubmed.retmax-limit 必须配置且为正数, 当前值: " + pubmedRetmaxLimit);
    }
    this.searchPort = searchPort;
    this.objectMapper = objectMapper;
    this.pubmedRetmaxLimit = pubmedRetmaxLimit;
  }

  @Override
  public ProvenanceCode getProvenanceCode() {
    return ProvenanceCode.PUBMED;
  }

  /**
   * 为 PubMed 数据采集规划批次。
   *
   * <p>算法步骤:
   *
   * <ol>
   *   <li>验证查询或参数至少有一个非空(支持日期范围查询)
   *   <li>调用 ESearch 获取结果总数和 WebEnv
   *   <li>如果结果为0,返回空计划
   *   <li>解析页面大小和最大页数配置
   *   <li>计算所需页面数,验证不超过限制
   *   <li>为每个页面创建批次,设置 retstart/retmax
   *   <li>如果有 WebEnv,添加到批次参数中
   * </ol>
   *
   * @param ctx 执行上下文,包含编译后的查询和配置快照
   * @return 批次计划,包含所有批次或空计划
   * @throws BatchPlanningException 如果查询和参数都为空
   */
  @Override
  public BatchPlan plan(ExecutionContext ctx) {
    String compiledQuery = ctx.compiledQuery();
    JsonNode compiledParams = ctx.compiledParams();

    // 步骤1: 验证查询或参数至少有一个非空
    // 对于日期范围查询,query 可能为空,而 params 包含 from/to/datetype
    boolean hasQuery = compiledQuery != null && !compiledQuery.isBlank();
    boolean hasParams = compiledParams != null && !compiledParams.isEmpty();

    if (!hasQuery && !hasParams) {
      throw new BatchPlanningException("PubMed 批处理规划失败: compiledQuery 和 compiledParams 均为空");
    }

    ObjectNode baseParams = toObjectNode(compiledParams);

    // 步骤2: 调用 ESearch 获取结果总数和 WebEnv
    log.debug("调用 PubMed ESearch 准备计划元数据 queryHash={}", safeHash(compiledQuery));
    PlanMetadata metadata =
        searchPort.preparePlanMetadata(compiledQuery, ctx.compiledParams(), ctx.configSnapshot());
    int total = metadata.totalCount();

    // 步骤3: 如果结果为0,返回空计划
    if (total <= 0) {
      log.info("pubmed 规划器: 无结果 termHash={}", safeHash(compiledQuery));
      return BatchPlan.empty();
    }

    // 步骤4: 解析页面大小和最大页数配置
    int pageSize = resolvePageSize(baseParams, ctx.configSnapshot());
    int maxPages = resolveMaxPages(ctx.configSnapshot());
    log.debug(
        "批次规划参数 pageSize={} maxPages={} total={} queryHash={}",
        pageSize,
        maxPages,
        total,
        safeHash(compiledQuery));

    // 步骤5: 计算所需页面数,验证不超过限制
    int pagesNeeded = (int) Math.ceil(total / (double) pageSize);
    if (pagesNeeded > maxPages) {
      log.warn(
          "pubmed 规划器快速失败: pagesNeeded={} > maxPages={} termHash={} pageSize={} total={}",
          pagesNeeded,
          maxPages,
          safeHash(compiledQuery),
          pageSize,
          total);
      // 返回空批次列表,但标记为超出限制
      return new BatchPlan(List.of(), pagesNeeded, true);
    }

    // 步骤6: 为每个页面创建批次
    int pages = Math.min(pagesNeeded, maxPages);
    List<Batch> batches = new ArrayList<>(pages);
    for (int i = 0; i < pages; i++) {
      int retstart = i * pageSize;
      ObjectNode batchParams = baseParams.deepCopy();

      // 设置批次相关的控制参数
      batchParams.put("retstart", retstart);
      batchParams.put("retmax", pageSize);
      batchParams.put("retmode", "json");

      // 移除仅计数的设置,确保真实获取数据
      if (batchParams.has("rettype")) {
        batchParams.remove("rettype");
      }

      // 步骤7: 如果有 WebEnv,添加到批次参数中
      if (metadata.hasWebEnv()) {
        batchParams.put("WebEnv", metadata.webEnv());
        batchParams.put("query_key", metadata.queryKey());
      }

      batches.add(new Batch(i + 1, compiledQuery, batchParams, null, i + 1, pageSize));
    }

    log.info(
        "pubmed 规划器: 已规划 {} 个批次 termHash={} pageSize={} total={} webEnv={}",
        pages,
        safeHash(compiledQuery),
        pageSize,
        total,
        metadata.hasWebEnv() ? "已启用" : "未启用");
    return new BatchPlan(batches, pages, false);
  }

  /**
   * 将 JsonNode 转换为 ObjectNode,确保可以进行 put 操作。
   *
   * @param node 原始 JsonNode
   * @return ObjectNode 实例
   */
  private ObjectNode toObjectNode(JsonNode node) {
    if (node == null || node.isNull()) {
      return objectMapper.createObjectNode();
    }
    if (node.isObject()) {
      return node.deepCopy();
    }
    return objectMapper.valueToTree(node);
  }

  /**
   * 解析页面大小,优先级: 参数中的 retmax > 配置的 pageSizeValue。
   *
   * <p>验证规则:
   *
   * <ol>
   *   <li>必须至少有一个来源提供页面大小
   *   <li>页面大小必须为正数
   *   <li>不能超过 PubMed API 限制(pubmedRetmaxLimit)
   * </ol>
   *
   * @param params 批次参数
   * @param snapshot 配置快照
   * @return 有效的页面大小
   * @throws BatchPlanningException 如果没有配置或值无效
   */
  private int resolvePageSize(ObjectNode params, ProvenanceConfigSnapshot snapshot) {
    // 步骤1: 尝试从参数和配置中获取页面大小
    Integer fromParams = intOrNull(params, "retmax");
    Integer fromCfg =
        snapshot != null && snapshot.pagination() != null
            ? snapshot.pagination().pageSizeValue()
            : null;

    // 步骤2: 验证至少有一个来源提供了页面大小
    if (fromParams == null && fromCfg == null) {
      throw new BatchPlanningException("页面大小配置是强制的: 'retmax' 参数和 pagination.pageSizeValue 均未配置");
    }

    // 步骤3: 选择页面大小(参数优先)
    int pageSize = fromParams != null ? fromParams : fromCfg;

    // 步骤4: 验证页面大小为正数
    if (pageSize <= 0) {
      throw new BatchPlanningException("页面大小必须为正数, 当前值: " + pageSize);
    }

    // 步骤5: 限制为 PubMed API 上限
    if (pageSize > pubmedRetmaxLimit) {
      log.warn("pubmed 规划器: retmax 从 {} 限制为 {}", pageSize, pubmedRetmaxLimit);
      pageSize = pubmedRetmaxLimit;
    }
    return pageSize;
  }

  /**
   * 解析单次执行的最大页数,从配置快照中获取。
   *
   * @param snapshot 配置快照
   * @return 最大页数,默认为 1(保守策略)
   */
  private int resolveMaxPages(ProvenanceConfigSnapshot snapshot) {
    Integer max =
        snapshot != null && snapshot.pagination() != null
            ? snapshot.pagination().maxPagesPerExecution()
            : null;
    // 如果未设置,使用保守的默认值 1
    return (max != null && max > 0) ? max : 1;
  }

  /**
   * 从 ObjectNode 中提取整数值,支持数字和文本格式。
   *
   * @param node JSON 对象节点
   * @param field 字段名
   * @return 整数值,如果不存在或无法解析则返回 null
   */
  private static Integer intOrNull(ObjectNode node, String field) {
    JsonNode v = node.get(field);
    if (v == null || v.isNull()) return null;
    if (v.isInt() || v.isLong()) return v.asInt();
    if (v.isTextual()) {
      try {
        return Integer.parseInt(v.asText());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  /**
   * 为字符串生成安全的哈希值(十六进制),用于日志记录。
   *
   * @param s 输入字符串
   * @return 十六进制哈希值,null 返回 "null"
   */
  private static String safeHash(String s) {
    if (s == null) return "null";
    int h = s.hashCode();
    return Integer.toHexString(h);
  }
}
