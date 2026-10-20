import { makeFullText } from "@tests/fixtures/fulltext";
import { makePaperDetail } from "@tests/fixtures/paper";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/portal-api/fulltext", () => ({ fetchPublicationFullText: vi.fn() }));

import { FullTextCrumbStatus } from "@/components/portal/fulltext/FullTextCrumbStatus";
import { fetchPublicationFullText } from "@/lib/portal-api/fulltext";

const fetchMock = vi.mocked(fetchPublicationFullText);

afterEach(() => fetchMock.mockReset());

describe("FullTextCrumbStatus", () => {
  it("有可读全文：位置表含章节号与文末区块", async () => {
    fetchMock.mockResolvedValue(makeFullText());
    const el = await FullTextCrumbStatus({ paper: makePaperDetail({ hasFullText: true }) });
    expect(el.props.positions.map((p: { label: string }) => p.label)).toEqual([
      "01",
      "02",
      "参考文献",
      "文献档案",
    ]);
  });

  it("已撤稿时没有位置表（不显示「已读 %」），也不请求全文", async () => {
    fetchMock.mockResolvedValue(makeFullText());
    const el = await FullTextCrumbStatus({
      paper: makePaperDetail({ hasFullText: true, retracted: true }),
    });
    expect(el.props.positions).toEqual([]);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
