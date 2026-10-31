import { describe, expect, it } from "vitest";
import type { Progress } from "@/lib/progress/progress";
import { continueReading, masteryCounts, nextUnread, rankReview, reviewReason } from "./today";

const DAY = 24 * 60 * 60 * 1000;
const NOW = 100 * DAY;
const ago = (days: number) => NOW - days * DAY;

const pool = ["a", "b", "c", "d", "e", "f"].map((slug) => ({ slug }));

describe("rankReview：今天读这一条的候选与顺序", () => {
  it("没有任何记录时没有候选（新读者看到从这里开始）", () => {
    expect(rankReview(pool, {}, NOW)).toEqual([]);
  });

  it("只挑有记录的条目；候选池外的记录不算", () => {
    const progress: Progress = { a: { readAt: ago(3) }, zzz: { level: 1, markedAt: ago(9) } };
    expect(rankReview(pool, progress, NOW).map((c) => c.slug)).toEqual(["a"]);
  });

  it("先挑标过刚接触 / 理解中的，再挑只打开过的，同档最久没看的在前", () => {
    const progress: Progress = {
      a: { readAt: ago(30) },
      b: { level: 2, markedAt: ago(5), readAt: ago(5) },
      c: { level: 1, markedAt: ago(17), readAt: ago(17) },
      d: { readAt: ago(10) },
    };
    expect(rankReview(pool, progress, NOW).map((c) => c.slug)).toEqual(["c", "b", "a", "d"]);
  });

  it("最近一次看到取阅读与标记里晚的那个", () => {
    const progress: Progress = {
      a: { level: 2, markedAt: ago(2), readAt: ago(20) },
      b: { level: 2, markedAt: ago(10) },
    };
    expect(rankReview(pool, progress, NOW).map((c) => c.slug)).toEqual(["b", "a"]);
  });

  it("吃透了的 30 天内不挑，今天刚看过的排到最后", () => {
    const progress: Progress = {
      a: { level: 3, markedAt: ago(10) },
      b: { level: 3, markedAt: ago(40) },
      c: { level: 1, markedAt: NOW - 1000 },
      d: { readAt: ago(2) },
    };
    expect(rankReview(pool, progress, NOW).map((c) => c.slug)).toEqual(["b", "d", "a", "c"]);
  });
});

describe("reviewReason：卡上写为什么推这一条", () => {
  it("标过的写级别与几天没看", () => {
    expect(reviewReason({ level: 2, markedAt: ago(17) }, NOW)).toBe(
      "你标过「理解中」· 17 天没看了",
    );
  });

  it("没标的写上次看是几天前，不满一天写今天看过", () => {
    expect(reviewReason({ readAt: ago(5) }, NOW)).toBe("上次看是 5 天前");
    expect(reviewReason({ readAt: NOW - 60_000 }, NOW)).toBe("今天看过");
    expect(reviewReason({ level: 1, markedAt: NOW - 60_000 }, NOW)).toBe(
      "你标过「刚接触」· 今天看过",
    );
  });
});

describe("masteryCounts / nextUnread / continueReading", () => {
  const progress: Progress = {
    a: { level: 3, markedAt: ago(1) },
    b: { level: 2, markedAt: ago(9) },
    c: { readAt: ago(2) },
    x: { level: 1, markedAt: ago(1) },
  };

  it("按掌握度计数，只算给定的条目；读过 = 标过掌握度", () => {
    expect(masteryCounts(["a", "b", "c", "d"], progress)).toEqual({ 1: 0, 2: 1, 3: 1, read: 2 });
  });

  it("接着读：路线里第一篇没标过的；一篇都没标时为 undefined", () => {
    expect(nextUnread(["a", "b", "c", "d"], progress)).toBe("c");
    expect(nextUnread(["c", "d"], progress)).toBeUndefined();
    expect(nextUnread(["a", "b"], progress)).toBeUndefined();
  });

  it("首页接着读：最近动过的那条路线里的下一篇", () => {
    const routes = [
      {
        slug: "r1",
        title: "一",
        entries: [
          { slug: "b", title: "B" },
          { slug: "e", title: "E" },
        ],
      },
      {
        slug: "r2",
        title: "二",
        entries: [
          { slug: "a", title: "A" },
          { slug: "f", title: "F" },
        ],
      },
    ];
    expect(continueReading(routes, progress)).toEqual({
      route: "二",
      routeSlug: "r2",
      position: 2,
      slug: "f",
      title: "F",
    });
    expect(continueReading(routes, {})).toBeUndefined();
  });
});
