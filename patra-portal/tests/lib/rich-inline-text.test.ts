import { describe, expect, it } from "vitest";
import {
  decodeEntities,
  highlightInlineNodes,
  type InlineNode,
  parseInlineMarkup,
} from "@/lib/rich-inline-text";

/** 简写：文本节点。 */
const text = (value: string): InlineNode => ({ kind: "text", value });
/** 简写：元素节点。 */
const el = (tag: string, ...children: InlineNode[]): InlineNode => ({
  kind: "element",
  tag,
  children,
});

/** 递归收集节点树的纯文本内容。 */
function textOf(nodes: InlineNode[]): string {
  return nodes.map((n) => (n.kind === "text" ? n.value : textOf(n.children))).join("");
}

describe("decodeEntities", () => {
  it("解码命名实体", () => {
    expect(decodeEntities("A &amp; B &lt;= C &gt; D &quot;E&quot; &apos;F&apos;&nbsp;G")).toBe(
      "A & B <= C > D \"E\" 'F'\u00a0G",
    );
  });

  it("解码十进制与十六进制数字实体", () => {
    expect(decodeEntities("&#8722;1 &#x2212;2")).toBe("−1 −2");
  });

  it("未知实体与孤立 & 原样保留", () => {
    expect(decodeEntities("&unknownx; a & b &#xZZ;")).toBe("&unknownx; a & b &#xZZ;");
  });

  it("码点越界与畸形码点原样保留", () => {
    expect(decodeEntities("X&#1114112;Y")).toBe("X&#1114112;Y");
    expect(decodeEntities("X&#99999999999999999999;Y")).toBe("X&#99999999999999999999;Y");
    expect(decodeEntities("X&#0;Y")).toBe("X&#0;Y");
    expect(decodeEntities("X&#xD800;Y")).toBe("X&#xD800;Y");
  });

  it("双重编码只解一层（不递归）", () => {
    expect(decodeEntities("&amp;lt;script&amp;gt;")).toBe("&lt;script&gt;");
  });

  it("原型链成员不被当实体", () => {
    expect(decodeEntities("A &constructor; B")).toBe("A &constructor; B");
  });

  it("缺分号不解码", () => {
    expect(decodeEntities("A &amp B")).toBe("A &amp B");
  });

  it("命名实体大小写敏感（大写不解码）", () => {
    expect(decodeEntities("&LT;i&GT;")).toBe("&LT;i&GT;");
  });
});

describe("parseInlineMarkup · 纯文本", () => {
  it("无标签文本原样输出为单一文本节点", () => {
    expect(parseInlineMarkup("Plain abstract text.")).toEqual([text("Plain abstract text.")]);
  });

  it("空字符串输出空数组", () => {
    expect(parseInlineMarkup("")).toEqual([]);
  });

  it("文本中的实体被解码", () => {
    expect(parseInlineMarkup("A &amp; B")).toEqual([text("A & B")]);
  });
});

describe("parseInlineMarkup · 排版标签", () => {
  it("解析下标：CO<sub>2</sub>", () => {
    expect(parseInlineMarkup("CO<sub>2</sub> fixation")).toEqual([
      text("CO"),
      el("sub", text("2")),
      text(" fixation"),
    ]);
  });

  it("解析上标 / 斜体 / 粗体 / 下划线", () => {
    expect(parseInlineMarkup("h<sup>-1</sup>")).toEqual([text("h"), el("sup", text("-1"))]);
    expect(parseInlineMarkup("<i>E. coli</i>")).toEqual([el("i", text("E. coli"))]);
    expect(parseInlineMarkup("<b>P</b>")).toEqual([el("b", text("P"))]);
    expect(parseInlineMarkup("<u>x</u>")).toEqual([el("u", text("x"))]);
  });

  it("支持嵌套", () => {
    expect(parseInlineMarkup("<i>A<sub>n</sub></i>")).toEqual([
      el("i", text("A"), el("sub", text("n"))),
    ]);
  });

  it("剥离全部属性", () => {
    expect(parseInlineMarkup('<i class="x" onclick="alert(1)">y</i>')).toEqual([
      el("i", text("y")),
    ]);
  });

  it("标签名大小写不敏感，归一化小写", () => {
    expect(parseInlineMarkup("<SUP>2</SUP>")).toEqual([el("sup", text("2"))]);
  });

  it("< 或 </ 后跟空白不构成标签（HTML 规范：视为文本）", () => {
    expect(parseInlineMarkup("a < b and c > d")).toEqual([text("a < b and c > d")]);
    expect(parseInlineMarkup("< i>x")).toEqual([text("< i>x")]);
    expect(parseInlineMarkup("</ i>x")).toEqual([text("</ i>x")]);
  });

  it("实体不会被解码成标签（解码在文本片段层，晚于标签识别）", () => {
    expect(parseInlineMarkup("&lt;i&gt;x&lt;/i&gt;")).toEqual([text("<i>x</i>")]);
  });
});

describe("parseInlineMarkup · 降级铁律与安全", () => {
  it("正文里的裸 < 按字面保留", () => {
    expect(parseInlineMarkup("P <median vs >0.05")).toEqual([text("P <median vs >0.05")]);
    expect(parseInlineMarkup("CD4 <0.05 cells")).toEqual([text("CD4 <0.05 cells")]);
  });

  it("白名单外标签按字面保留", () => {
    expect(parseInlineMarkup("<fib-4> index")).toEqual([text("<fib-4> index")]);
    expect(parseInlineMarkup("<script>alert(1)</script>")).toEqual([
      text("<script>alert(1)</script>"),
    ]);
  });

  it("未闭合标签自动闭合到段尾", () => {
    expect(parseInlineMarkup("<i>abc")).toEqual([el("i", text("abc"))]);
  });

  it("孤儿闭合标签按字面保留", () => {
    expect(parseInlineMarkup("abc</i>def")).toEqual([text("abc</i>def")]);
  });

  it("交叉嵌套：闭合外层时内层自动闭合", () => {
    expect(parseInlineMarkup("<i>a<b>c</i>d")).toEqual([
      el("i", text("a"), el("b", text("c"))),
      text("d"),
    ]);
  });

  it("恶意元素按字面保留", () => {
    expect(parseInlineMarkup("<img src=x onerror=alert(1)>")).toEqual([
      text("<img src=x onerror=alert(1)>"),
    ]);
    expect(parseInlineMarkup('<a href="javascript:alert(1)">x</a>')).toEqual([
      text('<a href="javascript:alert(1)">x</a>'),
    ]);
  });
});

describe("parseInlineMarkup · 审查遗留护栏", () => {
  it("自闭合白名单标签不吞后文", () => {
    expect(parseInlineMarkup("<i/>abc")).toEqual([el("i"), text("abc")]);
    expect(parseInlineMarkup("<sub />2")).toEqual([el("sub"), text("2")]);
  });

  it("同名嵌套：闭合最近的同名开标签", () => {
    expect(parseInlineMarkup("<i>a<i>b</i>c</i>d")).toEqual([
      el("i", text("a"), el("i", text("b")), text("c")),
      text("d"),
    ]);
  });

  it("超深嵌套安全降级：不抛异常，超限开标签按字面保留", () => {
    expect(() => parseInlineMarkup("<math>".repeat(10000))).not.toThrow();
    const nodes = parseInlineMarkup(`${"<i>".repeat(100)}x`);
    expect(textOf(nodes)).toBe(`${"<i>".repeat(36)}x`);
  });

  it("未闭合 annotation 不吞段尾正文（展开子节点）", () => {
    expect(parseInlineMarkup("<math><annotation>$$T$$ tail")).toEqual([
      el("math", text("$$T$$ tail")),
    ]);
  });

  it("被 </math> 弹栈闭合的 annotation 仍整体丢弃", () => {
    expect(parseInlineMarkup("<math><annotation>$$T$$</math>tail")).toEqual([
      el("math"),
      text("tail"),
    ]);
  });
});

describe("parseInlineMarkup · MathML 公式组", () => {
  it("math 子树内放行 MathML Core 标签", () => {
    expect(parseInlineMarkup("x<math><msub><mi>T</mi><mn>2</mn></msub></math>y")).toEqual([
      text("x"),
      el("math", el("msub", el("mi", text("T")), el("mn", text("2")))),
      text("y"),
    ]);
  });

  it("annotation 内容整体丢弃（避免公式显示两遍）", () => {
    expect(
      parseInlineMarkup(
        '<math><semantics><mrow><mi>T</mi></mrow><annotation encoding="application/x-tex">$$T$$</annotation></semantics></math>',
      ),
    ).toEqual([el("math", el("semantics", el("mrow", el("mi", text("T")))))]);
  });

  it("游离在 math 外的 MathML 标签按字面保留", () => {
    expect(parseInlineMarkup("<mi>x</mi>")).toEqual([text("<mi>x</mi>")]);
  });

  it("生产库真实样本：解析出 math 元素且文本内容不丢", () => {
    const sample =
      "growth with <math><msub><mi>μ</mi> <mrow><mi>max</mi></mrow> </msub> </math> values of 0.37 h<sup>-1</sup>";
    const nodes = parseInlineMarkup(sample);
    const math = nodes.find((n) => n.kind === "element" && n.tag === "math");
    expect(math).toBeDefined();
    // 元素间空白成为文本节点：msub 内尾随 1 空格 + math 内尾随 1 空格 + " values" 前导 1 空格
    expect(textOf(nodes)).toBe("growth with μ max   values of 0.37 h-1");
  });

  it("带引号属性的自闭合 mspace 不吞后续内容", () => {
    expect(parseInlineMarkup('<math><mi>a</mi><mspace width="0.25em"/><mi>b</mi></math>')).toEqual([
      el("math", el("mi", text("a")), el("mspace"), el("mi", text("b"))),
    ]);
  });
});

describe("highlightInlineNodes", () => {
  const mark = (value: string): InlineNode => el("mark", text(value));

  it("q 为空时原样返回", () => {
    const nodes = parseInlineMarkup("GLP-1 agonists");
    expect(highlightInlineNodes(nodes, "  ")).toBe(nodes);
  });

  it("大小写不敏感，多处命中", () => {
    expect(highlightInlineNodes(parseInlineMarkup("GLP-1 and glp-1"), "glp-1")).toEqual([
      mark("GLP-1"),
      text(" and "),
      mark("glp-1"),
    ]);
  });

  it("跨标签命中拆成多段 mark，标签结构不变", () => {
    expect(highlightInlineNodes(parseInlineMarkup("CO<sub>2</sub> fixation"), "co2")).toEqual([
      mark("CO"),
      el("sub", mark("2")),
      text(" fixation"),
    ]);
  });

  it("正则元字符按字面匹配", () => {
    expect(highlightInlineNodes(parseInlineMarkup("IL-(6) level"), "(6)")).toEqual([
      text("IL-"),
      mark("(6)"),
      text(" level"),
    ]);
  });

  it("math 子树不插 mark，但其文本参与偏移计算", () => {
    const nodes = parseInlineMarkup("<math><mi>x</mi></math> x");
    expect(highlightInlineNodes(nodes, "x")).toEqual([
      el("math", el("mi", text("x"))),
      text(" "),
      mark("x"),
    ]);
  });

  it("无命中时返回等价节点树", () => {
    const nodes = parseInlineMarkup("<i>abc</i>");
    expect(highlightInlineNodes(nodes, "zzz")).toEqual(nodes);
  });
});
