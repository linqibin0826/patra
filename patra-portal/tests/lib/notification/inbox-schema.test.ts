import { makeNotification } from "@tests/fixtures/notification";
import { describe, expect, it } from "vitest";
import type { NotificationSubject } from "@/lib/notification/inbox-schema";
import { notificationSchema, unreadCountsSchema } from "@/lib/notification/inbox-schema";

describe("notificationSchema", () => {
  it("回复行：人数恒为 1、评论 ID 必有，对象没有经验字段", () => {
    expect(notificationSchema.safeParse(makeNotification()).success).toBe(true);
  });

  it("有帮助点经验：评论 ID 为空、经验总用时为空也通过", () => {
    const row = makeNotification({
      type: "POST_HELPFUL",
      actorCount: 3,
      commentId: null,
      subject: {
        ...(makeNotification().subject as NotificationSubject),
        type: "post",
        mine: true,
        experience: {
          firstDecision: "DESK_REJECT",
          finalOutcome: "REJECTED",
          firstRoundDays: 3,
          totalDays: null,
        },
      },
    });
    expect(notificationSchema.safeParse(row).success).toBe(true);
  });

  it("人数缺失或为 0、时间仍叫 createdAt：不通过", () => {
    const { actorCount: _, ...noCount } = makeNotification();
    expect(notificationSchema.safeParse(noCount).success).toBe(false);
    expect(notificationSchema.safeParse(makeNotification({ actorCount: 0 })).success).toBe(false);
    const { occurredAt, ...rest } = makeNotification();
    expect(notificationSchema.safeParse({ ...rest, createdAt: occurredAt }).success).toBe(false);
  });

  it("经验字段里有不认识的枚举值：这一行照常解析，经验字段当作没有（不拖垮整页）", () => {
    const row = makeNotification({
      subject: {
        ...(makeNotification().subject as NotificationSubject),
        type: "post",
        experience: {
          firstDecision: "PRE_SUBMISSION_INQUIRY",
          finalOutcome: "ACCEPTED",
          firstRoundDays: 3,
          totalDays: null,
        } as unknown as NotificationSubject["experience"],
      },
    });
    const parsed = notificationSchema.safeParse(row);
    expect(parsed.success).toBe(true);
    expect(parsed.data?.subject?.experience).toBeNull();
  });
});

describe("unreadCountsSchema", () => {
  it("四个数都要有", () => {
    expect(
      unreadCountsSchema.safeParse({ total: 1, replies: 1, mentions: 0, helpful: 2 }).success,
    ).toBe(true);
    expect(unreadCountsSchema.safeParse({ total: 1, replies: 1, mentions: 0 }).success).toBe(false);
  });
});
