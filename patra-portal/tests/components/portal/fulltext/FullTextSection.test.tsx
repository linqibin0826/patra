import { render, screen, within } from "@testing-library/react";
import { makeBody, makeFullText, para, section } from "@tests/fixtures/fulltext";
import { makePaperDetail } from "@tests/fixtures/paper";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { FullTextBlock } from "@/types/fulltext";
import "@testing-library/jest-dom/vitest";

vi.mock("@/lib/portal-api/fulltext", () => ({ fetchPublicationFullText: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }) }));

import { FullTextSection } from "@/components/portal/fulltext/FullTextSection";
import { fetchPublicationFullText } from "@/lib/portal-api/fulltext";

const fetchMock = vi.mocked(fetchPublicationFullText);

const readable = makePaperDetail({
  hasFullText: true,
  hasPdf: true,
  pdfSizeBytes: "2500000",
  pmcid: "PMC13583816",
  estimatedReadMin: 25,
});

async function renderSection(paper = readable) {
  return render(await FullTextSection({ paper }));
}

afterEach(() => {
  fetchMock.mockReset();
});

describe("FullTextSection · 有全文", () => {
  const figure: FullTextBlock = {
    kind: "figure",
    id: "g1",
    label: "Figure 1",
    caption: [para("Flow.")],
    image: "g1.webp",
    width: 10,
    height: 10,
  };

  it("全文区块：栏目小标、统计行、出处条、正文、参考文献、许可与出处", async () => {
    fetchMock.mockResolvedValue(
      makeFullText({ body: makeBody({ sections: [section("s1", "Introduction", [figure])] }) }),
    );
    await renderSection();

    const article = screen.getByRole("article", { name: "全文" });
    expect(article).toHaveAttribute("id", "fulltext");
    expect(
      within(article).getByText("5,701 词 · ≈ 25 分钟 · 1 图 · 2 篇参考文献"),
    ).toBeInTheDocument();
    expect(within(article).getByText("CC BY 4.0")).toBeInTheDocument();
    expect(
      within(article).getByText(
        "数据来源：美国国家医学图书馆（NLM）PubMed Central · PMC13583816 · 抓取于 2026-10-18",
      ),
    ).toBeInTheDocument();
    expect(within(article).getByRole("link", { name: /许可与出处/ })).toHaveAttribute(
      "href",
      "#license",
    );
    expect(
      within(article).getByRole("heading", { level: 2, name: "Introduction" }),
    ).toBeInTheDocument();
    expect(within(article).getByRole("list", { name: "参考文献" })).toBeInTheDocument();
    expect(within(article).getByRole("region", { name: "许可与出处" })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(readable.id);
  });

  it("作者手稿在出处条上多一枚「作者手稿」胶囊", async () => {
    fetchMock.mockResolvedValue(makeFullText({ isManuscript: true }));
    await renderSection();
    expect(screen.getAllByText("作者手稿").length).toBeGreaterThan(0);
  });

  it("许可为空时出处条写「许可见原文」", async () => {
    fetchMock.mockResolvedValue(makeFullText({ license: null }));
    await renderSection();
    expect(screen.getAllByText("许可见原文").length).toBeGreaterThan(0);
  });

  it("全文接口出错 → 「全文没能加载」，不抛到全局错误页", async () => {
    fetchMock.mockRejectedValue(new Error("全文加载失败：503"));
    await renderSection();
    expect(screen.getByRole("heading", { name: "全文没能加载" })).toBeInTheDocument();
  });

  it("详情说有全文、全文接口却没给正文 → 同样按加载失败处理", async () => {
    fetchMock.mockResolvedValue(null);
    await renderSection();
    expect(screen.getByRole("heading", { name: "全文没能加载" })).toBeInTheDocument();
  });
});

describe("FullTextSection · 没有可读正文", () => {
  it("不在开放获取子集：说明面板，不请求全文接口", async () => {
    await renderSection(makePaperDetail());
    const article = screen.getByRole("article", { name: "全文" });
    expect(within(article).getByRole("heading", { name: "暂无开放全文" })).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("只有 PDF：取全文接口的署名信息放进说明面板", async () => {
    fetchMock.mockResolvedValue(makeFullText({ body: null, wordCount: null }));
    await renderSection(
      makePaperDetail({ hasPdf: true, pdfSizeBytes: "334000", pmcid: "PMC7121018" }),
    );
    expect(screen.getByRole("heading", { name: "只有 PDF 版本" })).toBeInTheDocument();
    expect(screen.getByText("PDF 按原文许可转载，未作改动。")).toBeInTheDocument();
  });

  it("只有 PDF 但署名信息取不到：照常给下载，不显示署名表", async () => {
    fetchMock.mockRejectedValue(new Error("x"));
    await renderSection(
      makePaperDetail({ hasPdf: true, pdfSizeBytes: "334000", pmcid: "PMC7121018" }),
    );
    expect(screen.getByRole("link", { name: /下载 PDF/ })).toBeInTheDocument();
    expect(screen.queryByText("PDF 按原文许可转载，未作改动。")).toBeNull();
  });
});
