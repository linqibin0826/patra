package dev.patra.notification.domain.inbox.port.repository;

import java.time.Instant;

/// 评论快照与评论墓碑：每条评论一行，存最新版本的纯文本；评论删除后这一行变成墓碑，之后这条评论的事件全部丢弃。
///
/// 写快照与写墓碑都会锁住这一行，同一条评论的发表与删除因此互斥。
public interface CommentContentRepository {

  /// 写入一版正文：没有就新建；已存版本低于这一版就覆盖；已存的更新或相同就保留。
  ///
  /// @param commentId 评论
  /// @param subjectId 所在对象
  /// @param version 这一版的版本
  /// @param plainText 这一版的纯文本全文
  /// @param now 处理时刻
  /// @return 这条评论还在为 `true`；已是墓碑为 `false`（正文不动）
  boolean saveIfNewer(long commentId, long subjectId, int version, String plainText, Instant now);

  /// 把评论标成墓碑（没有就新建一行墓碑），正文置空。重复调用保留第一次的删除时刻。
  ///
  /// @param commentId 评论
  /// @param subjectId 所在对象
  /// @param deletedAt 删除时刻（事件里的）
  /// @param now 处理时刻
  void markDeleted(long commentId, long subjectId, Instant deletedAt, Instant now);

  /// 评论是否已是墓碑。
  ///
  /// @param commentId 评论
  /// @return 已删除为 `true`
  boolean isDeleted(long commentId);

  /// 删掉一个对象下全部评论的快照与墓碑（经验删除后由对象墓碑接管）。
  ///
  /// @param subjectId 对象
  /// @return 删除的行数
  int deleteBySubject(long subjectId);

  /// 删掉早于截止时刻写入、且没有提醒引用的快照与墓碑（保留期清理）。
  ///
  /// @param cutoff 截止时刻（不含）
  /// @return 删除的行数
  int purgeUnreferencedBefore(Instant cutoff);
}
