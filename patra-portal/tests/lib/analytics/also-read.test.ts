import { describe, expect, it } from "vitest";
import { groupAlsoRead } from "@/lib/analytics/also-read";
import type { AlsoReadItem } from "@/lib/analytics/insight-schema";

const co = (id: string): AlsoReadItem => ({
  articleId: id,
  source: "CO_READ",
  meshTerm: null,
  venueName: null,
});
const mesh = (id: string): AlsoReadItem => ({
  articleId: id,
  source: "SAME_MESH",
  meshTerm: "Shock, Septic",
  venueName: null,
});
const venue = (id: string): AlsoReadItem => ({
  articleId: id,
  source: "SAME_VENUE",
  meshTerm: null,
  venueName: "Chest",
});
const shape = (groups: ReturnType<typeof groupAlsoRead>) =>
  groups.map((g) => [g.kind, g.items.map((i) => i.articleId)]);

describe("groupAlsoRead（简报 v0.18 §3.6 组合规则）", () => {
  it("全是一起读的：一组", () => {
    expect(shape(groupAlsoRead([co("1"), co("2")], "9"))).toEqual([["co-read", ["1", "2"]]]);
  });

  it("混合：先一起读的，再同主题（主题词与期刊合成一组），顺序不变", () => {
    expect(shape(groupAlsoRead([co("1"), mesh("2"), venue("3")], "9"))).toEqual([
      ["co-read", ["1"]],
      ["topic", ["2", "3"]],
    ]);
  });

  it("没有一起读的：只有同主题一组", () => {
    expect(shape(groupAlsoRead([mesh("2"), venue("3")], "9"))).toEqual([["topic", ["2", "3"]]]);
  });

  it("空：没有组", () => {
    expect(groupAlsoRead([], "9")).toEqual([]);
  });

  it("去掉当前这篇与重复的 ID", () => {
    expect(shape(groupAlsoRead([co("9"), co("1"), mesh("1"), mesh("2")], "9"))).toEqual([
      ["co-read", ["1"]],
      ["topic", ["2"]],
    ]);
  });
});
