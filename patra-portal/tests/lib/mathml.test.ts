import { describe, expect, it } from "vitest";
import { MATH_ELEMENT_TAGS, type MathNode, parseMathml } from "@/lib/mathml";

/** 节点树转回紧凑标记，便于断言（属性按名字排序）。 */
function serialize(nodes: MathNode[]): string {
  return nodes
    .map((node) => {
      if (node.kind === "text") return node.value;
      const attrs = Object.entries(node.attrs)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([k, v]) => ` ${k}="${v}"`)
        .join("");
      return `<${node.tag}${attrs}>${serialize(node.children)}</${node.tag}>`;
    })
    .join("");
}

const parse = (mathml: string) => serialize(parseMathml(mathml));

describe("MATH_ELEMENT_TAGS", () => {
  it("覆盖 PAP-86 夹具里出现过的全部 21 个 MathML 标签", () => {
    const fromFixtures = [
      "math",
      "mfenced",
      "mfrac",
      "mi",
      "mn",
      "mo",
      "mover",
      "mpadded",
      "mrow",
      "mspace",
      "msqrt",
      "mstyle",
      "msub",
      "msubsup",
      "msup",
      "mtable",
      "mtd",
      "mtext",
      "mtr",
      "munder",
      "munderover",
    ];
    for (const tag of fromFixtures) {
      expect(MATH_ELEMENT_TAGS.has(tag), tag).toBe(true);
    }
  });
});

describe("parseMathml", () => {
  it("保留白名单标签与排版属性", () => {
    expect(
      parse(
        '<math display="block" id="M2" overflow="scroll"><mrow><mi mathvariant="bold">x</mi><mo stretchy="false">(</mo></mrow></math>',
      ),
    ).toBe(
      '<math display="block"><mrow><mi mathvariant="bold">x</mi><mo stretchy="false">(</mo></mrow></math>',
    );
  });

  it("矩阵与分段函数：mtable / mtr / mtd 及 columnalign 保留", () => {
    expect(
      parse('<math><mtable columnalign="left"><mtr><mtd><mn>1</mn></mtd></mtr></mtable></math>'),
    ).toBe('<math><mtable columnalign="left"><mtr><mtd><mn>1</mn></mtd></mtr></mtable></math>');
  });

  it("去掉事件、样式、链接等非排版属性", () => {
    expect(
      parse(
        '<math><mi onclick="alert(1)" style="color:red" href="javascript:x" class="c" xlink:href="y">a</mi></math>',
      ),
    ).toBe("<math><mi>a</mi></math>");
  });

  it("白名单外的元素去掉标签、保留内容（文本照常转义显示）", () => {
    expect(parse("<math><script>alert(1)</script><mi>a</mi></math>")).toBe(
      "<math>alert(1)<mi>a</mi></math>",
    );
  });

  it("annotation 与 annotation-xml 整体丢弃（同一公式的 TeX 重复表述）", () => {
    expect(
      parse(
        '<math><semantics><mi>x</mi><annotation encoding="application/x-tex">x</annotation><annotation-xml>y</annotation-xml></semantics></math>',
      ),
    ).toBe("<math><semantics><mi>x</mi></semantics></math>");
  });

  it("mfenced 改写成 mrow + 两个括号 mo，子项之间插分隔符（Chrome 的 MathML Core 不认 mfenced）", () => {
    expect(parse("<math><mfenced><mi>a</mi><mi>b</mi></mfenced></math>")).toBe(
      '<math><mrow><mo fence="true" form="prefix">(</mo><mi>a</mi><mo separator="true">,</mo><mi>b</mi><mo fence="true" form="postfix">)</mo></mrow></math>',
    );
  });

  it("mfenced 的 open / close / separators：分隔符不够时重复最后一个，空串不加", () => {
    expect(
      parse(
        '<math><mfenced open="[" close="]" separators=";|"><mi>a</mi><mi>b</mi><mi>c</mi><mi>d</mi></mfenced></math>',
      ),
    ).toBe(
      '<math><mrow><mo fence="true" form="prefix">[</mo><mi>a</mi><mo separator="true">;</mo><mi>b</mi><mo separator="true">|</mo><mi>c</mi><mo separator="true">|</mo><mi>d</mi><mo fence="true" form="postfix">]</mo></mrow></math>',
    );
    expect(
      parse('<math><mfenced open="{" close="" separators=""><mi>a</mi><mi>b</mi></mfenced></math>'),
    ).toBe('<math><mrow><mo fence="true" form="prefix">{</mo><mi>a</mi><mi>b</mi></mrow></math>');
  });

  it("解码实体，自闭合标签不吞后面的兄弟节点", () => {
    expect(parse('<math><mo>&lt;</mo><mspace width="1em"/><mi>&#x3B1;</mi></math>')).toBe(
      '<math><mo><</mo><mspace width="1em"></mspace><mi>α</mi></math>',
    );
  });

  it("没有 math 根元素时补上一个，保证按公式渲染", () => {
    expect(parse("<mi>x</mi>")).toBe("<math><mi>x</mi></math>");
  });

  it("嵌套超过上限的部分丢弃标签、只留文本，畸形输入不会压垮渲染", () => {
    const deep = `<math>${"<mrow>".repeat(100)}x${"</mrow>".repeat(100)}</math>`;
    expect(parse(deep)).toContain("x");
    expect(parse(deep).split("<mrow>").length - 1).toBeLessThan(100);
  });
});
