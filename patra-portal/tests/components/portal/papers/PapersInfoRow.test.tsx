import { render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { formatSyncDate, PapersInfoRow } from "@/components/portal/papers/PapersInfoRow";
import { EMPTY_PAPER_QUERY } from "@/lib/portal-api/paper-search";
import type { PublicationFacets } from "@/types/portal";

vi.mock("@/lib/portal-api/publication-search", () => ({
  fetchPublicationSearch: vi.fn(),
  fetchPublicationFacets: vi.fn(),
}));

import { fetchPublicationFacets } from "@/lib/portal-api/publication-search";

const FACETS: PublicationFacets = {
  years: [],
  types: [],
  evidence: [],
  languages: [],
  openAccess: 0,
  total: 18807,
  lastSyncedAt: "2026-08-30T04:06:51Z",
};

describe("formatSyncDate", () => {
  it("按 Asia/Shanghai 格式化为 yyyy-MM-dd", () => {
    expect(formatSyncDate("2026-08-30T04:06:51Z")).toBe("2026-08-30");
    expect(formatSyncDate("2026-08-30T20:00:00Z")).toBe("2026-08-31");
  });
});

describe("PapersInfoRow", () => {
  beforeEach(() => vi.mocked(fetchPublicationFacets).mockReset());

  it("浏览态：共 N 篇 · 最近同步", async () => {
    vi.mocked(fetchPublicationFacets).mockResolvedValue(FACETS);
    const { container } = render(await PapersInfoRow({ query: EMPTY_PAPER_QUERY }));
    expect(container.firstChild).toHaveTextContent("共 18,807 篇");
    expect(container.firstChild).toHaveTextContent("最近同步 2026-08-30");
  });

  it("检索态：只显示命中数", async () => {
    vi.mocked(fetchPublicationFacets).mockResolvedValue({ ...FACETS, total: 1284 });
    const { container } = render(
      await PapersInfoRow({ query: { ...EMPTY_PAPER_QUERY, q: "GLP-1" } }),
    );
    expect(container.firstChild).toHaveTextContent("共 1,284 篇");
    expect(container.firstChild).not.toHaveTextContent("最近同步");
  });

  it("空库：共 0 篇且不显示同步时间", async () => {
    vi.mocked(fetchPublicationFacets).mockResolvedValue({
      ...FACETS,
      total: 0,
      lastSyncedAt: null,
    });
    const { container } = render(await PapersInfoRow({ query: EMPTY_PAPER_QUERY }));
    expect(container.firstChild).toHaveTextContent("共 0 篇");
    expect(container.firstChild).not.toHaveTextContent("最近同步");
  });
});
