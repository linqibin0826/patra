package com.patra.ingest.adapter.outbound.rest;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.adapter.outbound.rest.converter.ProvenanceConfigSnapshotConverter;
import com.patra.ingest.app.port.ProvenancePort;
import com.patra.ingest.domain.exception.IngestConfigurationException;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.registry.api.rpc.client.ProvenanceClient;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceConfigResp;
import com.patra.starter.feign.error.exception.RemoteCallException;
import com.patra.starter.feign.error.util.RemoteErrorHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;

/**
 * 调用 patra-registry 的出站适配器，实现来源配置查询。
 *
 * <p>遵循六边形架构模式，作为基础设施层组件实现应用层端口。
 * 负责从 Registry 服务获取完整的 Provenance 配置快照，支持失败重试和错误处理。</p>
 *
 * <p>特性：
 * - 完整的错误处理和日志记录
 * - 使用 MapStruct 进行类型安全的数据转换
 * - 优雅降级，返回最小可用配置</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvenancePortAdapter implements ProvenancePort {

    /**
     * Registry RPC 客户端
     */
    private final ProvenanceClient provenanceClient;
    /**
     * 配置快照转换器
     */
    private final ProvenanceConfigSnapshotConverter converter;

    /**
     * 调用 Registry 获取来源配置。
     */
    @Override
    public ProvenanceConfigSnapshot fetchConfig(ProvenanceCode provenanceCode, Endpoint endpointCode, OperationCode operationCode) {
        String code = provenanceCode.getCode();
        String endpoint = endpointCode.name();
        String taskType = operationCode.name();
        Instant queryTime = Instant.now();

        log.debug("[INGEST][ADAPTER] Requesting provenance config, code={}, taskType={}, endpoint={}, at={}",
                code, taskType, endpoint, queryTime);

        try {
            ProvenanceConfigResp resp = provenanceClient.getConfiguration(provenanceCode, taskType, endpoint, queryTime);

            if (resp == null) {
                log.warn("[INGEST][ADAPTER] Registry returned empty config, code={}, taskType={}, endpoint={}", code, taskType, endpoint);
                return createMinimalSnapshot(code);
            }

            ProvenanceConfigSnapshot snapshot = converter.convert(resp);

            // 日志输出转换结果哈希信息便于诊断（snapshot 本身含 toString）
            log.debug("[INGEST][ADAPTER] Provenance config loaded, code={}, snapshot={}", code, snapshot);
            return snapshot;

        } catch (RemoteCallException ex) {
            return handleRemoteException(ex, code, taskType, endpoint);
        } catch (Exception ex) {
            String msg = String.format("Unexpected error when fetching config, code=%s, taskType=%s, endpoint=%s",
                    code, taskType, endpoint);
            log.error("[INGEST][ADAPTER] " + msg, ex);
            throw new IngestConfigurationException(code, taskType, endpoint, msg, ex);
        }
    }

    /**
     * 处理远端 ProblemDetail 异常。
     */
    private ProvenanceConfigSnapshot handleRemoteException(RemoteCallException ex,
                                                           String code,
                                                           String taskType,
                                                           String endpoint) {
        if (RemoteErrorHelper.isNotFound(ex)) {
            String msg = String.format("Provenance config not found, code=%s, taskType=%s, endpoint=%s", code, taskType, endpoint);
            log.warn("[INGEST][ADAPTER] {} (remoteCode={}, status={}, traceId={})", msg, ex.getErrorCode(), ex.getHttpStatus(), ex.getTraceId());
            throw new IngestConfigurationException(code, taskType, endpoint, msg, ex);
        }

        if (RemoteErrorHelper.isServerError(ex) || RemoteErrorHelper.isRetryable(ex)) {
            log.warn("[INGEST][ADAPTER] Registry unavailable, fallback to minimal snapshot, code={}, status={}, traceId={}",
                    code, ex.getHttpStatus(), ex.getTraceId());
            return createMinimalSnapshot(code);
        }

        String msg = "Registry client error" +
                String.format(
                        ", code=%s, status=%d, remoteCode=%s, traceId=%s, detail=%s",
                        code,
                        ex.getHttpStatus(),
                        ex.getErrorCode(),
                        ex.getTraceId(),
                        ex.getMessage());
        log.error("[INGEST][ADAPTER] " + msg);
        throw new IngestConfigurationException(code, taskType, endpoint, msg, ex);
    }

    /**
     * 创建最小可用配置快照。
     */
    private ProvenanceConfigSnapshot createMinimalSnapshot(String provenanceCode) {
    log.info("[INGEST][ADAPTER] Creating minimal provenance snapshot, code={}", provenanceCode);

        // 创建最小的 ProvenanceInfo
        ProvenanceConfigSnapshot.ProvenanceInfo minimalProvenance =
                new ProvenanceConfigSnapshot.ProvenanceInfo(
                        null, // id
                        provenanceCode, // code
                        null, // name
                        null, // baseUrlDefault
                        null, // timezoneDefault
                        null, // docsUrl
                        true, // active
                        null  // lifecycleStatusCode
                );

        return new ProvenanceConfigSnapshot(
                minimalProvenance,
                null, // endpoint
                null, // windowOffset
                null, // pagination
                null, // http
                null, // batching
                null, // retry
                null, // rateLimit
                Collections.emptyList() // credentials
        );
    }

}
