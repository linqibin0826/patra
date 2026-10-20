import { makeBody, para, section } from "@tests/fixtures/fulltext";
import { makePaperDetail } from "@tests/fixtures/paper";
import { describe, expect, it } from "vitest";
import {
  deriveFullTextState,
  deriveFullTextStats,
  deriveToc,
  figureLabel,
  figureLayout,
  formatFetchedDate,
  formatFileSize,
  inlineText,
  isSupplementarySection,
  licenseLabel,
  numericColumns,
} from "@/lib/portal-api/fulltext-derive";
import type { FullTextBlock, FullTextTableCell } from "@/types/fulltext";

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
  it("一级章节 + 有标题的二级章节，文末附加跟在后面；无标题章节与补充材料不进目录", () => {
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
      { id: "ft-s1", label: "Introduction", children: [] },
      {
        id: "ft-s2",
        label: "Methods",
        children: [{ id: "ft-s2-1", label: "Design", children: [] }],
      },
      { id: "ft-ack", label: "Acknowledgements", children: [] },
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
});
