package dev.patra.interaction.domain.post.port.repository;

import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.model.vo.PostRevision;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/// 帖子的写端。写方法不开事务，由 app 层的事务包住；改投稿日期的路径（写、改）先拿作者锁再查重。
public interface PostRepository {

  /// 发一个新的帖子 ID（Snowflake）。
  ///
  /// @return 帖子 ID
  PostId nextId();

  /// 拿作者的事务级 advisory lock（`post.author:{userId}`），随调用方的事务释放。
  ///
  /// @param userId 作者
  void lockAuthor(long userId);

  /// 按 ID 取帖子（含已删除的）。
  ///
  /// @param id 帖子 ID
  /// @return 帖子；不存在为空
  Optional<PostAggregate> find(PostId id);

  /// 同一作者在同一期刊下、同一投稿日期的未删除经验。
  ///
  /// @param authorId 作者
  /// @param journal 期刊的本地 ID
  /// @param submittedOn 投稿日期
  /// @return 已有经验的 ID
  Optional<PostId> findActiveBySubmission(long authorId, SubjectId journal, LocalDate submittedOn);

  /// 插入新帖子（基表与扩展表）。
  ///
  /// @param post 帖子
  void insert(PostAggregate post);

  /// 写回内容：正文、扩展表全部字段、`revised_at`。作者、期刊、匿名、状态、发布时刻不动。
  ///
  /// @param post 编辑后的帖子
  /// @param now 编辑时刻
  void update(PostAggregate post, Instant now);

  /// 记一版被替换的旧内容，版本号接在已有的后面。
  ///
  /// @param id 帖子 ID
  /// @param revision 旧版本
  void insertRevision(PostId id, PostRevision revision);

  /// 软删：状态改为 DELETED，记删除时刻；内容留在库里。
  ///
  /// @param id 帖子 ID
  /// @param now 删除时刻
  void markDeleted(PostId id, Instant now);
}
