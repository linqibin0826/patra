package dev.patra.interaction.app.collection;

import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import java.time.Instant;

/// app 层 collection 测试的公共数据。
public final class Fixtures {

  /// 只有静态方法。
  private Fixtures() {}

  /// 一个收藏夹。
  ///
  /// @param id ID
  /// @param ownerId 所有者
  /// @param visibility 可见性
  /// @param itemCount 篇数
  /// @return 收藏夹
  public static CollectionSummary collection(
      long id, long ownerId, CollectionVisibility visibility, int itemCount) {
    Instant at = Instant.parse("2026-10-24T00:00:00Z");
    return CollectionSummary.builder()
        .collectionId(CollectionId.of(id))
        .ownerId(ownerId)
        .name("组会")
        .description("每周四")
        .visibility(visibility)
        .itemCount(itemCount)
        .createdAt(at)
        .updatedAt(at)
        .build();
  }

  /// 一张用户卡片。
  ///
  /// @param userId 用户
  /// @param state 状态
  /// @param profileVisible 主页是否对访客可见
  /// @return 卡片
  public static UserCard card(long userId, AccountState state, boolean profileVisible) {
    return UserCard.builder()
        .userId(userId)
        .username("u" + userId)
        .displayName("用户" + userId)
        .state(state)
        .emailVerified(true)
        .profileVisible(profileVisible)
        .build();
  }

  /// 一个登记对象的视图。
  ///
  /// @param id 本地 ID
  /// @param state 状态
  /// @param key 对外 key，可空
  /// @param journal 期刊
  /// @param publishedDate 出版日期
  /// @return 视图
  public static SubjectView view(
      long id, SubjectState state, String key, String journal, String publishedDate) {
    return SubjectView.builder()
        .subjectId(SubjectId.of(id))
        .type(SubjectType.PUBLICATION)
        .key(key)
        .state(state)
        .snapshot(SubjectSnapshot.of("Title " + id, journal, publishedDate, null))
        .build();
  }
}
