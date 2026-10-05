package dev.linqibin.starter.jpa.support;

import org.springframework.data.jpa.repository.JpaRepository;

/// 集成测试专用实体的 JPA Repository。
public interface JpaITAuditedNoteDao extends JpaRepository<JpaITAuditedNoteEntity, Long> {}
