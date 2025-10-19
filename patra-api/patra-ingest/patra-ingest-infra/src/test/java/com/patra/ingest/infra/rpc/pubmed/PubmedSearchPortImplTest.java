package com.patra.ingest.infra.rpc.pubmed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.model.vo.PlanMetadata;
import com.patra.starter.provenance.pubmed.PubMedClient;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PubmedSearchPortImplTest {

  @Mock private PubMedClient pubMedClient;

  private PubmedSearchPortImpl port;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    port = new PubmedSearchPortImpl(pubMedClient);
  }

  @Test
  void shouldRequestUseHistoryAndReturnMetadata() {
    ObjectNode params = JsonMapperHolder.getObjectMapper().createObjectNode();
    params.put("retmax", 100);
    params.put("datetype", "pdat");

    ESearchResponse.Result result =
        new ESearchResponse.Result(
            123, 0, 0, List.of(), List.of(), List.of(), "env123", "1", "term", null, null);
    ESearchResponse response = new ESearchResponse(null, result);
    when(pubMedClient.esearch(any(ESearchRequest.class))).thenReturn(response);

    PlanMetadata metadata = port.preparePlanMetadata("covid", params, null);

    assertThat(metadata.totalCount()).isEqualTo(123);
    assertThat(metadata.webEnv()).isEqualTo("env123");
    assertThat(metadata.queryKey()).isEqualTo("1");
    assertThat(metadata.hasWebEnv()).isTrue();

    ArgumentCaptor<ESearchRequest> captor = ArgumentCaptor.forClass(ESearchRequest.class);
    verify(pubMedClient).esearch(captor.capture());
    ESearchRequest request = captor.getValue();
    assertThat(request.usehistory()).isEqualTo("y");
    assertThat(request.retmax()).isEqualTo(0);
    assertThat(request.rettype()).isNull();
  }

  @Test
  void shouldReturnEmptyMetadataWhenResultMissing() {
    when(pubMedClient.esearch(any(ESearchRequest.class))).thenReturn(null);

    PlanMetadata metadata = port.preparePlanMetadata("heart", null, null);

    assertThat(metadata.totalCount()).isZero();
    assertThat(metadata.hasWebEnv()).isFalse();
  }
}
