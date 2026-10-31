import { makeNotification } from "@tests/fixtures/notification";
import { describe, expect, it } from "vitest";
import { flattenPages, groupByDay } from "@/lib/notifications/groups";

/// 北京时间 2026-10-29 10:00
const NOW = Date.parse("2026-10-29T02:00:00Z");

const today1 = makeNotification({ notificationId: "1", occurredAt: "2026-10-29T01:00:00Z" });
const today2 = makeNotification({ notificationId: "2", occurredAt: "2026-10-28T17:00:00Z" });
const yesterday = makeNotification({ notificationId: "3", occurredAt: "2026-10-28T10:00:00Z" });
const earlier = makeNotification({ notificationId: "4", occurredAt: "2026-10-20T10:00:00Z" });

const ids = (groups: ReturnType<typeof groupByDay>) =>
  groups.map((g) => [g.label, g.items.map((n) => n.notificationId)]);

describe("groupByDay", () => {
  it("固定按今天、昨天、更早排，组内保持传入顺序", () => {
    expect(ids(groupByDay([yesterday, today1, earlier, today2], NOW))).toEqual([
      ["今天", ["1", "2"]],
      ["昨天", ["3"]],
      ["更早", ["4"]],
    ]);
  });

  it("空组不返回", () => {
    expect(ids(groupByDay([earlier], NOW))).toEqual([["更早", ["4"]]]);
  });

  it("没有提醒时是空数组", () => {
    expect(groupByDay([], NOW)).toEqual([]);
  });
});

describe("flattenPages", () => {
  it("按页拼起来；同一条在两页里出现时只留第一次（有帮助条目翻页期间会挪位置）", () => {
    const pages = [
      { items: [today1, today2], nextCursor: "c" },
      { items: [today2, yesterday], nextCursor: null },
    ];
    expect(flattenPages(pages).map((n) => n.notificationId)).toEqual(["1", "2", "3"]);
  });
});
