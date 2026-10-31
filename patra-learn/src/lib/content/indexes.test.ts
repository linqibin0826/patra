import { describe, expect, it } from "vitest";
import {
  buildRouteIndex,
  buildTagIndex,
  buildTopicIndex,
  buildVersionIndex,
  compareVersions,
  findEntry,
} from "./indexes";
import { catalog, entry, route, topic } from "./testing/factory";

function slugs(list: { slug: string }[]) {
  return list.map((e) => e.slug);
}

describe("compareVersions", () => {
  it("按数字比较，不按字符串", () => {
    expect(compareVersions("v0.9", "v0.16")).toBeLessThan(0);
    expect(compareVersions("v1.0", "v0.16")).toBeGreaterThan(0);
    expect(compareVersions("v0.16", "v0.16")).toBe(0);
  });
});

describe("路线索引", () => {
  it("按 order 排序，条目按路线顺序解析", () => {
    const c = catalog({
      entries: [entry("a"), entry("b")],
      routes: [route("second", ["b"], 2), route("first", ["b", "a"], 1)],
    });
    const index = buildRouteIndex(c);
    expect(slugs(index)).toEqual(["first", "second"]);
    expect(slugs(index[0]?.entries ?? [])).toEqual(["b", "a"]);
  });

  it("跳过不存在的条目", () => {
    const c = catalog({ entries: [entry("a")], routes: [route("r", ["a", "nope"])] });
    expect(slugs(buildRouteIndex(c)[0]?.entries ?? [])).toEqual(["a"]);
  });
});

describe("主题索引", () => {
  const c = catalog({
    topics: [topic("delivery")],
    entries: [
      entry("later", { topics: ["delivery"], sources: [{ version: "v0.16" }] }),
      entry("earlier", {
        topics: ["delivery"],
        sources: [{ version: "v0.16" }, { version: "v0.9" }],
      }),
      entry("same-b", { topics: ["delivery"], sources: [{ version: "v0.12" }] }),
      entry("same-a", { topics: ["delivery"], sources: [{ version: "v0.12" }] }),
      entry("mine", { topics: ["delivery"], sources: [{ version: "v0.10" }], personalEnv: true }),
      entry("old", {
        topics: ["delivery"],
        sources: [{ version: "v0.11" }],
        supersededBy: "later",
      }),
    ],
  });

  it("按最早出处版本升序，同版本按 slug，个人环境与已被取代的条目都保留", () => {
    expect(slugs(buildTopicIndex(c)[0]?.entries ?? [])).toEqual([
      "earlier",
      "mine",
      "old",
      "same-a",
      "same-b",
      "later",
    ]);
    expect(buildTopicIndex(c)[0]?.personalOnly).toBe(false);
  });

  it("主题下全是个人环境条目时标 personalOnly，页面据此不进主题列表与首页", () => {
    const only = catalog({
      topics: [topic("proxy"), topic("empty")],
      entries: [
        entry("p1", { topics: ["proxy"], sources: [{ version: "v0.12" }], personalEnv: true }),
        entry("p2", { topics: ["proxy"], sources: [{ version: "v0.13" }], personalEnv: true }),
      ],
    });
    const index = buildTopicIndex(only);
    expect(index.map((t) => [t.slug, t.personalOnly])).toEqual([
      ["empty", false],
      ["proxy", true],
    ]);
  });
});

describe("标签索引", () => {
  it("按标签表顺序，带计数，含个人环境条目与零计数标签", () => {
    const c = catalog({
      entries: [entry("a"), entry("p", { personalEnv: true }), entry("n", { tags: ["network"] })],
    });
    const index = buildTagIndex(c);
    expect(index.map((t) => [t.slug, t.count])).toEqual([
      ["backend", 2],
      ["network", 1],
    ]);
    expect(slugs(index[0]?.entries ?? [])).toEqual(["a", "p"]);

    const empty = buildTagIndex(catalog());
    expect(empty.map((t) => t.count)).toEqual([0, 0]);
  });
});

describe("版本索引", () => {
  it("版本降序；一篇多版本出现在多处、同版本只出现一次；排除个人环境", () => {
    const c = catalog({
      entries: [
        entry("a", {
          sources: [{ version: "v0.9" }, { version: "v0.16" }, { version: "v0.16", pr: 1 }],
        }),
        entry("b", { sources: [{ version: "v0.16" }] }),
        entry("p", { sources: [{ version: "v0.12" }], personalEnv: true }),
        entry("none"),
      ],
    });
    const index = buildVersionIndex(c);
    expect(index.map((v) => v.version)).toEqual(["v0.16", "v0.9"]);
    expect(slugs(index[0]?.entries ?? [])).toEqual(["a", "b"]);
    expect(slugs(index[1]?.entries ?? [])).toEqual(["a"]);
  });
});

describe("findEntry", () => {
  it("按 slug 找条目", () => {
    const c = catalog({ entries: [entry("a")] });
    expect(findEntry(c, "a")?.slug).toBe("a");
    expect(findEntry(c, "nope")).toBeUndefined();
  });
});
