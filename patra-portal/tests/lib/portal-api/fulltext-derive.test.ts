import { makeBody, para, section } from "@tests/fixtures/fulltext";
import { makePaperDetail } from "@tests/fixtures/paper";
import { describe, expect, it } from "vitest";
import {
  deriveFullTextState,
  deriveFullTextStats,
  deriveToc,
  formatFetchedDate,
  formatFileSize,
  inlineText,
  isSupplementarySection,
  licenseLabel,
} from "@/lib/portal-api/fulltext-derive";
import type { FullTextBlock } from "@/types/fulltext";

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

  it("文献类型含 Retracted Publication → retracted，优先于其他判断", () => {
    const paper = makePaperDetail({
      pmcid: "PMC1",
      publicationTypes: ["Journal Article", "Retracted Publication"],
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
