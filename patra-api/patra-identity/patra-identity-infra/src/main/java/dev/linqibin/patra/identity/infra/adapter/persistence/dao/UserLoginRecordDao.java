package dev.linqibin.patra.identity.infra.adapter.persistence.dao;

import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserLoginRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/// `idn_user_login_record` 的 Spring Data 仓库。
public interface UserLoginRecordDao extends JpaRepository<UserLoginRecordEntity, Long> {}
