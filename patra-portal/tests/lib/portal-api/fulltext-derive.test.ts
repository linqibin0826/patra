import { makeBody, para, reference, section, text } from "@tests/fixtures/fulltext";
import { makePaperDetail } from "@tests/fixtures/paper";
import { describe, expect, it } from "vitest";
import {
  deriveFullTextState,
  deriveFullTextStats,
  deriveToc,
  deriveTocTail,
  figureLabel,
  figureLayout,
  formatFetchedDate,
  formatFileSize,
  inlineText,
  isCitationSup,
  isSupplementarySection,
  licenseLabel,
  numberSections,
  numericColumns,
  resolveColumns,
  splitBack,
} from "@/lib/portal-api/fulltext-derive";
import type { FullTextBlock, FullTextInline, FullTextTableCell } from "@/types/fulltext";

describe("deriveFullTextState", () => {
  it("有正文 → full", () => {
    expect(deriveFullTextState(makePaperDetail({ hasFullText: true, hasPdf: true }))).toBe("full");
  });

  it("没有正文但能下 PDF → pdf-only", () => {
    expect(deriveFullTextState(makePaperDetail({ hasPdf: true, pmcid: "PMC7121018" }))).toBe(
      "pdf-only",
    );
  });

  it("什么都没有但有 PMCID → pmc-only（PMC 收录但不能转载）", () => {
    expect(deriveFullTextState(makePaperDetail({ pmcid: "PMC12879293" }))).toBe("pmc-only");
  });

  it("什么都没有也没有 PMCID → not-oa", () => {
    expect(deriveFullTextState(makePaperDetail())).toBe("not-oa");
  });

  it("后端标了撤稿 → retracted，优先于其他判断（PMC 标撤稿时文献类型里可能还没有撤稿）", () => {
    const paper = makePaperDetail({
      pmcid: "PMC12829825",
      publicationTypes: ["Journal Article"],
      retracted: true,
    });
    expect(deriveFullTextState(paper)).toBe("retracted");
  });
});

describe("formatFileSize", () => {
  it("按十进制换算，MB 保留一位小数", () => {
    expect(formatFileSize("43892198")).toBe("43.9 MB");
    expect(formatFileSize("2500000")).toBe("2.5 MB");
  });

  it("不到 1 MB 用 KB 取整", () => {
    expect(formatFileSize("334000")).toBe("334 KB");
  });

  it("空值或非数字 → null（不显示大小）", () => {
    expect(formatFileSize(null)).toBeNull();
    expect(formatFileSize("abc")).toBeNull();
  });
});

describe("formatFetchedDate", () => {
  it("按北京时间取日期", () => {
    expect(formatFetchedDate("2026-10-18T05:12:00Z")).toBe("2026-10-18");
    expect(formatFetchedDate("2026-10-18T17:30:00Z")).toBe("2026-10-19");
  });

  it("空值 → null", () => {
    expect(formatFetchedDate(null)).toBeNull();
  });
});

describe("licenseLabel", () => {
  it("许可代码后接许可地址里的版本号", () => {
    expect(
      licenseLabel({ code: "CC BY", url: "https://creativecommons.org/licenses/by/4.0/" }),
    ).toBe("CC BY 4.0");
    expect(
      licenseLabel({ code: "CC0", url: "https://creativecommons.org/publicdomain/zero/1.0/" }),
    ).toBe("CC0 1.0");
  });

  it("没有地址时只写代码；许可为空写「许可见原文」", () => {
    expect(licenseLabel({ code: "CC BY-NC", url: null })).toBe("CC BY-NC");
    expect(licenseLabel(null)).toBe("许可见原文");
  });
});

describe("inlineText", () => {
  it("拼出行内节点的纯文本，换行不计", () => {
    expect(
      inlineText([
        { kind: "text", text: "2. " },
        { kind: "style", style: "italic", children: [{ kind: "text", text: "Methods" }] },
        { kind: "break" },
      ]),
    ).toBe("2. Methods");
  });

  it("行内公式取其中的文字（目录里不留空括号）", () => {
    expect(
      inlineText([
        { kind: "text", text: "Densities of N1 TANs (" },
        { kind: "math", mathml: "<math><msub><mi>N</mi><mn>1</mn></msub><mo>&lt;</mo></math>" },
        { kind: "text", text: ")" },
      ]),
    ).toBe("Densities of N1 TANs (N1<)");
  });
});

describe("isSupplementarySection", () => {
  it("补充材料一节按标题认出（职责边界 E：整节不渲染）", () => {
    for (const title of [
      "Supplementary Material",
      "Supplementary Materials",
      "Supplemental material",
      "Supplementary data",
      "Supporting Information",
      "Electronic supplementary material",
    ]) {
      expect(isSupplementarySection(section("x", title)), title).toBe(true);
    }
  });

  it("标题里只是提到 supporting 的普通章节不算", () => {
    expect(isSupplementarySection(section("x", "Optimization design of supporting ring"))).toBe(
      false,
    );
    expect(isSupplementarySection(section("x", ""))).toBe(false);
  });
});

describe("deriveToc", () => {
  it("一级章节 + 有标题的二级章节，带序号；文末附加不进目录；无标题章节与补充材料不进目录", () => {
    const body = makeBody({
      sections: [
        section("s1", "Introduction"),
        section("s2", "Methods", [], [section("s2-1", "Design"), section("s2-2", "")]),
        section("s3", ""),
        section("s4", "Supplementary Material"),
      ],
      back: [section("ack", "Acknowledgements"), section("fn", "")],
    });

    expect(deriveToc(body)).toEqual([
      { id: "ft-s1", number: "01", label: "Introduction", children: [] },
      {
        id: "ft-s2",
        number: "02",
        label: "Methods",
        children: [{ id: "ft-s2-1", number: "2.1", label: "Design", children: [] }],
      },
    ]);
  });

  it("标题自带序号时目录用原文序号、标签去掉序号", () => {
    const body = makeBody({
      sections: [section("s1", "1. Introduction"), section("s2", "2. Methods")],
    });

    expect(deriveToc(body).map((e) => [e.number, e.label])).toEqual([
      ["01", "Introduction"],
      ["02", "Methods"],
    ]);
  });

  it("正文没有带标题的一级章节 → 空目录（不显示目录卡与目录按钮）", () => {
    const body = makeBody({
      sections: [section("s1", "", [para("Commentary.")])],
      back: [section("ack", "Acknowledgements")],
    });

    expect(deriveToc(body)).toEqual([]);
  });
});

describe("numberSections", () => {
  it("没有自带序号时按位置编号：一级补两位，二级 4.1 起，三级不编", () => {
    const subs = [1, 2, 3, 4, 5].map((n) => section(`s4-${n}`, `Sub ${n}`));
    subs[0] = section("s4-1", "Sub 1", [], [section("s4-1-1", "Deep")]);
    const sections = [
      section("s1", "Introduction"),
      section("s2", "Related"),
      section("s3", "Methods"),
      section("s4", "Results", [], subs),
      section("s5", "Conclusion"),
    ];

    const numbered = numberSections(sections);

    expect(["s1", "s2", "s3", "s4", "s5"].map((id) => numbered[id]?.label)).toEqual([
      "01",
      "02",
      "03",
      "04",
      "05",
    ]);
    expect(numbered["s4-5"]?.label).toBe("4.5");
    expect(numbered["s4-1-1"]).toBeUndefined();
    expect(inlineText(numbered.s1?.title ?? [])).toBe("Introduction");
  });

  it("一、二级标题全带序号时用原文序号，并从标题文字里拿掉", () => {
    const numbered = numberSections([
      section("s1", "1. Introduction"),
      section("s2", "2 Methods", [], [section("s2-1", "2.1 Data"), section("s2-2", "2.2. Model")]),
    ]);

    expect(numbered.s1).toEqual({ label: "01", title: [{ kind: "text", text: "Introduction" }] });
    expect(numbered["s2-1"]?.label).toBe("2.1");
    expect(inlineText(numbered["s2-1"]?.title ?? [])).toBe("Data");
    expect(numbered["s2-2"]?.label).toBe("2.2");
  });

  it("只有部分带序号时全部按位置编号，标题文字不动", () => {
    const numbered = numberSections([section("s1", "1. Introduction"), section("s2", "Methods")]);

    expect(numbered.s1?.label).toBe("01");
    expect(inlineText(numbered.s1?.title ?? [])).toBe("1. Introduction");
    expect(numbered.s2?.label).toBe("02");
  });

  it("无标题章节、补充材料不编号也不占序号", () => {
    const numbered = numberSections([
      section("s0", ""),
      section("s1", "Introduction"),
      section("sup", "Supplementary Material"),
      section("s2", "Methods"),
    ]);

    expect(numbered.s0).toBeUndefined();
    expect(numbered.sup).toBeUndefined();
    expect(numbered.s2?.label).toBe("02");
  });
});

describe("deriveTocTail", () => {
  it("声明数 = 有标题的文末节（含附录），参考文献数 = 条数", () => {
    const fig: FullTextBlock = {
      kind: "figure",
      id: "f1",
      label: "Figure A1",
      caption: [],
      image: null,
      width: null,
      height: null,
    };
    const back = [
      ...Array.from({ length: 8 }, (_, i) => section(`b${i}`, `Decl ${i}`, [para("x")])),
      section("app", "Appendix", [fig]),
      section("fn", "", [para("footnote")]),
    ];
    const references = Array.from({ length: 56 }, (_, i) => reference(`r${i}`, `${i + 1}`));

    expect(deriveTocTail(makeBody({ back, references }))).toEqual({
      declarations: 9,
      references: 56,
    });
  });
});

describe("deriveTocTail · 声明区不存在时", () => {
  it("只有附录或只有无标题节时，不列「声明与附注」（页面上没有 #declarations）", () => {
    const table: FullTextBlock = {
      kind: "table",
      id: "t",
      label: "Table A1",
      caption: [],
      head: [],
      body: [],
      footnotes: [],
    };
    expect(
      deriveTocTail(makeBody({ back: [section("app", "Appendix", [table])] })).declarations,
    ).toBe(0);
    expect(deriveTocTail(makeBody({ back: [section("fn", "", [para("x")])] })).declarations).toBe(
      0,
    );
  });
});

describe("isCitationSup", () => {
  const bibr = (t: string): FullTextInline => ({
    kind: "xref",
    refType: "bibr",
    targets: [`r${t}`],
    children: [text(t)],
  });

  it("sup 里只有参考文献引用（可夹逗号、连字符、空白）→ true", () => {
    expect(isCitationSup({ kind: "style", style: "sup", children: [bibr("8")] })).toBe(true);
    expect(
      isCitationSup({
        kind: "style",
        style: "sup",
        children: [bibr("3"), text("–"), bibr("5"), text(", "), bibr("9")],
      }),
    ).toBe(true);
  });

  it("sup 里有图引用或普通文字、或不是 sup → false", () => {
    const fig: FullTextInline = {
      kind: "xref",
      refType: "fig",
      targets: ["f1"],
      children: [text("1")],
    };
    expect(isCitationSup({ kind: "style", style: "sup", children: [fig] })).toBe(false);
    expect(isCitationSup({ kind: "style", style: "sup", children: [text("2")] })).toBe(false);
    expect(isCitationSup({ kind: "style", style: "italic", children: [bibr("8")] })).toBe(false);
    expect(isCitationSup(bibr("8"))).toBe(false);
  });
});

describe("splitBack", () => {
  it("有标题的进声明区，无标题的另放，含图表的算附录，补充材料排除", () => {
    const table: FullTextBlock = {
      kind: "table",
      id: "t1",
      label: "Table A1",
      caption: [],
      head: [],
      body: [],
      footnotes: [],
    };
    const back = [
      section("ack", "Acknowledgments", [para("Thanks.")]),
      section("fn", "", [para("Footnote.")]),
      section("app", "Appendix", [], [section("app-1", "Extra", [table])]),
      section("sup", "Supplementary Material"),
    ];

    const split = splitBack(back);

    expect(split.declarations.map((s) => s.id)).toEqual(["ack"]);
    expect(split.untitled.map((s) => s.id)).toEqual(["fn"]);
    expect(split.appendices.map((s) => s.id)).toEqual(["app"]);
  });
});

describe("deriveFullTextStats", () => {
  it("统计图、表（含嵌在文本框和列表里的）与参考文献数，补充材料一节不计", () => {
    const fig: FullTextBlock = {
      kind: "figure",
      id: null,
      label: null,
      caption: [],
      image: null,
      width: null,
      height: null,
    };
    const table: FullTextBlock = {
      kind: "table",
      id: null,
      label: null,
      caption: [],
      head: [],
      body: [],
      footnotes: [],
    };
    const body = makeBody({
      blocks: [{ kind: "box", id: null, title: [], blocks: [fig] }],
      sections: [
        section("s1", "Results", [fig, table, { kind: "list", ordered: false, items: [[table]] }]),
        section("s9", "Supplementary Material", [fig]),
      ],
    });

    expect(deriveFullTextStats(body)).toEqual({ figures: 2, tables: 2, references: 2 });
  });
});

describe("figureLayout", () => {
  it("按宽高比分四类（PMID 41575322 的图 1、3、2、7）", () => {
    expect(figureLayout(733, 558)).toBe("text");
    expect(figureLayout(750, 420)).toBe("wide");
    expect(figureLayout(750, 280)).toBe("wide-zoom");
    expect(figureLayout(752, 952)).toBe("portrait");
  });

  it("区间左闭右开：0.8 → text，1.5 → wide，2.4 → wide，2.41 → wide-zoom", () => {
    expect(figureLayout(80, 100)).toBe("text");
    expect(figureLayout(150, 100)).toBe("wide");
    expect(figureLayout(240, 100)).toBe("wide");
    expect(figureLayout(241, 100)).toBe("wide-zoom");
  });

  it("宽或高缺失、为 0 → text 兜底", () => {
    expect(figureLayout(null, 100)).toBe("text");
    expect(figureLayout(100, null)).toBe("text");
    expect(figureLayout(100, 0)).toBe("text");
  });
});

describe("resolveColumns", () => {
  it("按 colspan 累加列位，并跳过上方 rowspan 仍占着的列", () => {
    const span = (colspan: number, rowspan: number) => ({ colspan, rowspan });
    expect(
      resolveColumns([
        [span(1, 2), span(2, 1), span(1, 1)],
        [span(1, 1), span(1, 1), span(1, 1)],
      ]),
    ).toEqual([
      [0, 1, 3],
      [1, 2, 3],
    ]);
  });
});

describe("figureLabel", () => {
  it("去掉原文标签末尾的句点，其余照原文", () => {
    expect(figureLabel("Figure 1.")).toBe("Figure 1");
    expect(figureLabel("Table 10")).toBe("Table 10");
    expect(figureLabel(null)).toBeNull();
  });
});

function cell(value: string, colspan = 1): FullTextTableCell {
  return { content: [{ kind: "text", text: value }], colspan, rowspan: 1, header: false };
}

describe("numericColumns", () => {
  it("一列里过半是数字才右对齐；作者列不算", () => {
    const head = [[cell("Study"), cell("Accuracy"), cell("Precision")]];
    const body = [
      [cell("Tsai et al."), cell("91%"), cell("—")],
      [cell("Nopour (2024)"), cell("93.43%"), cell("0.9239")],
    ];
    expect(numericColumns(head, body)).toEqual([false, true, true]);
  });

  it("均值 ± 标准差、带区间的比值、负号都算数字", () => {
    const body = [[cell("12.3 ± 4.5")], [cell("0.82 (0.71–0.93)")], [cell("−1.2")]];
    expect(numericColumns([], body)).toEqual([true]);
  });

  it("跨列单元格不参与判定", () => {
    const body = [[cell("Subtotal", 2)], [cell("A"), cell("1.0")], [cell("B"), cell("2.0")]];
    expect(numericColumns([], body)).toEqual([false, true]);
  });

  it("空表 → 空数组", () => {
    expect(numericColumns([], [])).toEqual([]);
  });

  it("跨行单元格占住的列位不算到下一行：下一行的单元格向右顺延", () => {
    const group = { ...cell("Group"), rowspan: 2 };
    const body = [
      [group, cell("A"), cell("12"), cell("34")],
      [cell("B"), cell("56"), cell("78")],
    ];
    expect(numericColumns([], body)).toEqual([false, false, true, true]);
  });
});
