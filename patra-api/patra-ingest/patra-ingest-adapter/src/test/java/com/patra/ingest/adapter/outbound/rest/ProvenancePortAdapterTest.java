package com.patra.ingest.adapter.outbound.rest;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.adapter.outbound.rest.converter.ProvenanceConfigSnapshotConverter;
import com.patra.ingest.domain.exception.IngestConfigurationException;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.registry.api.rpc.client.ProvenanceClient;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceConfigResp;
import com.patra.starter.feign.error.exception.RemoteCallException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProvenancePortAdapter} 单元测试，验证 ProblemDetail 异常处理策略。
 */
class ProvenancePortAdapterTest {

    private final ProvenanceClient provenanceClient = mock(ProvenanceClient.class);
    private final ProvenanceConfigSnapshotConverter converter = mock(ProvenanceConfigSnapshotConverter.class);
    private final ProvenancePortAdapter adapter = new ProvenancePortAdapter(provenanceClient, converter);

    @Test
    @DisplayName("正常返回时调用转换器并返回快照")
    void shouldConvertRemoteConfiguration() {
        ProvenanceConfigResp resp = new ProvenanceConfigResp(null, null, null, null, null, null, null, null, List.of());
        ProvenanceConfigSnapshot snapshot = minimalSnapshot("PUBMED");
        when(provenanceClient.getConfiguration(any(), any(), any(), any())).thenReturn(resp);
        when(converter.convert(resp)).thenReturn(snapshot);

        ProvenanceConfigSnapshot result = adapter.fetchConfig(ProvenanceCode.PUBMED, Endpoint.SEARCH, OperationCode.FULL);

        assertThat(result).isSameAs(snapshot);
        verify(provenanceClient).getConfiguration(eq(ProvenanceCode.PUBMED), eq(OperationCode.FULL.name()),
                eq(Endpoint.SEARCH.name()), any());
    }

    @Test
    @DisplayName("Registry 未找到时降级为最小快照")
    void shouldFallbackToMinimalSnapshotWhenNotFound() {
        RemoteCallException remote = new RemoteCallException("REG-0404", 404, "not found",
                "method", "trace-404", Map.of());
        when(provenanceClient.getConfiguration(any(), any(), any(), any())).thenThrow(remote);

        ProvenanceConfigSnapshot result = adapter.fetchConfig(ProvenanceCode.PUBMED, Endpoint.SEARCH, OperationCode.FULL);

        assertThat(result.provenance().code()).isEqualTo(ProvenanceCode.PUBMED.getCode());
        assertThat(result.credentials()).isEmpty();
        assertThat(result.endpoint()).isNull();
    }

    @Test
    @DisplayName("Registry 服务端错误时降级为最小快照")
    void shouldFallbackToMinimalSnapshotWhenServerError() {
        RemoteCallException remote = new RemoteCallException("REG-0500", 503, "service unavailable",
                "method", "trace-503", Map.of());
        when(provenanceClient.getConfiguration(any(), any(), any(), any())).thenThrow(remote);

        ProvenanceConfigSnapshot result = adapter.fetchConfig(ProvenanceCode.PUBMED, Endpoint.SEARCH, OperationCode.FULL);

        assertThat(result.provenance().code()).isEqualTo(ProvenanceCode.PUBMED.getCode());
        assertThat(result.credentials()).isEmpty();
    }

    @Test
    @DisplayName("不可恢复的客户端错误抛出 IngestConfigurationException")
    void shouldRaiseConfigurationExceptionOnClientError() {
        RemoteCallException remote = new RemoteCallException("REG-0422", 422, "invalid config",
                "method", "trace-422", Map.of());
        when(provenanceClient.getConfiguration(any(), any(), any(), any())).thenThrow(remote);

        assertThatThrownBy(() -> adapter.fetchConfig(ProvenanceCode.PUBMED, Endpoint.SEARCH, OperationCode.FULL))
                .isInstanceOf(IngestConfigurationException.class)
                .hasCause(remote);
    }

    private ProvenanceConfigSnapshot minimalSnapshot(String code) {
        return new ProvenanceConfigSnapshot(
                new ProvenanceConfigSnapshot.ProvenanceInfo(null, code, null, null, null, null, true, null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}
