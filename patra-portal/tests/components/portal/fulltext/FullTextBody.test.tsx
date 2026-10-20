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

  it("文末附加（致谢等）渲染在正文之后", () => {
    const { container } = renderBody(
      makeBody({ sections: [section("s1", "Intro")], back: [section("ack", "Acknowledgements")] }),
    );
    expect(screen.getByRole("heading", { level: 2, name: "Acknowledgements" })).toBeInTheDocument();
    const html = container.innerHTML;
    expect(html.indexOf("Intro")).toBeLessThan(html.indexOf("Acknowledgements"));
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
