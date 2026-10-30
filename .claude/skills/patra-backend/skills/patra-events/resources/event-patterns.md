# 领域事件模式详解

## 事件定义规范

- 用 `record` 定义不可变事件，字段只放订阅方需要的数据；多于 4 个字段用 `@Builder`（见 `.claude/rules/code-style.md`）。
- 类注释写清：谁在什么时机、哪个事务里发布，订阅方该用同步还是提交后。
- **位置**：`domain/{子域}/event/`。名字以 `Event` 结尾的领域类必须放在这里（架构测试会查）。

```java
/// 评论已发表（根评论或回复）。讨论子域在写入评论的同一事务里发布。
///
/// 订阅方用同步的 `@EventListener`，同一线程、同一事务；**不要用 `@TransactionalEventListener`**
/// （提交后才执行，提醒会丢）。订阅方抛出异常时整个事务回滚。
@Builder
public record CommentPublishedEvent(
    long commentId,
    SubjectId subjectId,
    SubjectType subjectType,
    long authorId,
    List<Long> mentionedUserIds,
    Instant publishedAt
    /* …其余字段见 interaction 的实际定义 */) {

  public CommentPublishedEvent {
    mentionedUserIds = mentionedUserIds != null ? List.copyOf(mentionedUserIds) : List.of();
  }
}
```

## 监听器模式

### 同成同败：同步 `@EventListener`（项目里最常用）

```java
/// 同步 `@EventListener`：与发布方同一线程、同一事务；抛出异常时评论、发表计数与提醒一起回滚。
/// 不加 `@Transactional`，也不能用 `@TransactionalEventListener`（提交后才执行，提醒会丢）。
@Component
@RequiredArgsConstructor
public class CommentNotificationListener {

  @EventListener
  public void on(CommentPublishedEvent event) {
    // 在发布方的事务里写提醒；抛异常就整体回滚
  }
}
```

### 提交后可丢：`AFTER_COMMIT` + `REQUIRES_NEW`

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
@Transactional(propagation = REQUIRES_NEW, rollbackFor = Exception.class)
public void onSomethingChanged(SomethingChangedEvent event) {
  try {
    // 幂等性检查后再写
  } catch (OptimisticLockingFailureException e) {
    log.warn("并发更新冲突，其他线程已处理 id={}", event.id());
  }
}
```

### 错误处理策略（提交后监听器）

| 错误类型 | 处理方式 |
|---------|---------|
| `OptimisticLockingFailureException` | 记录警告，跳过（其他线程已处理） |
| `IllegalStateException` | 记录错误，依赖对账修复 |
| 未知异常 | 记录错误，**不抛出**（让其他处理器继续） |

同步监听器相反：要的就是抛出异常让整个事务回滚，不要吞异常。

### 事件链设计原则

```
线性事件链（正确）：
A → B → C

循环事件链（禁止）：
A → B → A（无限循环）
```

## 项目中的事件清单

| 事件 | 服务 / 子域 | 触发时机 | 订阅方 |
|------|------------|---------|--------|
| **PmcFullTextFetchedEvent** | catalog / fulltext | 写入 PMC 全文记录 | 文献子域 `PmcOpenAccessListener`：标为开放获取 |
| **CommentPublishedEvent** | interaction / discussion | 评论发表 | 提醒子域 `CommentNotificationListener` |
| **CommentEditedEvent** | interaction / discussion | 评论编辑 | 提醒子域 `CommentNotificationListener` |
| **CommentDeletedEvent** | interaction / discussion | 评论删除 | 提醒子域 `CommentNotificationListener` |
| **PostDeletedEvent** | interaction / post | 投稿经验删除 | 提醒子域 `PostNotificationListener` |

## 测试要点

- 验证事件发布（使用 `@RecordApplicationEvents`）
- 验证处理器逻辑（直接调用监听器方法）
- 同步监听器：验证它抛异常时发布方的写入一起回滚
- 提交后监听器：验证幂等性（重复发送同一事件）与并发场景（乐观锁冲突）
