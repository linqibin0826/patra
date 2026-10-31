import { describe, expect, it } from "vitest";
import { checkCatalog, checkEntryTags } from "./catalog-rules";
import { catalog, entry, route, topic } from "./testing/factory";

function rules(issues: { rule: string }[]) {
  return issues.map((i) => i.rule);
}

describe("标签与主题", () => {
  it("用了标签表里没有的标签报 tag-unknown", () => {
    expect(rules(checkEntryTags(entry("a", { tags: ["nope"] }), catalog().tags))).toEqual([
      "tag-unknown",
    ]);
  });

  it("挂了不存在的主题报 topic-unknown", () => {
    const c = catalog({
      entries: [entry("a", { topics: ["nope"], sources: [{ version: "v0.16" }] })],
    });
    expect(rules(checkCatalog(c))).toEqual(["topic-unknown"]);
  });

  it("挂了主题却没有带版本的出处报 topic-needs-version", () => {
    const c = catalog({ entries: [entry("a", { topics: ["t"] })], topics: [topic("t")] });
    expect(rules(checkCatalog(c))).toEqual(["topic-needs-version"]);
  });
});

describe("前置", () => {
  it("前置不存在报 prerequisite-missing", () => {
    const c = catalog({ entries: [entry("a", { prerequisites: ["nope"] })] });
    expect(rules(checkCatalog(c))).toEqual(["prerequisite-missing"]);
  });

  it("前置指向自己报 prerequisite-missing", () => {
    const c = catalog({ entries: [entry("a", { prerequisites: ["a"] })] });
    expect(rules(checkCatalog(c))).toEqual(["prerequisite-missing"]);
  });

  it("a → b → c → a 成环只报一条，说明里写出环路", () => {
    const c = catalog({
      entries: [
        entry("b", { prerequisites: ["c"] }),
        entry("a", { prerequisites: ["b"] }),
        entry("c", { prerequisites: ["a"] }),
      ],
    });
    const issues = checkCatalog(c);
    expect(rules(issues)).toEqual(["prerequisite-cycle"]);
    expect(issues[0]?.message).toContain("a → b → c → a");
  });

  it("两个不相交的环各报一条", () => {
    const c = catalog({
      entries: [
        entry("a", { prerequisites: ["b"] }),
        entry("b", { prerequisites: ["a"] }),
        entry("x", { prerequisites: ["y"] }),
        entry("y", { prerequisites: ["x"] }),
      ],
    });
    expect(rules(checkCatalog(c))).toEqual(["prerequisite-cycle", "prerequisite-cycle"]);
  });

  it("菱形依赖不算环", () => {
    const c = catalog({
      entries: [
        entry("a"),
        entry("b", { prerequisites: ["a"] }),
        entry("c", { prerequisites: ["a"] }),
        entry("d", { prerequisites: ["b", "c"] }),
      ],
    });
    expect(checkCatalog(c)).toEqual([]);
  });
});

describe("被取代", () => {
  it.each([
    ["不存在", "nope"],
    ["指向自己", "a"],
  ])("supersededBy %s 时报 superseded-missing", (_name, target) => {
    const c = catalog({ entries: [entry("a", { supersededBy: target })] });
    expect(rules(checkCatalog(c))).toEqual(["superseded-missing"]);
  });
});

describe("路线", () => {
  const intro = entry("intro-one", { difficulty: "intro" });
  const basic = entry("basic-one", { difficulty: "basic" });
  const basic2 = entry("basic-two", { difficulty: "basic" });

  it("路线里的条目不存在报 route-entry-missing", () => {
    const c = catalog({ entries: [intro], routes: [route("r", ["intro-one", "nope"])] });
    expect(rules(checkCatalog(c))).toEqual(["route-entry-missing"]);
  });

  it("同一路线重复出现报 route-entry-duplicate", () => {
    const c = catalog({ entries: [intro], routes: [route("r", ["intro-one", "intro-one"])] });
    expect(rules(checkCatalog(c))).toEqual(["route-entry-duplicate"]);
  });

  it("两条路线 order 相同报 route-order-duplicate", () => {
    const c = catalog({
      entries: [intro],
      routes: [route("r1", ["intro-one"], 1), route("r2", ["intro-one"], 1)],
    });
    expect(rules(checkCatalog(c))).toEqual(["route-order-duplicate"]);
  });

  it("难度倒排报 route-difficulty-descending", () => {
    const c = catalog({
      entries: [intro, basic],
      routes: [route("r", ["basic-one", "intro-one"])],
    });
    expect(rules(checkCatalog(c))).toEqual(["route-difficulty-descending"]);
  });

  it("同档难度相邻不算倒排", () => {
    const c = catalog({
      entries: [intro, basic, basic2],
      routes: [route("r", ["intro-one", "basic-one", "basic-two"])],
    });
    expect(checkCatalog(c)).toEqual([]);
  });

  it("本路线里的前置排在后面报 route-prerequisite-after", () => {
    const later = entry("basic-two", { difficulty: "basic", prerequisites: ["basic-one"] });
    const c = catalog({
      entries: [basic, later],
      routes: [route("r", ["basic-two", "basic-one"])],
    });
    expect(rules(checkCatalog(c))).toEqual(["route-prerequisite-after"]);
  });

  it("前置不在本路线里不要求顺序", () => {
    const withOutside = entry("basic-two", { difficulty: "basic", prerequisites: ["basic-one"] });
    const c = catalog({ entries: [basic, withOutside], routes: [route("r", ["basic-two"])] });
    expect(checkCatalog(c)).toEqual([]);
  });

  it("个人环境条目进路线报 route-personal-entry", () => {
    const c = catalog({
      entries: [entry("p", { personalEnv: true })],
      routes: [route("r", ["p"])],
    });
    expect(rules(checkCatalog(c))).toEqual(["route-personal-entry"]);
  });

  it("已被取代的条目进路线报 route-superseded-entry", () => {
    const c = catalog({
      entries: [entry("old", { supersededBy: "new" }), entry("new")],
      routes: [route("r", ["old"])],
    });
    expect(rules(checkCatalog(c))).toEqual(["route-superseded-entry"]);
  });
});

describe("合法内容", () => {
  it("没有任何问题", () => {
    const c = catalog({
      entries: [
        entry("http", { difficulty: "intro" }),
        entry("rest", {
          difficulty: "basic",
          prerequisites: ["http"],
          topics: ["api"],
          sources: [{ version: "v0.12" }],
        }),
      ],
      routes: [route("web", ["http", "rest"])],
      topics: [topic("api")],
    });
    expect(checkCatalog(c)).toEqual([]);
  });
});
