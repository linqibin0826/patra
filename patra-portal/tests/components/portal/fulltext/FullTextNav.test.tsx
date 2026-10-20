import { render, screen } from "@testing-library/react";
import { makeBody, makeFullText, section } from "@tests/fixtures/fulltext";
import { makePaperDetail } from "@tests/fixtures/paper";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("@/lib/portal-api/fulltext", () => ({ fetchPublicationFullText: vi.fn() }));

import { FullTextTocButton, FullTextTocCard } from "@/components/portal/fulltext/FullTextNav";
import { fetchPublicationFullText } from "@/lib/portal-api/fulltext";

const fetchMock = vi.mocked(fetchPublicationFullText);
const readable = makePaperDetail({
  hasFullText: true,
  hasPdf: true,
  pdfSizeBytes: "2500000",
  estimatedReadMin: 25,
});

afterEach(() => fetchMock.mockReset());

describe("FullTextTocCard / FullTextTocButton", () => {
  it("有带标题的章节时出目录卡与目录按钮", async () => {
    fetchMock.mockResolvedValue(makeFullText());
    render(
      <>
        {await FullTextTocCard({ paper: readable })}
        {await FullTextTocButton({ paper: readable })}
      </>,
    );
    expect(screen.getByRole("navigation", { name: "目录" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "目录" })).toBeInTheDocument();
  });

  it("正文不分节：都不出现", async () => {
    fetchMock.mockResolvedValue(
      makeFullText({ body: makeBody({ sections: [section("s1", "")] }) }),
    );
    expect(await FullTextTocCard({ paper: readable })).toBeNull();
    expect(await FullTextTocButton({ paper: readable })).toBeNull();
  });

  it("没有全文或全文取不到：都不出现，也不报错", async () => {
    expect(await FullTextTocCard({ paper: makePaperDetail({ hasPdf: true }) })).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
    fetchMock.mockRejectedValue(new Error("503"));
    expect(await FullTextTocCard({ paper: readable })).toBeNull();
  });

  it("已撤稿（即便标着有全文）：不出目录卡与目录按钮，也不请求全文", async () => {
    fetchMock.mockResolvedValue(makeFullText());
    const retracted = makePaperDetail({ hasFullText: true, retracted: true });
    expect(await FullTextTocCard({ paper: retracted })).toBeNull();
    expect(await FullTextTocButton({ paper: retracted })).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
