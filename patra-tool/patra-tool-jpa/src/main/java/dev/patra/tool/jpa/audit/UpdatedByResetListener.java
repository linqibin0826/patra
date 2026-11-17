package dev.patra.tool.jpa.audit;

import dev.patra.tool.jpa.entity.BaseJpaEntity;
import jakarta.persistence.PreUpdate;

/// 主表行每次更新前先清空 `updated_by`，让没有当前用户的写入（定时任务、导入、消息消费）留下空值。
///
/// Spring Data 审计拿不到当前用户时不动 `updated_by`，加载后就地修改的实体就会留着上一个用户的 ID，
/// 和「空表示系统写入」（库表规范第 3 节）不符。它没有提供「拿不到就清空」的开关，所以在它之前先清空：
/// JPA 按 `@EntityListeners` 里的声明顺序调用监听器，本监听器必须排在 `AuditingEntityListener` 前面，
/// 有当前用户时由它再填上。只在更新时清：插入时实体刚建出来，不会带着别人的 ID。
public class UpdatedByResetListener {

  @PreUpdate
  public void clearUpdatedBy(BaseJpaEntity entity) {
    entity.setUpdatedBy(null);
  }
}
