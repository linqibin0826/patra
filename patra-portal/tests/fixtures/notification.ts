import { makeCard } from "@tests/fixtures/discussion";
import type { Notification } from "@/lib/notification/inbox-schema";

/// 一条提醒：默认陈宇回复了你在一篇文献下的评论，未读
export function makeNotification(overrides: Partial<Notification> = {}): Notification {
  return {
    notificationId: "100",
    type: "COMMENT_REPLY",
    read: false,
    occurredAt: "2026-10-27T08:00:00Z",
    actor: makeCard(),
    actorAnonymous: false,
    actorCount: 1,
    subject: {
      type: "publication",
      key: "11",
      available: true,
      title: "Hydrocortisone, Vitamin C, and Thiamine for Sepsis",
      journal: null,
      abbreviation: null,
      submittedMonth: null,
      mine: false,
      experience: null,
    },
    commentId: "9",
    excerpt: "同意。补充一点：文中的倾向评分只用来校正主要结局",
    excerptTruncated: false,
    ...overrides,
  };
}
