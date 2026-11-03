import { describe, expect, it } from "vitest";
import type { HistoryEntry } from "@/lib/analytics/history-schema";
import { groupHistoryByDay, historyTime } from "@/lib/history/groups";

/// 「现在」是北京时间 2026-11-01（周日）16:00
const NOW = Date.parse("2026-11-01T08:00:00.000Z");

const entry = (articleId: string, lastReadAt: string): HistoryEntry => ({
  articleId,
  lastReadAt,
  fulltextRead: false,
  paper: null,
});

describe("groupHistoryByDay：按北京时间的自然日分组（简报 §3.7 第 3 条）", () => {
  it("今天、昨天、今年更早、往年各自的组标题", () => {
    const groups = groupHistoryByDay(
      [
        entry("1", "2026-11-01T07:42:10.177Z"),
        entry("2", "2026-10-31T02:00:00.000Z"),
        entry("3", "2026-10-30T02:00:00.000Z"),
        entry("4", "2025-12-30T02:00:00.000Z"),
      ],
      NOW,
    );
    expect(groups.map((g) => g.label)).toEqual([
      "今天 · 11 月 1 日",
      "昨天 · 10 月 31 日",
      "10 月 30 日 周五",
      "2025 年 12 月 30 日 周二",
    ]);
  });

  it("UTC 前一天 16:30 是北京时间当天 00:30：算今天", () => {
    const [group] = groupHistoryByDay([entry("1", "2026-10-31T16:30:00.000Z")], NOW);
    expect(group?.label).toBe("今天 · 11 月 1 日");
  });

  it("同一天的几条在同一组，顺序不变；组按出现顺序", () => {
    const groups = groupHistoryByDay(
      [
        entry("a", "2026-11-01T07:00:00.000Z"),
        entry("b", "2026-11-01T03:00:00.000Z"),
        entry("c", "2026-10-31T09:00:00.000Z"),
      ],
      NOW,
    );
    expect(groups.map((g) => g.items.map((i) => i.articleId))).toEqual([["a", "b"], ["c"]]);
    expect(groups.map((g) => g.key)).toEqual(["2026-11-01", "2026-10-31"]);
  });

  it("空列表没有组", () => {
    expect(groupHistoryByDay([], NOW)).toEqual([]);
  });
});

describe("historyTime", () => {
  it("北京时间的时:分", () => {
    expect(historyTime("2026-11-01T07:42:10.177Z")).toBe("15:42");
  });
});
