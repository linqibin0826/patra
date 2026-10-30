import { render, screen } from "@testing-library/react";
import { makeBody, makeFullText, reference } from "@tests/fixtures/fulltext";
import { makePaperDetail } from "@tests/fixtures/paper";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { FullTextBlock } from "@/types/fulltext";
import "@testing-library/jest-dom/vitest";

vi.mock("@/lib/portal-api/fulltext", () => ({ fetchPublicationFullText: vi.fn() }));

import { FullTextFacts, PaperFacts } from "@/components/portal/paper-detail/PaperFacts";
import { fetchPublicationFullText } from "@/lib/portal-api/fulltext";

const fetchMock = vi.mocked(fetchPublicationFullText);

afterEach(() => {
  fetchMock.mockReset();
});

/** 事实条里每一格的「dt dd」文本。 */
function cells(): string[] {
  return screen
    .getAllByRole("term")
    .map((dt) => `${dt.textContent} ${dt.nextElementSibling?.textContent ?? ""}`);
}

describe("PaperFacts", () => {
  it("详情里有的四项：证据等级、被引、来源、原文阅读；没有「收藏」格", () => {
    render(<PaperFacts paper={makePaperDetail({ citationCount: 1234, estimatedReadMin: 34 })} />);
    expect(cells()).toEqual([
      expect.stringMatching(/^证据等级 /),
      "被引 1,234",
      "来源 PubMed",
      "原文阅读 ≈ 34 分钟",
    ]);
  });

  it("should_put_interaction_facts_after_full_text_facts", () => {
    render(
      <PaperFacts
        paper={makePaperDetail({ estimatedReadMin: null })}
        fullTextFacts={
          <div>
            <dt>篇幅</dt>
            <dd>1 词</dd>
          </div>
        }
        interactionFacts={
          <div>
            <dt>收藏</dt>
            <dd>2 人</dd>
          </div>
        }
      />,
    );
    expect(cells().map((c) => c.split(" ")[0])).toEqual([
      "证据等级",
      "被引",
      "来源",
      "篇幅",
      "收藏",
    ]);
  });

  it("没有阅读时长时不出「原文阅读」；全文三项插在「来源」之后", () => {
    render(
      <PaperFacts
        paper={makePaperDetail({ estimatedReadMin: null })}
        fullTextFacts={
          <div>
            <dt>篇幅</dt>
            <dd>1 词</dd>
          </div>
        }
      />,
    );
    expect(cells().map((c) => c.split(" ")[0])).toEqual(["证据等级", "被引", "来源", "篇幅"]);
  });
});

describe("FullTextFacts", () => {
  const figure: FullTextBlock = {
    kind: "figure",
    id: "f",
    label: "Figure 1",
    caption: [],
    image: null,
    width: null,
    height: null,
  };
  const table: FullTextBlock = {
    kind: "table",
    id: "t",
    label: "Table 1",
    caption: [],
    head: [],
    body: [],
    footnotes: [],
  };

  it("篇幅、图表、参考文献三格", async () => {
    fetchMock.mockResolvedValue(
      makeFullText({
        wordCount: 7633,
        body: makeBody({
          sections: [
            {
              id: "s1",
              title: [{ kind: "text", text: "Results" }],
              blocks: [...Array(11).fill(figure), ...Array(10).fill(table)],
              sections: [],
            },
          ],
          references: Array.from({ length: 56 }, (_, i) => reference(`r${i}`, `${i + 1}`)),
        }),
      }),
    );
    render(<dl>{await FullTextFacts({ paper: makePaperDetail({ hasFullText: true }) })}</dl>);
    expect(cells()).toEqual(["篇幅 7,633 词", "图表 11 图 · 10 表", "参考文献 56 篇"]);
  });

  it("没有图表时不出「图表」", async () => {
    fetchMock.mockResolvedValue(makeFullText());
    render(<dl>{await FullTextFacts({ paper: makePaperDetail({ hasFullText: true }) })}</dl>);
    expect(screen.queryByText("图表")).toBeNull();
  });

  it("已撤稿（即便标着有全文）不显示正文的篇幅、图表、参考文献，也不请求全文", async () => {
    fetchMock.mockResolvedValue(makeFullText());
    const node = await FullTextFacts({
      paper: makePaperDetail({ hasFullText: true, retracted: true }),
    });
    expect(node).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("全文请求失败时什么都不渲染", async () => {
    fetchMock.mockRejectedValue(new Error("boom"));
    const { container } = render(
      <dl>{await FullTextFacts({ paper: makePaperDetail({ hasFullText: true }) })}</dl>,
    );
    expect(container.querySelector("dt")).toBeNull();
  });
});
