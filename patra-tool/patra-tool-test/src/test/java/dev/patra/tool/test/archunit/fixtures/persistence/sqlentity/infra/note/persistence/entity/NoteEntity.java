package dev.patra.tool.test.archunit.fixtures.persistence.sqlentity.infra.note.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/// 夹具：原生 SQL 服务里出现 JPA 实体，违规。
@Entity
public class NoteEntity {

  @Id private Long id;
}
