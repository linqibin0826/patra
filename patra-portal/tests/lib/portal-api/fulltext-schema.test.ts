import { makeFullText, para, section } from "@tests/fixtures/fulltext";
import { describe, expect, it } from "vitest";
import { fullTextSchema } from "@/lib/portal-api/fulltext-schema";

describe("fullTextSchema", () => {
  it("接受有全文的响应（含图、表、公式、列表、文本框、代码与全部行内节点）", () => {
    const response = makeFullText({
      body: {
        schemaVersion: 1,
        blocks: [
          { kind: "box", id: "b1", title: [{ kind: "text", text: "Key points" }], blocks: [] },
        ],
        sections: [
          section("s1", "Results", [
            para(
              { kind: "style", style: "italic", children: [{ kind: "text", text: "β" }] },
              {
                kind: "xref",
                refType: "bibr",
                targets: ["r1"],
                children: [{ kind: "text", text: "1" }],
              },
              { kind: "link", href: "https://doi.org/10.1/x", children: [] },
              { kind: "math", mathml: "<math><mi>x</mi></math>" },
              { kind: "break" },
            ),
            {
              kind: "figure",
              id: "f1",
              label: "Fig 1",
              caption: [para("Caption.")],
              image: "f1.webp",
              width: 1600,
              height: 2159,
            },
            {
              kind: "table",
              id: "t1",
              label: null,
              caption: [],
              head: [[{ content: [], colspan: 1, rowspan: 1, header: true }]],
              body: [],
              footnotes: [],
            },
            {
              kind: "formula",
              id: null,
              label: "(1)",
              mathml: null,
              image: "e1.gif",
              width: null,
              height: null,
            },
            { kind: "list", ordered: true, items: [[para("one")]] },
            { kind: "quote", blocks: [para("q")] },
            { kind: "code", text: "x = 1" },
          ]),
        ],
        back: [],
        references: [{ id: "r1", label: null, content: [], pmid: "1", doi: null }],
        wordCount: 10,
      },
    });

    expect(fullTextSchema.parse(response)).toEqual(response);
  });

  it("接受只有 PDF 的响应：body、字数、阅读时长为 null，许可为 null", () => {
    const response = makeFullText({
      body: null,
      wordCount: null,
      estimatedReadMin: null,
      license: null,
      isManuscript: null,
    });

    expect(fullTextSchema.parse(response)).toEqual(response);
  });

  it("拒绝白名单外的块类型", () => {
    const response = makeFullText();
    const bad = {
      ...response,
      body: { ...response.body, blocks: [{ kind: "html", html: "<script>" }] },
    };

    expect(fullTextSchema.safeParse(bad).success).toBe(false);
  });
});
