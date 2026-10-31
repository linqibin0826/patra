import { describe, expect, it } from "vitest";
import type { RawContent, RawFile } from "./types";
import { validateContent, validateEntryFiles } from "./validate";

// 诊断题与复述题的参数按教学组件登记表写全
const CHOICE = 'options={["甲", "乙"]} answer="A" ifRight="对" ifWrong="错"';
const POINTS = 'points={[{ text: "一" }, { text: "二" }]}';
const BODY = `<Diagnose question="为什么要异步？" ${CHOICE} />\n\n正文\n\n<Recap prompt="复述一遍" ${POINTS} />\n`;

function mdx(slug: string, frontmatter: string, body = BODY): RawFile {
  return { file: `content/entries/${slug}.mdx`, text: `---\n${frontmatter}\n---\n${body}` };
}

const META = "summary: 简介\ntype: concept\ntags: [backend]";

const VALID: RawContent = {
  entries: [
    mdx("why-async", `title: 为什么要异步\n${META}\ndifficulty: intro\nprerequisites: []`),
    mdx(
      "message-queue",
      `title: 消息队列\n${META}\ndifficulty: basic\nprerequisites: [why-async]`,
      `<Diagnose question="消息队列解决什么问题？" ${CHOICE} />\n\n<CodeRef at="patra-learn/package.json@pending" />\n\n<Transfer prompt="换成 Kafka 呢？" ${POINTS} />\n`,
    ),
  ],
  routes: [
    {
      file: "content/routes/messaging.yaml",
      text: "title: 消息与事件\nsummary: 简介\norder: 1\nentries: [why-async, message-queue]\n",
    },
  ],
  topics: [],
  tags: { file: "content/tags.yaml", text: "- slug: backend\n  label: 后端\n" },
};

const deps = { fileExists: (path: string) => path === "patra-learn/package.json" };

describe("validateContent", () => {
  it("合法内容没有问题，并收集代码引用", () => {
    const { catalog, issues, codeRefs } = validateContent(VALID, deps);
    expect(issues).toEqual([]);
    expect(catalog.entries).toHaveLength(2);
    expect(codeRefs.map((r) => r.at)).toEqual(["patra-learn/package.json@pending"]);
  });

  it("一篇条目里的多种问题一起报出", () => {
    const bad = mdx(
      "bad",
      `title: 坏条目\n${META}\ndifficulty: basic\nprerequisites: [nope]`,
      `地址 100.64.0.1\n\n<Recap prompt="x" ${POINTS} />\n`,
    );
    const { issues } = validateContent({ ...VALID, entries: [...VALID.entries, bad] }, deps);
    expect(issues.map((i) => i.rule).sort()).toEqual(
      ["diagnostic-missing", "prerequisite-missing", "private-address"].sort(),
    );
  });

  it("路线与标签文件也扫公开边界", () => {
    const route: RawFile = {
      file: "content/routes/web.yaml",
      text: "title: 在 10.1.2.3 上\nsummary: 简介\norder: 2\nentries: [why-async]\n",
    };
    const { issues } = validateContent({ ...VALID, routes: [...VALID.routes, route] }, deps);
    expect(issues.map((i) => [i.rule, i.file])).toEqual([
      ["private-address", "content/routes/web.yaml"],
    ]);
  });

  it("frontmatter 不合法的条目只报解析问题，不再跑正文规则", () => {
    const bad: RawFile = { file: "content/entries/x.mdx", text: "没有 frontmatter\n" };
    const { issues } = validateContent({ ...VALID, entries: [...VALID.entries, bad] }, deps);
    expect(issues.map((i) => i.rule)).toEqual(["frontmatter-invalid"]);
  });
});

describe("解析失败的条目被引用时", () => {
  it("引用它的前置与路线不报不存在，只报它自己的解析问题", () => {
    const broken: RawFile = {
      file: "content/entries/why-async.mdx",
      text: "---\ntitle: 缺字段\n---\n正文\n",
    };
    const entries = [broken, ...VALID.entries.slice(1)];
    const { issues } = validateContent({ ...VALID, entries }, deps);
    expect(issues.map((i) => [i.rule, i.file])).toEqual([
      ["frontmatter-invalid", "content/entries/why-async.mdx"],
    ]);
  });
});

describe("validateEntryFiles（单篇校验）", () => {
  const tags = [{ slug: "backend", label: "后端" }];

  it("不查跨篇规则：占位前置不报", () => {
    const template: RawFile = {
      file: "templates/Concept.mdx",
      text: `---\ntitle: 〔标题〕\n${META}\ndifficulty: intro\nprerequisites: [some-placeholder]\n---\n{/* 写法说明 */}\n\n${BODY}`,
    };
    expect(validateEntryFiles([template], tags, deps)).toEqual([]);
  });

  it("单篇规则照查：未知标签报 tag-unknown", () => {
    const file = mdx(
      "a",
      `title: A\nsummary: 简介\ntype: concept\ntags: [nope]\ndifficulty: intro\nprerequisites: []`,
    );
    expect(validateEntryFiles([file], tags, deps).map((i) => i.rule)).toEqual(["tag-unknown"]);
  });
});
