package dev.patra.tool.jpa.support;

import org.springframework.data.jpa.repository.JpaRepository;

/// 集成测试专用 Dao。
public interface JpaITPersistableDao extends JpaRepository<JpaITPersistableEntity, Long> {}
