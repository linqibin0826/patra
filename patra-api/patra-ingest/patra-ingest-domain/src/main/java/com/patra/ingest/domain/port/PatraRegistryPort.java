package com.patra.ingest.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;

/**
 * Patra Registry 访问端口（领域层 Port）。
 *
 * <p>用途：
 * <ul>
 *   <li>为应用层（App）提供访问 Registry 服务（patra-registry）的统一入口；</li>
 *   <li>按来源（provenance）与操作类型（operation）拉取"来源配置快照"；</li>
 *   <li>屏蔽具体调用协议与客户端技术（Feign/HTTP/gRPC 等），保持领域与技术解耦。</li>
 * </ul>
 *
 * <p>分层约束：该接口定义在领域层，具体实现位于基础设施层（infra.rpc.registry）。
 * 应用服务仅依赖本接口进行编排，避免引入任何外部系统细节。</p>
 *
 * <p>错误语义（建议）：
 * <ul>
 *   <li>外部 4xx/数据缺失等不可恢复错误，建议转换为领域/应用异常（例如 IngestConfigurationException）；</li>
 *   <li>外部 5xx/网络超时等可恢复错误，可在实现层做重试/降级（返回最小可用快照）或上抛由上层策略处理；</li>
 *   <li>实现层应记录 traceId/远端错误码，便于问题追踪；本接口不限定具体异常类型，保持调用方灵活性。</li>
 * </ul>
 *
 * <p>线程安全：实现需为无状态或可并发复用；配置型依赖通过 Spring 管理。</p>
 */
public interface PatraRegistryPort {

    /**
     * 获取指定来源在某一操作类型下的"配置快照"。
     *
     * <p>约定：快照应包含调用 Registry 所需的分页/窗口/HTTP/重试/限流等静态参数；
     * 对于临时不可用的场景，允许实现返回"最小可用快照"，但必须在日志中记录降级信息。</p>
     *
     * @param provenanceCode 来源代码（如 PUBMED/EPMC）
     * @param operationCode  业务操作类型（如 HARVEST/BACKFILL/UPDATE）
     * @return Registry 配置快照（不应为 null；必要时可返回最小快照以支撑有限功能）
     * @throws RuntimeException 当出现不可恢复的配置/语义问题时，由实现抛出领域/应用异常
     */
    ProvenanceConfigSnapshot fetchConfig(ProvenanceCode provenanceCode,
                                         OperationCode operationCode);
}

