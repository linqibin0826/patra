import { describe, expect, it } from "vitest";
import { catalog, entry, route, topic } from "@/lib/content/testing/factory";
import type { Entry } from "@/lib/content/types";
import {
  entryPage,
  homeView,
  PERSONAL_TAG,
  routePage,
  routeWindow,
  tagList,
  tagPage,
  topicList,
  topicPage,
} from "./views";

const DIAGNOSE =
  '<Diagnose question="题干？" options={["甲", "乙"]} answer="A" ifRight="对" ifWrong="答案是 A。因为甲。" />';

function withBody(e: Entry, body = DIAGNOSE): Entry {
  return { ...e, body };
}

const content = catalog({
  entries: [
    withBody(entry("why-async", { title: "为什么要异步", difficulty: "intro" })),
    withBody(
      entry("queue", {
        title: "消息队列",
        prerequisites: ["why-async"],
        topics: ["delivery"],
        sources: [{ version: "v0.16", issue: "PAP-140" }],
      }),
    ),
    withBody(
      entry("outbox", {
        title: "发件箱",
        difficulty: "advanced",
        prerequisites: ["queue", "missing"],
        topics: ["delivery"],
        sources: [{ version: "v0.16", issue: "PAP-141", pr: 343 }],
      }),
      `${DIAGNOSE}\n\n<CodeRef at="a/B.java@5b1d855a5aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" />\n\n<CodeRef at="a/C.java@pending" />`,
    ),
    withBody(
      entry("old-relay", {
        title: "旧的转发",
        type: "decision",
        topics: ["delivery"],
        supersededBy: "outbox",
        sources: [{ version: "v0.9" }],
      }),
    ),
    withBody(
      entry("proxy", {
        title: "本机代理",
        personalEnv: true,
        tags: ["network"],
        topics: ["local-net"],
        sources: [{ version: "v0.10" }],
      }),
    ),
    withBody(
      entry("dlq", { title: "死信", difficulty: "practice", sources: [{ version: "v0.17" }] }),
    ),
  ],
  routes: [
    route("messaging", ["why-async", "queue", "ghost", "outbox", "dlq"], 2),
    route("ddd", ["queue"], 1),
    route("testing", ["dlq"], 3),
  ],
  topics: [topic("delivery"), topic("local-net")],
});

describe("routeWindow：侧栏只显示当前篇前后两篇", () => {
  it("中间的篇目前后各两篇", () => {
    expect(routeWindow(17, 8)).toEqual({ start: 6, end: 10 });
  });

  it("靠近两端时不越界", () => {
    expect(routeWindow(17, 1)).toEqual({ start: 1, end: 3 });
    expect(routeWindow(17, 17)).toEqual({ start: 15, end: 17 });
    expect(routeWindow(2, 1)).toEqual({ start: 1, end: 2 });
  });
});

describe("routePage", () => {
  const view = routePage(content, "messaging");

  it("按难度分段，只留有篇目的段，序号连续、跳过还没写的", () => {
    expect(view?.bands.map((b) => [b.difficulty, b.rows.map((r) => [r.n, r.card.slug])])).toEqual([
      ["intro", [[1, "why-async"]]],
      ["basic", [[2, "queue"]]],
      ["advanced", [[3, "outbox"]]],
      ["practice", [[4, "dlq"]]],
    ]);
  });

  it("统计篇数、各难度篇数，注明也在别的路线里", () => {
    expect(view?.count).toBe(4);
    expect(view?.levels).toEqual({ intro: 1, basic: 1, advanced: 1, practice: 1 });
    expect(view?.bands[1]?.rows[0]?.alsoIn).toEqual(["ddd"]);
    expect(view?.index).toBe(2);
  });

  it("接着走：order 排在后面的两条，到末尾从头接", () => {
    expect(view?.next.map((r) => r.slug)).toEqual(["testing", "ddd"]);
  });

  it("不存在的路线返回 undefined", () => {
    expect(routePage(content, "nope")).toBeUndefined();
  });
});

describe("topicList / topicPage", () => {
  it("主题列表不含全是个人环境条目的主题，按最新出处版本降序", () => {
    expect(topicList(content).map((t) => [t.slug, t.count, t.from, t.to])).toEqual([
      ["delivery", 3, "v0.9", "v0.16"],
    ]);
  });

  it("时间轴按最早出处版本分组，被取代的标出谁取代了它", () => {
    const view = topicPage(content, "delivery");
    expect(view?.groups.map((g) => [g.version, g.issues, g.items.map((i) => i.card.slug)])).toEqual(
      [
        ["v0.9", [], ["old-relay"]],
        ["v0.16", ["PAP-140", "PAP-141"], ["outbox", "queue"]],
      ],
    );
    const [old] = view?.groups[0]?.items ?? [];
    expect(old?.supersededBy).toBe("发件箱");
    expect(view?.groups[1]?.items[0]?.replaces).toEqual(["旧的转发"]);
    expect(view?.supersededCount).toBe(1);
  });

  it("个人环境主题页照常生成，标 personalOnly", () => {
    expect(topicPage(content, "local-net")?.personalOnly).toBe(true);
  });
});

describe("tagList / tagPage", () => {
  it("普通标签计数不算个人环境条目，另给个人环境篇数", () => {
    const { tags, personalCount } = tagList(content);
    expect(tags).toEqual([
      { slug: "backend", label: "后端", count: 5 },
      { slug: "network", label: "网络", count: 0 },
    ]);
    expect(personalCount).toBe(1);
  });

  it("标签页按难度从低到高，不列个人环境条目", () => {
    expect(tagPage(content, "backend")?.cards.map((c) => c.slug)).toEqual([
      "why-async",
      "old-relay",
      "queue",
      "outbox",
      "dlq",
    ]);
    expect(tagPage(content, "network")?.cards).toEqual([]);
  });

  it("个人环境标签页列全部个人环境条目", () => {
    const view = tagPage(content, PERSONAL_TAG);
    expect(view?.personal).toBe(true);
    expect(view?.cards.map((c) => c.slug)).toEqual(["proxy"]);
    expect(tagPage(content, "nope")).toBeUndefined();
  });
});

describe("entryPage", () => {
  const view = entryPage(content, "outbox");

  it("位置取 order 最小的路线，上一篇 / 下一篇跳过还没写的", () => {
    expect(view?.mainRoute).toMatchObject({ slug: "messaging", position: 3, total: 4 });
    expect(view?.prev?.slug).toBe("queue");
    expect(view?.next?.slug).toBe("dlq");
  });

  it("直接前置带一行说明，跳过不存在的；前置的前置折成一行", () => {
    expect(view?.prerequisites).toEqual([
      {
        slug: "queue",
        title: "消息队列",
        difficulty: "basic",
        meta: "基础 · 1 分钟 · messaging 第 2 篇",
      },
    ]);
    expect(view?.indirect).toEqual([{ slug: "why-async", title: "为什么要异步" }]);
  });

  it("同一主题、出处、钉住的提交、被谁取代", () => {
    expect(view?.topics[0]?.entries.map((e) => e.slug)).toEqual(["old-relay", "outbox", "queue"]);
    expect(view?.commits).toEqual(["5b1d855"]);
    expect(view?.sources).toEqual([{ version: "v0.16", issue: "PAP-141", pr: 343 }]);
    expect(entryPage(content, "old-relay")?.supersededBy).toEqual({
      slug: "outbox",
      title: "发件箱",
      version: "v0.16",
    });
  });

  it("读完可以接着读：把本篇列为前置的条目", () => {
    expect(entryPage(content, "queue")?.followUps).toEqual([{ slug: "outbox", title: "发件箱" }]);
  });

  it("不在路线里的个人环境条目，位置写个人环境标签", () => {
    const proxy = entryPage(content, "proxy");
    expect(proxy?.mainRoute).toBeUndefined();
    expect(proxy?.crumb).toEqual({ label: "标签 · 个人环境", href: `/tags/${PERSONAL_TAG}` });
    expect(entryPage(content, "old-relay")?.crumb).toEqual({
      label: "主题 · delivery",
      href: "/topics/delivery",
    });
  });
});

describe("homeView", () => {
  const view = homeView(content);

  it("从这里开始：第 1 条路线的前三篇", () => {
    expect(view.starter?.route.slug).toBe("ddd");
    expect(view.starter?.entries.map((e) => e.slug)).toEqual(["queue"]);
  });

  it("路线卡按 order，带难度跨度与篇目", () => {
    expect(view.routes.map((r) => [r.slug, r.count, r.from, r.to])).toEqual([
      ["ddd", 1, "basic", "basic"],
      ["messaging", 4, "intro", "practice"],
      ["testing", 1, "practice", "practice"],
    ]);
  });

  it("最近写的按最新出处版本降序、不含个人环境；总篇数不算个人环境", () => {
    expect(view.recent.map((c) => c.slug)).toEqual(["dlq", "outbox", "queue"]);
    expect(view.total).toBe(5);
  });

  it("回顾候选池：不含个人环境与被取代的，带回顾题与位置", () => {
    expect(view.reviewPool.map((c) => c.slug)).toEqual(["why-async", "queue", "outbox", "dlq"]);
    expect(view.reviewPool[1]).toMatchObject({
      where: "ddd · 第 1 篇",
      review: { question: "题干？", answer: "甲", explain: "因为甲。" },
    });
  });
});
