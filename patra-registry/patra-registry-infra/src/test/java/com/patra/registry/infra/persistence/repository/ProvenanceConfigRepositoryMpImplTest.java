package com.patra.registry.infra.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

import com.patra.registry.infra.persistence.converter.ProvenanceEntityConverter;
import com.patra.registry.infra.persistence.mapper.provenance.*;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvenanceConfigRepositoryMpImplTest {

  @Mock RegProvenanceMapper provenanceMapper;
  @Mock RegProvWindowOffsetCfgMapper windowOffsetCfgMapper;
  @Mock RegProvPaginationCfgMapper paginationCfgMapper;
  @Mock RegProvHttpCfgMapper httpCfgMapper;
  @Mock RegProvBatchingCfgMapper batchingCfgMapper;
  @Mock RegProvRetryCfgMapper retryCfgMapper;
  @Mock RegProvRateLimitCfgMapper rateLimitCfgMapper;
  @Mock ProvenanceEntityConverter converter;

  ProvenanceConfigRepositoryMpImpl repo;

  @BeforeEach
  void setUp() {
    repo =
        new ProvenanceConfigRepositoryMpImpl(
            provenanceMapper,
            windowOffsetCfgMapper,
            paginationCfgMapper,
            httpCfgMapper,
            batchingCfgMapper,
            retryCfgMapper,
            rateLimitCfgMapper,
            converter);
  }

  @Test
  void findActivePagination_nullOperationType_usesALL() {
    repo.findActivePagination(1L, null, null);

    ArgumentCaptor<String> opKey = ArgumentCaptor.forClass(String.class);
    verify(paginationCfgMapper).selectActiveMerged(eq(1L), opKey.capture(), any(Instant.class));
    assertEquals("ALL", opKey.getValue());
  }
}
