package com.patra.registry.infra.persistence.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.exception.provenance.ProvenanceNotFoundException;
import com.patra.registry.infra.persistence.converter.ExprEntityConverter;
import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import com.patra.registry.infra.persistence.mapper.expr.RegExprFieldDictMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvApiParamMapMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvExprCapabilityMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvExprRenderRuleMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvenanceMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExprRepositoryMpImplTest {

  @Mock RegExprFieldDictMapper fieldDictMapper;
  @Mock RegProvApiParamMapMapper apiParamMapMapper;
  @Mock RegProvExprCapabilityMapper capabilityMapper;
  @Mock RegProvExprRenderRuleMapper renderRuleMapper;
  @Mock RegProvenanceMapper provenanceMapper;
  @Mock ExprEntityConverter converter;

  ExprRepositoryMpImpl repo;

  @BeforeEach
  void setUp() {
    repo =
        new ExprRepositoryMpImpl(
            fieldDictMapper,
            apiParamMapMapper,
            capabilityMapper,
            renderRuleMapper,
            provenanceMapper,
            converter);
  }

  @Test
  void loadSnapshot_endpointNull_passesNullToApiParamMapper() {
    // arrange
    RegProvenanceDO entity = new RegProvenanceDO();
    entity.setId(1L);
    when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(entity));
    when(fieldDictMapper.selectAllActive()).thenReturn(Collections.emptyList());
    when(capabilityMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
        .thenReturn(Collections.emptyList());
    when(renderRuleMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
        .thenReturn(Collections.emptyList());
    when(apiParamMapMapper.selectActiveByTask(anyLong(), anyString(), any(), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    // act
    repo.loadSnapshot(ProvenanceCode.PUBMED, " harvest ", null, null);

    // assert
    ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
    verify(apiParamMapMapper)
        .selectActiveByTask(eq(1L), eq("harvest"), endpointCaptor.capture(), any(Instant.class));
    assertNull(
        endpointCaptor.getValue(), "endpoint should be null when input endpointName is null");
  }

  @Test
  void resolveProvenance_notFound_throwDomainException() {
    when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.empty());
    assertThrows(
        ProvenanceNotFoundException.class,
        () -> repo.loadSnapshot(ProvenanceCode.PUBMED, null, null, null));
  }
}
