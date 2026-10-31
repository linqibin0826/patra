import { describe, expect, it } from "vitest";
import { parseContent, parseEntryFile } from "./parse";
import type { RawContent, RawFile } from "./types";

const TAGS: RawFile = {
  file: "content/tags.yaml",
  text: "- slug: backend\n  label: 后端\n",
};

/// 拼一篇条目原文；fields 覆盖默认 frontmatter 行
function entryText(fields: Record<string, string> = {}, body = "正文\n") {
  const base: Record<string, string> = {
    title: "发件箱",
    summary: "先写库再发消息",
    type: "concept",
    difficulty: "advanced",
    tags: "[backend]",
    prerequisites: "[]",
    ...fields,
  };
  const lines = Object.entries(base)
    .filter(([, v]) => v !== "")
    .map(([k, v]) => `${k}: ${v}`);
  return `---\n${lines.join("\n")}\n---\n${body}`;
}

function entry(text: string, file = "content/entries/outbox.mdx"): RawFile {
  return { file, text };
}

function content(overrides: Partial<RawContent> = {}): RawContent {
  return { entries: [], routes: [], topics: [], tags: TAGS, ...overrides };
}

function rules(issues: { rule: string }[]) {
  return issues.map((i) => i.rule);
}

describe("条目解析", () => {
  it("合法条目：slug 来自文件名，可选字段取默认值，正文去掉 frontmatter", () => {
    const { entry: parsed, issues } = parseEntryFile(entry(entryText()));
    expect(issues).toEqual([]);
    expect(parsed?.slug).toBe("outbox");
    expect(parsed?.meta.topics).toEqual([]);
    expect(parsed?.meta.sources).toEqual([]);
    expect(parsed?.meta.personalEnv).toBe(false);
    expect(parsed?.body).toBe("正文\n");
  });

  it("出处带版本、issue 与 PR", () => {
    const { entry: parsed } = parseEntryFile(
      entry(entryText({ sources: "[{ version: v0.16, issue: PAP-140, pr: 343 }]" })),
    );
    expect(parsed?.meta.sources).toEqual([{ version: "v0.16", issue: "PAP-140", pr: 343 }]);
  });

  it("CRLF 换行的 frontmatter 也能解析", () => {
    const { entry: parsed, issues } = parseEntryFile(entry(entryText().replaceAll("\n", "\r\n")));
    expect(issues).toEqual([]);
    expect(parsed?.meta.title).toBe("发件箱");
  });

  it("文件名不是 slug 时报 slug-invalid", () => {
    const { issues } = parseEntryFile(entry(entryText(), "content/entries/Outbox_1.mdx"));
    expect(rules(issues)).toEqual(["slug-invalid"]);
  });

  it.each([
    ["没有 frontmatter", "正文\n"],
    ["YAML 语法错", "---\ntitle: [\n---\n正文\n"],
    ["缺 title", entryText({ title: "" })],
    ["难度取值不对", entryText({ difficulty: "hard" })],
    ["多了未知字段", entryText({ level: "intro" })],
    ["版本号格式不对", entryText({ sources: '[{ version: "0.16" }]' })],
    ["issue 格式不对", entryText({ sources: "[{ version: v0.16, issue: LIN-1 }]" })],
    ["标签为空", entryText({ tags: "[]" })],
  ])("%s 时报 frontmatter-invalid", (_name, text) => {
    const { entry: parsed, issues } = parseEntryFile(entry(text));
    expect(parsed).toBeUndefined();
    expect(rules(issues)).toEqual(["frontmatter-invalid"]);
  });
});

describe("路线、主题、标签解析", () => {
  it("合法内容全部进 catalog", () => {
    const { catalog, issues } = parseContent(
      content({
        entries: [entry(entryText())],
        routes: [
          {
            file: "content/routes/messaging.yaml",
            text: "title: 消息与事件\nsummary: 从异步读到发件箱\norder: 4\nentries: [outbox]\n",
          },
        ],
        topics: [
          {
            file: "content/topics/message-delivery.yaml",
            text: "title: 消息不丢\nsummary: 演进\n",
          },
        ],
      }),
    );
    expect(issues).toEqual([]);
    expect(catalog.entries.map((e) => e.slug)).toEqual(["outbox"]);
    expect(catalog.routes).toEqual([
      {
        slug: "messaging",
        file: "content/routes/messaging.yaml",
        title: "消息与事件",
        summary: "从异步读到发件箱",
        order: 4,
        entries: ["outbox"],
      },
    ]);
    expect(catalog.topics[0]?.slug).toBe("message-delivery");
    expect(catalog.tags).toEqual([{ slug: "backend", label: "后端" }]);
  });

  it("路线缺 order 时报 yaml-invalid", () => {
    const { catalog, issues } = parseContent(
      content({
        routes: [{ file: "content/routes/web.yaml", text: "title: W\nsummary: S\nentries: [a]\n" }],
      }),
    );
    expect(rules(issues)).toEqual(["yaml-invalid"]);
    expect(catalog.routes).toEqual([]);
  });

  it("主题 YAML 语法错时报 yaml-invalid", () => {
    const { issues } = parseContent(
      content({ topics: [{ file: "content/topics/x.yaml", text: "title: [\n" }] }),
    );
    expect(rules(issues)).toEqual(["yaml-invalid"]);
  });

  it("路线文件名不是 slug 时报 slug-invalid", () => {
    const { issues } = parseContent(
      content({
        routes: [
          {
            file: "content/routes/Web.yaml",
            text: "title: W\nsummary: S\norder: 1\nentries: [a]\n",
          },
        ],
      }),
    );
    expect(rules(issues)).toEqual(["slug-invalid"]);
  });

  it("标签项缺 label 时报 yaml-invalid", () => {
    const { issues } = parseContent(
      content({ tags: { file: "content/tags.yaml", text: "- slug: backend\n" } }),
    );
    expect(rules(issues)).toEqual(["yaml-invalid"]);
  });

  it("没有 tags.yaml 时报 yaml-invalid", () => {
    const { issues } = parseContent(content({ tags: null }));
    expect(issues).toEqual([
      expect.objectContaining({ rule: "yaml-invalid", file: "content/tags.yaml" }),
    ]);
  });
});
