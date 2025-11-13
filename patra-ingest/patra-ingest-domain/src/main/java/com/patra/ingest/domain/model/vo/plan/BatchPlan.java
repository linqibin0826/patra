package com.patra.ingest.domain.model.vo.plan;

import java.util.Map;
import java.util.Optional;

/**
 * 批次计划 - Ingest 领域模型
 *
 * <p>封装批次生成所需的计划信息，屏蔽外部数据源的实现细节。
 *
 * <p><strong>职责</strong>：
 * <ul>
 *   <li>提供总记录数，用于计算批次数量</li>
 *   <li>提供数据源标识，用于策略路由</li>
 *   <li>提供状态令牌（opaque），用于跨批次传递上下文</li>
 * </ul>
 *
 * <p><strong>设计原则</strong>：
 * <ul>
 *   <li>与外部数据源实现解耦</li>
 *   <li>不包含框架或技术细节</li>
 *   <li>状态令牌为 opaque 类型，Ingest 不解析其内容</li>
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.3.0
 */
public interface BatchPlan {

    /**
     * 获取总记录数
     *
     * <p>用于批次生成器计算需要生成的批次数量
     *
     * @return 总记录数（>= 0）
     */
    int totalRecords();

    /**
     * 获取数据源代码
     *
     * <p>用于批次生成策略的路由选择
     *
     * @return 数据源代码（如 "pubmed", "doaj", "epmc"）
     */
    String dataSourceCode();

    /**
     * 检查是否包含状态令牌
     *
     * <p>某些数据源（如 PubMed）在计划阶段会返回状态令牌（如 WebEnv），
     * 执行阶段可以使用该令牌避免重复查询。
     *
     * @return 如果包含状态令牌则返回 true
     */
    boolean hasStateToken();

    /**
     * 获取状态令牌
     *
     * <p>状态令牌是 opaque 的，Ingest 不解析其内容，只负责在批次间传递。
     * 具体的令牌格式由数据源定义（如 PubMed 的 webEnv + queryKey）。
     *
     * @return 状态令牌的键值对（如果存在）
     */
    Optional<Map<String, String>> stateToken();

    /**
     * 创建空的批次计划（表示无可用数据）
     *
     * @param dataSourceCode 数据源代码
     * @return 空批次计划
     */
    static BatchPlan empty(String dataSourceCode) {
        return new EmptyBatchPlan(dataSourceCode);
    }
}

/**
 * 空批次计划实现（包级私有）
 */
record EmptyBatchPlan(String dataSourceCode) implements BatchPlan {

    @Override
    public int totalRecords() {
        return 0;
    }

    @Override
    public boolean hasStateToken() {
        return false;
    }

    @Override
    public Optional<Map<String, String>> stateToken() {
        return Optional.empty();
    }
}
