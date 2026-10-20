import { render, screen, within } from "@testing-library/react";
import { makeBody, para, reference, section, text } from "@tests/fixtures/fulltext";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { renderFullTextBody } from "@/components/portal/fulltext/FullTextBody";
import type { FullTextBody, FullTextInline } from "@/types/fulltext";

function renderBody(body: FullTextBody) {
  const result = renderFullTextBody(body, "42");
  const view = render(<article>{result.content}</article>);
  return { ...view, result };
}

const cite = (target: string, label: string): FullTextInline => ({
  kind: "xref",
  refType: "bibr",
  targets: [target],
  children: [text(label)],
});

describe("renderFullTextBody · 章节", () => {
  it("一级章节是 h2、二级 h3、三级及以下 h4，标题带页内锚点", () => {
    renderBody(
      makeBody({
        sections: [
          section(
            "s1",
            "Methods",
            [],
            [section("s1-1", "Design", [], [section("s1-1-1", "Arms")])],
          ),
        ],
      }),
    );

    expect(screen.getByRole("heading", { level: 2, name: "Methods" })).toHaveAttribute(
      "id",
      "ft-s1",
    );
    expect(screen.getByRole("heading", { level: 3, name: "Design" })).toHaveAttribute(
      "id",
      "ft-s1-1",
    );
    expect(screen.getByRole("heading", { level: 4, name: "Arms" })).toHaveAttribute(
      "id",
      "ft-s1-1-1",
    );
  });

  it("无标题章节不渲染标题，内容照常接在后面", () => {
    renderBody(makeBody({ sections: [section("s9", "", [para("Orphan paragraph.")])] }));
    expect(screen.queryByRole("heading")).toBeNull();
    expect(screen.getByText("Orphan paragraph.")).toBeInTheDocument();
  });

  it("正文前导块排在第一个章节之前", () => {
    const { container } = renderBody(
      makeBody({
        blocks: [para("Strengths and limitations.")],
        sections: [section("s1", "Intro")],
      }),
    );
    const html = container.innerHTML;
    expect(html.indexOf("Strengths and limitations.")).toBeLessThan(html.indexOf("Intro"));
  });

  it("补充材料一节整节不渲染", () => {
    renderBody(
      makeBody({
        sections: [
          section("s1", "Intro"),
          section("s2", "Supplementary Material", [para("S1 Table.")]),
        ],
      }),
    );
    expect(screen.queryByText("Supplementary Material")).toBeNull();
    expect(screen.queryByText("S1 Table.")).toBeNull();
  });

  it("正文包在 [data-ft-body] 里；文末附加不在正文里，进声明区", () => {
    const { container, result } = renderBody(
      makeBody({ sections: [section("s1", "Intro")], back: [section("ack", "Acknowledgements")] }),
    );
    expect(container.querySelector("[data-ft-body]")).not.toBeNull();
    expect(container).not.toHaveTextContent("Acknowledgements");
    expect(result.declarations).not.toBeNull();
  });
});

describe("renderFullTextBody · 标签栏与章节号（PAP-89）", () => {
  it("一级章节号 01，二级 4.5 这样的序号放在标签格；三级不编号；标题名不含序号", () => {
    const subs = [1, 2, 3, 4, 5].map((n) =>
      section(`s4-${n}`, `Sub ${n}`, [], n === 5 ? [section("s4-5-1", "Deep")] : []),
    );
    renderBody(
      makeBody({
        sections: [
          section("s1", "Intro"),
          section("s2", "Related"),
          section("s3", "Methods"),
          section("s4", "Results", [], subs),
        ],
      }),
    );
    const h2 = screen.getByRole("heading", { level: 2, name: "Intro" });
    expect(h2.closest(".grid")?.firstElementChild).toHaveTextContent("01");
    const h3 = screen.getByRole("heading", { level: 3, name: "Sub 5" });
    expect(h3.closest(".grid")?.firstElementChild).toHaveTextContent("4.5");
    const h4 = screen.getByRole("heading", { level: 4, name: "Deep" });
    expect(h4.closest(".grid")?.firstElementChild).toHaveAttribute("aria-hidden", "true");
  });

  it("连续的段落放在同一个标签栏行里", () => {
    renderBody(
      makeBody({ sections: [section("s1", "Intro", [para("A."), para("B."), para("C.")])] }),
    );
    const row = screen.getByText("A.").closest(".grid");
    expect(row).toContainElement(screen.getByText("C."));
  });
});

describe("renderFullTextBody · 引用号（PAP-89）", () => {
  const sup = (...children: FullTextInline[]): FullTextInline => ({
    kind: "style",
    style: "sup",
    children,
  });

  it("上标引用前的空格去掉，上标紧贴前文", () => {
    const { container } = renderBody(
      makeBody({
        sections: [section("s1", "Intro", [para("smoking. ", sup(cite("r1", "8")))])],
      }),
    );
    expect(container.querySelector("p")?.textContent).toBe("smoking.8");
  });

  it("空格写在上标里、引用号前面时也去掉（PMC 常见写法 <sup> 1</sup>）", () => {
    const { container } = renderBody(
      makeBody({
        sections: [section("s1", "Intro", [para("(AC).", sup(text(" "), cite("r1", "1")))])],
      }),
    );
    expect(container.querySelector("p")?.textContent).toBe("(AC).1");
  });

  it("方括号写法照原文，不去空格", () => {
    const { container } = renderBody(
      makeBody({ sections: [section("s1", "Intro", [para("as shown [", cite("r1", "3"), "]")])] }),
    );
    expect(container.querySelector("p")?.textContent).toBe("as shown [3]");
  });

  it("统计每条参考文献被引用的次数", () => {
    const { result } = renderBody(
      makeBody({
        sections: [section("s1", "Intro", [para(cite("r1", "1"), " x ", cite("r1", "1"))])],
      }),
    );
    expect(result.citeCountByRef.r1).toBe(2);
    expect(result.citeCountByRef.r2).toBeUndefined();
  });

  it("一个引用指向多条参考文献时 data-cite-refs 列出全部", () => {
    const multi: FullTextInline = {
      kind: "xref",
      refType: "bibr",
      targets: ["r1", "r2", "r3"],
      children: [text("1–3")],
    };
    renderBody(
      makeBody({
        sections: [section("s1", "Intro", [para(multi)])],
        references: [reference("r1", "1"), reference("r2", "2"), reference("r3", "3")],
      }),
    );
    const link = screen.getByRole("link", { name: "参考文献 1–3" });
    expect(link).toHaveAttribute("data-cite-ref", "r1");
    expect(link).toHaveAttribute("data-cite-refs", "r1 r2 r3");
  });
});

describe("renderFullTextBody · 声明与附注（PAP-89）", () => {
  const table = {
    kind: "table" as const,
    id: "ta1",
    label: "Table A1",
    caption: [para("Extra table.")],
    head: [],
    body: [],
    footnotes: [],
  };
  const back = [
    ...[
      "Acknowledgments",
      "Author contributions",
      "Consent",
      "Data",
      "Declaration",
      "Ethics",
      "Funding",
      "ORCID iD",
    ].map((t, i) => section(`b${i}`, t, [para(`${t} text.`)])),
    section("fn", "", [para("Untitled footnote.")]),
    section("app", "Appendix", [table]),
  ];

  it("有标题的文末节合成 #declarations 里的 dl，dt 带原锚点；标题「声明与附注」+「9 项」", () => {
    const { result } = renderBody(makeBody({ back }));
    const { container } = render(<div>{result.declarations}</div>);
    const block = container.querySelector("#declarations") as HTMLElement;
    expect(block).not.toBeNull();
    expect(within(block).getByRole("heading", { name: /声明与附注/ })).toHaveTextContent("9 项");
    const terms = within(block).getAllByRole("term");
    expect(terms).toHaveLength(8);
    expect(terms[0]).toHaveAttribute("id", "ft-b0");
    expect(terms[0]).toHaveTextContent("Acknowledgments");
  });

  it("无标题的文末节内容接在 dl 之后", () => {
    const { result } = renderBody(makeBody({ back }));
    const { container } = render(<div>{result.declarations}</div>);
    const dl = container.querySelector("dl") as HTMLElement;
    const note = screen.getByText("Untitled footnote.");
    expect(dl.compareDocumentPosition(note) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("含表的附录单独渲染在 appendices，带自己的 h2", () => {
    const { result } = renderBody(makeBody({ back }));
    render(<div>{result.appendices}</div>);
    expect(screen.getByRole("heading", { level: 2, name: "Appendix" })).toHaveAttribute(
      "id",
      "ft-app",
    );
  });

  it("没有文末附加时 declarations 与 appendices 为 null", () => {
    const { result } = renderBody(makeBody());
    expect(result.declarations).toBeNull();
    expect(result.appendices).toBeNull();
  });
});

describe("renderFullTextBody · 行内节点", () => {
  it("斜体、粗体、上下标照原样", () => {
    const { container } = renderBody(
      makeBody({
        sections: [
          section("s1", "R", [
            para(
              { kind: "style", style: "italic", children: [text("in vivo")] },
              { kind: "style", style: "bold", children: [text("n")] },
              { kind: "style", style: "sup", children: [text("2")] },
              { kind: "style", style: "sub", children: [text("1")] },
            ),
          ]),
        ],
      }),
    );
    expect(container.querySelector("em")?.textContent).toBe("in vivo");
    expect(container.querySelector("strong")?.textContent).toBe("n");
    expect(container.querySelector("sup")?.textContent).toBe("2");
    expect(container.querySelector("sub")?.textContent).toBe("1");
  });

  it("外链只放行 http(s)，新标签打开；doi: 转成 doi.org 链接", () => {
    renderBody(
      makeBody({
        sections: [
          section("s1", "R", [
            para(
              { kind: "link", href: "https://example.org/a", children: [text("site")] },
              { kind: "link", href: "doi:10.1/xyz", children: [text("doi")] },
              { kind: "link", href: "javascript:alert(1)", children: [text("bad")] },
            ),
          ]),
        ],
      }),
    );
    const site = screen.getByRole("link", { name: "site" });
    expect(site).toHaveAttribute("href", "https://example.org/a");
    expect(site).toHaveAttribute("target", "_blank");
    expect(screen.getByRole("link", { name: "doi" })).toHaveAttribute(
      "href",
      "https://doi.org/10.1/xyz",
    );
    expect(screen.queryByRole("link", { name: "bad" })).toBeNull();
    expect(screen.getByText("bad")).toBeInTheDocument();
  });

  it("行内公式按 MathML 渲染", () => {
    const { container } = renderBody(
      makeBody({
        sections: [
          section("s1", "R", [para("Let ", { kind: "math", mathml: "<math><mi>x</mi></math>" })]),
        ],
      }),
    );
    expect(container.querySelector("p math mi")?.textContent).toBe("x");
  });

  it("正文段落允许在超长单词（蛇形变量名、长网址）里断行，不把窄屏页面撑宽（PAP-89）", () => {
    const { container } = renderBody(
      makeBody({
        sections: [
          section("s1", "R", [para("primary_pathology_days_to_initial_pathologic_diagnosis")]),
        ],
      }),
    );
    expect(container.querySelector("p")?.className).toContain("break-words");
  });
});

describe("renderFullTextBody · 交叉引用", () => {
  const body = makeBody({
    sections: [
      section("s1", "Intro", [
        para("See ", cite("r1", "1"), " and ", cite("r1", "1"), "; also ", cite("r2", "2"), "."),
        para({ kind: "xref", refType: "fig", targets: ["f1"], children: [text("Figure 1")] }, " ", {
          kind: "xref",
          refType: "table",
          targets: ["missing"],
          children: [text("Table 9")],
        }),
        {
          kind: "figure",
          id: "f1",
          label: "Figure 1",
          caption: [],
          image: null,
          width: null,
          height: null,
        },
      ]),
    ],
    references: [reference("r1", "1"), reference("r2", "2")],
  });

  it("引用是指向参考文献条目的页内链接，读屏读作「参考文献 N」，每处引用有自己的锚点", () => {
    renderBody(body);
    const links = screen.getAllByRole("link", { name: "参考文献 1" });
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveAttribute("href", "#ref-r1");
    expect(links[0]?.id).not.toBe(links[1]?.id);
  });

  it("记下每条参考文献第一次被引用的位置（「回到正文」的默认去处）", () => {
    const { result } = renderBody(body);
    const [first] = screen.getAllByRole("link", { name: "参考文献 1" });
    expect(result.firstCiteByRef.r1).toBe(first?.id);
    expect(result.firstCiteByRef.r2).toBe(screen.getByRole("link", { name: "参考文献 2" }).id);
  });

  it("指向图表的引用跳到对应图表；目标不在本篇时只显示文字", () => {
    renderBody(body);
    expect(screen.getByRole("link", { name: "Figure 1" })).toHaveAttribute("href", "#ft-f1");
    expect(screen.queryByRole("link", { name: "Table 9" })).toBeNull();
    expect(within(document.body).getByText("Table 9")).toBeInTheDocument();
  });
});

describe("renderFullTextBody · 块", () => {
  const inSection = (...blocks: FullTextBody["blocks"]) =>
    makeBody({ sections: [section("s1", "R", blocks)] });

  it("有序 / 无序列表，列表项里照常渲染块", () => {
    renderBody(
      inSection(
        { kind: "list", ordered: true, items: [[para("first")], [para("second")]] },
        { kind: "list", ordered: false, items: [[para("dot")]] },
      ),
    );
    const [ordered, unordered] = screen.getAllByRole("list");
    expect(ordered?.tagName).toBe("OL");
    expect(within(ordered as HTMLElement).getAllByRole("listitem")).toHaveLength(2);
    expect(unordered?.tagName).toBe("UL");
  });

  it("引文框是 blockquote", () => {
    const { container } = renderBody(inSection({ kind: "quote", blocks: [para("Quoted.")] }));
    expect(container.querySelector("blockquote")?.textContent).toBe("Quoted.");
  });

  it("文本框带标题与锚点，可被交叉引用", () => {
    renderBody(
      inSection({
        kind: "box",
        id: "box1",
        title: [text("What is already known")],
        blocks: [para("Known.")],
      }),
    );
    const box = screen.getByRole("complementary", { name: "What is already known" });
    expect(box).toHaveAttribute("id", "ft-box1");
    expect(within(box).getByText("Known.")).toBeInTheDocument();
  });

  it("代码块原样保留空白；像 TeX 公式的加「公式 · LaTeX 源码」小标", () => {
    const { container } = renderBody(
      inSection(
        { kind: "code", text: "for i in 1..n:\n  x" },
        { kind: "code", text: "\\frac{a}{b} = c" },
      ),
    );
    const pres = container.querySelectorAll("pre");
    expect(pres[0]?.textContent).toBe("for i in 1..n:\n  x");
    expect(screen.getAllByText("公式 · LaTeX 源码")).toHaveLength(1);
  });

  it("行间公式：MathML 渲染 + 编号，带锚点", () => {
    const { container } = renderBody(
      inSection({
        kind: "formula",
        id: "e1",
        label: "(1)",
        mathml: '<math display="block"><mi>y</mi></math>',
        image: null,
        width: null,
        height: null,
      }),
    );
    const block = container.querySelector("#ft-e1");
    expect(block?.querySelector("math mi")?.textContent).toBe("y");
    expect(within(block as HTMLElement).getByText("(1)")).toBeInTheDocument();
  });

  it("只有图片的公式经图片接口取图，alt 写「公式 (N)」", () => {
    renderBody(
      inSection({
        kind: "formula",
        id: "e2",
        label: "(2)",
        mathml: null,
        image: "pone.e002.jpg",
        width: 120,
        height: 30,
      }),
    );
    expect(screen.getByRole("img", { name: "公式 (2)" })).toHaveAttribute(
      "src",
      "/api/papers/42/figures/pone.e002.jpg",
    );
  });
});

describe("renderFullTextBody · 图与表", () => {
  it("图：图注第一段是图题（也是 alt），图片走门户的同源地址", () => {
    renderBody(
      makeBody({
        sections: [
          section("s1", "R", [
            {
              kind: "figure",
              id: "g1",
              label: "Figure 1",
              caption: [para("Trial flow."), para("Details here.")],
              image: "g1.webp",
              width: 800,
              height: 600,
            },
          ]),
        ],
      }),
    );
    const figure = screen.getByRole("figure");
    expect(figure).toHaveAttribute("id", "ft-g1");
    expect(within(figure).getByRole("img", { name: "Trial flow." })).toHaveAttribute(
      "src",
      "/api/papers/42/figures/g1.webp",
    );
    expect(within(figure).getByText("Details here.")).toBeInTheDocument();
  });

  it("表：表题、列标题、行标题与表注都在；表格有锚点", () => {
    renderBody(
      makeBody({
        sections: [
          section("s1", "R", [
            {
              kind: "table",
              id: "t1",
              label: "Table 1",
              caption: [para("Parameters.")],
              head: [[{ content: [text("Par")], colspan: 1, rowspan: 1, header: true }]],
              body: [[{ content: [text("α")], colspan: 1, rowspan: 1, header: true }]],
              footnotes: [para("Est = estimated.")],
            },
          ]),
        ],
      }),
    );
    const table = screen.getByRole("table", { name: "Table 1 Parameters." });
    expect(table.closest("#ft-t1")).not.toBeNull();
    expect(screen.getByRole("columnheader", { name: "Par" })).toBeInTheDocument();
    expect(screen.getByRole("rowheader", { name: "α" })).toBeInTheDocument();
    expect(screen.getByText("Est = estimated.")).toBeInTheDocument();
  });
});
