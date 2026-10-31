import { describe, expect, it } from "vitest";
import { catalog, entry, route } from "@/lib/content/testing/factory";
import { availableHints, type SearchDoc, searchDocs, searchEntries } from "./search";

function doc(slug: string, fields: Partial<SearchDoc> = {}): SearchDoc {
  return {
    slug,
    title: slug,
    summary: "",
    type: "concept",
    difficulty: "basic",
    tags: [],
    routes: [],
    personalEnv: false,
    ...fields,
  };
}

const docs = [
  doc("ack", {
    title: "确认（ack）与重投",
    summary: "消费失败时消息去哪了",
    routes: ["消息与事件"],
  }),
  doc("outbox", {
    title: "发件箱",
    summary: "等 broker 确认后才把记录标完成",
    difficulty: "advanced",
    tags: ["消息"],
  }),
  doc("pitfall", { title: "发布确认要先看退回", type: "pitfall", difficulty: "advanced" }),
  doc("proxy", { title: "代理导致确认超时", personalEnv: true }),
  doc("nacos", { title: "Nacos 推回原文", summary: "md5 没变不通知" }),
];

describe("searchEntries", () => {
  it("空查询没有结果", () => {
    expect(searchEntries(docs, "  ").results).toEqual([]);
  });

  it("标题命中排在摘要命中前面，同为标题命中按难度", () => {
    const { results } = searchEntries(docs, "确认");
    expect(results.map((r) => r.doc.slug)).toEqual(["ack", "pitfall", "outbox"]);
  });

  it("不区分大小写；多个词都要出现，可以落在标签与路线名里", () => {
    expect(searchEntries(docs, "NACOS").results.map((r) => r.doc.slug)).toEqual(["nacos"]);
    expect(searchEntries(docs, "确认 消息").results.map((r) => r.doc.slug)).toEqual([
      "ack",
      "outbox",
    ]);
  });

  it("个人环境条目不进结果，只计数", () => {
    const found = searchEntries(docs, "确认");
    expect(found.results.some((r) => r.doc.personalEnv)).toBe(false);
    expect(found.personalCount).toBe(1);
  });

  it("按类型与难度筛选，并报出被筛掉的篇数", () => {
    const found = searchEntries(docs, "确认", { type: "pitfall", difficulties: [] });
    expect(found.results.map((r) => r.doc.slug)).toEqual(["pitfall"]);
    expect(found.hits).toBe(3);
    const advanced = searchEntries(docs, "确认", { type: "all", difficulties: ["advanced"] });
    expect(advanced.results.map((r) => r.doc.slug)).toEqual(["pitfall", "outbox"]);
  });

  it("标题与摘要里第一个命中的词给出高亮区间", () => {
    const [first] = searchEntries(docs, "ACK").results;
    expect(first?.titleMark).toEqual([3, 6]);
    expect(first?.summaryMark).toBeUndefined();
    const outbox = searchEntries(docs, "确认 消息").results.find((r) => r.doc.slug === "outbox");
    expect(outbox?.summaryMark).toEqual([9, 11]);
  });
});

describe("searchDocs / availableHints", () => {
  it("索引带上标签名、路线名与个人环境标记", () => {
    const content = catalog({
      entries: [entry("a", { title: "甲", tags: ["network"] }), entry("b", { personalEnv: true })],
      routes: [route("web", ["a"])],
    });
    expect(searchDocs(content)).toEqual([
      {
        slug: "a",
        title: "甲",
        summary: "a 的简介",
        type: "concept",
        difficulty: "basic",
        tags: ["网络"],
        routes: ["web"],
        personalEnv: false,
      },
      expect.objectContaining({ slug: "b", personalEnv: true }),
    ]);
  });

  it("常搜词只保留搜得到公开条目的", () => {
    expect(availableHints(docs, ["Nacos", "代理", "Kafka", "确认"])).toEqual(["Nacos", "确认"]);
  });
});
