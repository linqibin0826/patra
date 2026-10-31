package dev.patra.notification.domain.inbox.model.enums;

/// 一次点击并进合并条目后，条目变了没有、要不要写库、要不要通知推送。
public enum HelpfulChange {

  /// 什么都没变（同一个人更晚再点）：不写库、不发事件。
  NONE,

  /// 只有作者看不到的状态变了（经验正文的时刻往后推了、正文没变）：写库，不发事件。
  STORED_ONLY,

  /// 作者看得到的变了（人数、最近一位、时间、已读、正文）：写库并发「未读数变了」，开着的提醒页据此刷新。
  VISIBLE
}
