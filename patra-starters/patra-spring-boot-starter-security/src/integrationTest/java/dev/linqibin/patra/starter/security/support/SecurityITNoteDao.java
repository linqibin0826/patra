package dev.linqibin.patra.starter.security.support;

import org.springframework.data.jpa.repository.JpaRepository;

/// 集成测试专用实体的 JPA Repository。
public interface SecurityITNoteDao extends JpaRepository<SecurityITNoteEntity, Long> {}
