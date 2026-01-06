package com.patra.catalog.infra.adapter.persistence.converter.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.infra.adapter.persistence.entity.MeshEntryTermEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// MeshScrJpaMapper 单元测试。
@DisplayName("MeshScrJpaMapper 单元测试")
class MeshScrJpaMapperTest {

  private final MeshScrJpaMapper mapper = new MeshScrJpaMapperImpl();

  @Test
  @DisplayName("toEntryTerm 在 lexicalTag 为空时应正常转换")
  void shouldHandleNullLexicalTag() {
    MeshEntryTermEntity entity = new MeshEntryTermEntity();
    entity.setId(1L);
    entity.setOwnerUi("C000001");
    entity.setTerm("Test Term");
    entity.setLexicalTag(null);
    entity.setIsPrintFlag(false);
    entity.setRecordPreferred("N");
    entity.setIsPermutedTerm(false);
    entity.setIsConceptPreferred(false);

    MeshEntryTerm entryTerm = mapper.toEntryTerm(entity);

    assertThat(entryTerm).isNotNull();
    assertThat(entryTerm.getLexicalTag()).isNull();
    assertThat(entryTerm.getTerm()).isEqualTo("Test Term");
  }
}
