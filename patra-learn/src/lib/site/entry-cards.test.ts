import { describe, expect, it } from "vitest";
import { catalog, entry, route, topic } from "@/lib/content/testing/factory";
import { buildEntryCards, readingMinutes, reviewOf } from "./entry-cards";

const DIAGNOSE = `<Diagnose
  question="事务提交前崩了会怎样？"
  options={["都生效", "都不生效", "生效一半"]}
  answer="B"
  ifRight="对。提交前的修改都不算数。"
  ifWrong="答案是 B。没提交的修改，数据库重启后一律撤销。"
/>`;

describe("readingMinutes：按汉字与西文词估算读完要几分钟", () => {
  it("400 个汉字约 1 分钟，向上取整", () => {
    expect(readingMinutes("字".repeat(400))).toBe(1);
    expect(readingMinutes("字".repeat(401))).toBe(2);
  });

  it("西文按词算，200 词约 1 分钟；至少 1 分钟", () => {
    expect(readingMinutes(Array(400).fill("word").join(" "))).toBe(2);
    expect(readingMinutes("")).toBe(1);
  });
});

describe("reviewOf：首页回顾题复用开头诊断题", () => {
  it("题干、正确选项、去掉「答案是 X。」的讲解", () => {
    expect(reviewOf(`${DIAGNOSE}\n\n正文`)).toEqual({
      question: "事务提交前崩了会怎样？",
      answer: "都不生效",
      explain: "没提交的修改，数据库重启后一律撤销。",
    });
  });

  it("讲解不以「答案是」开头时原样保留，冒号写法也能去掉", () => {
    const colon = DIAGNOSE.replace("答案是 B。", "答案是 B：");
    expect(reviewOf(colon)?.explain).toBe("没提交的修改，数据库重启后一律撤销。");
    const plain = DIAGNOSE.replace("答案是 B。", "");
    expect(reviewOf(plain)?.explain).toBe("没提交的修改，数据库重启后一律撤销。");
  });

  it("没有诊断题或参数不全时没有回顾题", () => {
    expect(reviewOf("正文")).toBeUndefined();
    expect(reviewOf('<Diagnose question="只有题干" />')).toBeUndefined();
  });
});

describe("buildEntryCards：每篇条目的列表卡片数据", () => {
  const content = catalog({
    entries: [
      entry("http", { title: "HTTP", difficulty: "intro", sources: [{ version: "v0.9" }] }),
      entry("cookie", {
        title: "Cookie",
        tags: ["backend", "network"],
        topics: ["session"],
        sources: [{ version: "v0.8" }, { version: "v0.12" }],
      }),
      entry("old", { type: "decision", supersededBy: "cookie" }),
      entry("proxy", { personalEnv: true, tags: ["network"] }),
    ],
    routes: [route("web", ["http", "missing", "cookie"], 1), route("auth", ["cookie"], 2)],
    topics: [topic("session")],
  });
  const cards = new Map(buildEntryCards(content).map((c) => [c.slug, c]));

  it("带上所在路线与位置，路线里还没写的条目不占位置", () => {
    expect(cards.get("cookie")?.routes).toEqual([
      { slug: "web", title: "web", position: 2, total: 2 },
      { slug: "auth", title: "auth", position: 1, total: 1 },
    ]);
    expect(cards.get("old")?.routes).toEqual([]);
  });

  it("标签写中文名，主题带标题，最新出处版本按数字比较", () => {
    const cookie = cards.get("cookie");
    expect(cookie?.tagLabels).toEqual(["后端", "网络"]);
    expect(cookie?.topics).toEqual([{ slug: "session", title: "session" }]);
    expect(cookie?.latestVersion).toBe("v0.12");
    expect(cards.get("old")?.latestVersion).toBeUndefined();
  });

  it("标出被取代与个人环境", () => {
    expect(cards.get("old")?.superseded).toBe(true);
    expect(cards.get("http")?.superseded).toBe(false);
    expect(cards.get("proxy")?.personalEnv).toBe(true);
  });
});
