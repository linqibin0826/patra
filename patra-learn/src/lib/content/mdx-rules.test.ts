import { describe, expect, it } from "vitest";
import { z } from "zod";
import { componentRegistry } from "@/teaching/registry";
import { checkEntryBody, readComponents } from "./mdx-rules";

// 诊断题与复述题的参数按教学组件登记表写全，这里只关心首尾结构
const CHOICE = 'options={["甲", "乙"]} answer="A" ifRight="对" ifWrong="错"';
const POINTS = 'points={[{ text: "一" }, { text: "二" }]}';
const OPEN = `<Diagnose question="打开网页时发生了什么？" ${CHOICE} />`;
const CLOSE = `<Recap prompt="用自己的话复述一遍" ${POINTS} />`;

/// 首尾合法，中间放 middle
function body(middle: string) {
  return `${OPEN}\n\n${middle}\n\n${CLOSE}\n`;
}

function check(text: string) {
  return checkEntryBody(
    { slug: "outbox", file: "content/entries/outbox.mdx", body: text },
    componentRegistry,
  );
}

function rules(text: string) {
  return check(text).issues.map((i) => i.rule);
}

describe("正文结构", () => {
  it("诊断题开头、复述题结尾的正文没有问题", () => {
    expect(rules(body("正文段落。"))).toEqual([]);
  });

  it("迁移题结尾也可以", () => {
    expect(
      rules(`${OPEN}\n\n正文\n\n<Transfer prompt="换到订单服务会怎样？" ${POINTS} />\n`),
    ).toEqual([]);
  });

  it("第一块是段落时报 diagnostic-missing", () => {
    expect(rules(`前言\n\n${OPEN}\n\n${CLOSE}\n`)).toEqual(["diagnostic-missing"]);
  });

  it("诊断题写在段落里不算开头", () => {
    expect(rules(`看这里 ${OPEN} 吧\n\n${CLOSE}\n`)).toEqual(["diagnostic-missing"]);
  });

  it("诊断题嵌在其他组件里不算开头", () => {
    expect(rules(`<Recap prompt="x" ${POINTS}>\n${OPEN}\n</Recap>\n\n${CLOSE}\n`)).toEqual([
      "diagnostic-missing",
    ]);
  });

  it("最后一块是段落时报 recap-missing", () => {
    expect(rules(`${OPEN}\n\n${CLOSE}\n\n后记\n`)).toEqual(["recap-missing"]);
  });

  it("空正文同时缺诊断题与复述题", () => {
    expect(rules("")).toEqual(["diagnostic-missing", "recap-missing"]);
  });

  it("标签与内容写在同一行（MDX 解析成段落里的行内组件）也算首尾", () => {
    expect(rules(`${OPEN}\n\n正文\n\n<Recap prompt="复述" ${POINTS}>答案</Recap>\n`)).toEqual([]);
  });

  it("段落里除了组件还有别的文字，不算首尾", () => {
    expect(
      rules(`${OPEN}\n\n正文\n\n<Recap prompt="复述" ${POINTS}>答案</Recap> 补一句\n`),
    ).toEqual(["recap-missing"]);
  });

  it("判断首尾时跳过注释", () => {
    expect(rules(`{/* 占位 */}\n\n${OPEN}\n\n${CLOSE}\n\n{/* 结尾注释 */}\n`)).toEqual([]);
  });

  it("GFM 表格正常解析", () => {
    expect(rules(body("| 选项 | 代价 |\n| --- | --- |\n| A | 慢 |"))).toEqual([]);
  });
});

describe("禁止的写法", () => {
  it("import 报 mdx-esm", () => {
    expect(rules(`import X from "x"\n\n${body("正文")}`)).toEqual(["mdx-esm"]);
  });

  it("注释以外的表达式报 mdx-expression", () => {
    expect(rules(body("{1 + 1}"))).toEqual(["mdx-expression"]);
    expect(rules(body("段落里 {name} 也不行"))).toEqual(["mdx-expression"]);
  });

  it("MDX 语法错误只报 mdx-syntax", () => {
    expect(rules('<Diagnose question="没闭合\n')).toEqual(["mdx-syntax"]);
  });
});

describe("组件与参数", () => {
  it.each([
    ["未登记的组件", "<Foo />"],
    ["HTML 标签", "<div>块</div>"],
    ["嵌在组件里的未登记组件", `<Recap prompt="x" ${POINTS}>\n<Foo />\n</Recap>`],
  ])("%s 报 component-unknown", (_name, middle) => {
    expect(rules(body(middle))).toEqual(["component-unknown"]);
  });

  it.each([
    ["变量", "<Diagnose question={q} />"],
    ["展开", "<Diagnose {...p} />"],
  ])("属性用%s时报 component-prop-dynamic", (_name, middle) => {
    expect(rules(body(middle))).toContain("component-prop-dynamic");
  });

  it.each([
    ["缺必填参数", "<Diagnose />"],
    ["类型不对", "<Diagnose question={3} />"],
    ["多余参数", '<Diagnose question="?" extra="x" />'],
  ])("%s 报 component-props-invalid", (_name, middle) => {
    expect(rules(body(middle))).toEqual(["component-props-invalid"]);
  });

  it("字面量数组、对象、负数、模板字符串、布尔简写都能求值并通过 schema", () => {
    const registry = {
      ...componentRegistry,
      Probe: {
        props: z.strictObject({
          list: z.array(z.number()),
          obj: z.strictObject({ k: z.string() }),
          flag: z.boolean(),
        }),
        children: "none" as const,
      },
    };
    const result = checkEntryBody(
      {
        slug: "a",
        file: "content/entries/a.mdx",
        body: body("<Probe list={[1, -2]} obj={{ k: `v` }} flag />"),
      },
      registry,
    );
    expect(result.issues).toEqual([]);
  });

  it("不许有子内容的组件写了子内容报 component-children", () => {
    expect(rules(body('<CodeRef at="a.ts@pending">文字</CodeRef>'))).toEqual([
      "component-children",
    ]);
  });
});

describe("代码引用收集", () => {
  it("收集 CodeRef 的 at 与 lines", () => {
    const { codeRefs } = check(body('<CodeRef at="p/a.ts@pending" lines="3-5" />'));
    expect(codeRefs).toEqual([
      { entry: "outbox", file: "content/entries/outbox.mdx", at: "p/a.ts@pending", lines: "3-5" },
    ]);
  });
});

describe("readComponents：页面在构建时读组件的字面量参数", () => {
  it("按出现顺序返回同名组件的参数，含写在段落里的行内组件", () => {
    const text = body(
      '<CodeRef at="a.java@pending" lines="1-2" />\n\n正文 <CodeRef at="b.java@pending" /> 尾巴',
    );
    expect(readComponents(text, "CodeRef")).toEqual([
      { at: "a.java@pending", lines: "1-2" },
      { at: "b.java@pending" },
    ]);
    expect(readComponents(text, "Diagnose")[0]).toMatchObject({
      question: "打开网页时发生了什么？",
      options: ["甲", "乙"],
      answer: "A",
    });
  });

  it("解析失败或没有该组件时返回空列表，不是字面量的参数跳过", () => {
    expect(readComponents("<Diagnose question=", "Diagnose")).toEqual([]);
    expect(readComponents(body("正文"), "Quiz")).toEqual([]);
    expect(readComponents('<CodeRef at={x} lines="3" />', "CodeRef")).toEqual([{ lines: "3" }]);
  });
});
