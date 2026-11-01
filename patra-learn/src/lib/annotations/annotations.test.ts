import { describe, expect, it } from "vitest";
import {
  ANNOTATIONS_KEY,
  type Annotation,
  exportable,
  exportMarkdown,
  loadAnnotations,
  parseAnnotations,
  saveAnnotations,
  withAdded,
  withAnchorResult,
  withEdited,
  withExported,
  withRemoved,
} from "./annotations";

const QUOTE = { exact: "为每个监听器记一行", prefix: "发布事件时，", suffix: "；处理成功才算完成" };

function note(overrides: Partial<Annotation> = {}): Annotation {
  return {
    id: "a1",
    slug: "spring-modulith-event-publication-registry",
    kind: "question",
    text: "10 个监听器就是 10 行吗？",
    quote: QUOTE,
    createdAt: 100,
    ...overrides,
  };
}

/// 内存里的假存储；throwOn 让读或写抛错，模拟隐私模式、禁用存储、配额满
function fakeStorage(
  initial: Record<string, string> = {},
  throwOn: "get" | "set" | "none" = "none",
) {
  const data = new Map(Object.entries(initial));
  return {
    data,
    getItem(key: string) {
      if (throwOn === "get") throw new Error("SecurityError");
      return data.get(key) ?? null;
    },
    setItem(key: string, value: string) {
      if (throwOn === "set") throw new Error("QuotaExceededError");
      data.set(key, value);
    },
    removeItem(key: string) {
      data.delete(key);
    },
  };
}

describe("parseAnnotations：存储里的原文 → 批注列表，坏数据不抛错", () => {
  it("没有记录或不是 JSON 数组时是空列表", () => {
    expect(parseAnnotations(null)).toEqual([]);
    expect(parseAnnotations("{oops")).toEqual([]);
    expect(parseAnnotations('{"a":1}')).toEqual([]);
  });

  it("保留合法的批注，丢掉缺字段或类型不对的", () => {
    const good = note({ exportedAt: 300, orphaned: true });
    const raw = JSON.stringify([
      good,
      { ...note({ id: "a2" }), kind: "todo" },
      { ...note({ id: "a3" }), quote: { exact: "", prefix: "", suffix: "" } },
      { id: "a4" },
      "x",
    ]);
    expect(parseAnnotations(raw)).toEqual([good]);
  });
});

describe("增删改：返回新列表，不改原列表", () => {
  it("加一条放在最后", () => {
    const before = [note()];
    const after = withAdded(before, note({ id: "a2", kind: "note" }));
    expect(after.map((a) => a.id)).toEqual(["a1", "a2"]);
    expect(before).toHaveLength(1);
  });

  it("编辑改类型与说明，记下修改时间", () => {
    const after = withEdited([note()], "a1", { kind: "note", text: "改成笔记" }, 200);
    expect(after[0]).toMatchObject({
      kind: "note",
      text: "改成笔记",
      updatedAt: 200,
      createdAt: 100,
    });
  });

  it("删掉指定的一条", () => {
    expect(withRemoved([note(), note({ id: "a2" })], "a1").map((a) => a.id)).toEqual(["a2"]);
  });
});

describe("withAnchorResult：条目页挂完一遍后，记下哪些挂不回去", () => {
  it("这一篇里没找到原文的标成原文已变，找到的清掉标记，别的条目不动", () => {
    const list = [
      note({ id: "a1" }),
      note({ id: "a2", orphaned: true }),
      note({ id: "a3", slug: "other", orphaned: true }),
    ];
    const after = withAnchorResult(
      list,
      "spring-modulith-event-publication-registry",
      new Set(["a2"]),
    );
    expect(after.find((a) => a.id === "a1")?.orphaned).toBe(true);
    expect(after.find((a) => a.id === "a2")?.orphaned).toBeUndefined();
    expect(after.find((a) => a.id === "a3")?.orphaned).toBe(true);
  });

  it("结果没变化时返回原列表，免得白写一次存储", () => {
    const list = [note({ id: "a1" })];
    expect(
      withAnchorResult(list, "spring-modulith-event-publication-registry", new Set(["a1"])),
    ).toBe(list);
  });
});

describe("导出给 Claude", () => {
  it("只导出没导出过、原文还在的「有问题」；勾上后带上导出过的", () => {
    const list = [
      note({ id: "q1" }),
      note({ id: "q2", exportedAt: 50 }),
      note({ id: "q3", orphaned: true }),
      note({ id: "n1", kind: "note" }),
    ];
    expect(exportable(list, false).map((a) => a.id)).toEqual(["q1"]);
    expect(exportable(list, true).map((a) => a.id)).toEqual(["q1", "q2"]);
  });

  it("导出后记下导出时间", () => {
    const after = withExported([note({ id: "q1" }), note({ id: "q2" })], ["q1"], 900);
    expect(after.map((a) => a.exportedAt)).toEqual([900, undefined]);
  });

  it("生成一段 Markdown：每条带条目标题、页面链接、源文件、原文与问题", () => {
    const md = exportMarkdown(
      [
        note(),
        note({
          id: "a2",
          slug: "msg-transactional-outbox",
          text: "",
          quote: { ...QUOTE, exact: "不保证不重" },
        }),
      ],
      {
        "spring-modulith-event-publication-registry": "事件发布登记",
        "msg-transactional-outbox": "发件箱",
      },
      "https://learn.linqibin.dev",
      new Date(2026, 10, 1),
    );
    expect(md).toBe(
      [
        "# 学习站批注：有问题 2 条（11/01 导出）",
        "",
        "## 1. 事件发布登记",
        "页面：https://learn.linqibin.dev/entries/spring-modulith-event-publication-registry",
        "文件：patra-learn/content/entries/spring-modulith-event-publication-registry.mdx",
        "原文：> 为每个监听器记一行",
        "我的问题：10 个监听器就是 10 行吗？",
        "",
        "## 2. 发件箱",
        "页面：https://learn.linqibin.dev/entries/msg-transactional-outbox",
        "文件：patra-learn/content/entries/msg-transactional-outbox.mdx",
        "原文：> 不保证不重",
        "我的问题：（没写说明）",
      ].join("\n"),
    );
  });

  it("条目已经不在了就用 slug 当标题", () => {
    const md = exportMarkdown([note({ slug: "gone" })], {}, "https://x", new Date(2026, 0, 9));
    expect(md).toContain("## 1. gone");
    expect(md).toContain("（01/09 导出）");
  });
});

describe("loadAnnotations / saveAnnotations：读写都 try/catch", () => {
  it("读写同一个键", () => {
    const storage = fakeStorage();
    expect(saveAnnotations(storage, [note()])).toBe(true);
    expect(JSON.parse(storage.data.get(ANNOTATIONS_KEY) ?? "")).toEqual([note()]);
    expect(loadAnnotations(storage)).toEqual([note()]);
  });

  it("拿不到存储或读写抛错，读按空列表、写返回 false", () => {
    expect(loadAnnotations(undefined)).toEqual([]);
    expect(loadAnnotations(fakeStorage({}, "get"))).toEqual([]);
    expect(saveAnnotations(fakeStorage({}, "set"), [note()])).toBe(false);
    expect(saveAnnotations(undefined, [note()])).toBe(false);
  });
});
