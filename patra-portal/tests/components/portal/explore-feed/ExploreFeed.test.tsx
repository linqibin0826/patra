import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { makePaper } from "@tests/fixtures/paper";
import { ExploreFeed } from "@/components/portal/explore-feed";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/lib/portal-api/publications", () => ({
  fetchFeed: vi.fn(),
  fetchPublicationDetail: vi.fn(),
}));

import { fetchFeed } from "@/lib/portal-api/publications";

describe("ExploreFeed 查看更多", () => {
  it("有文献时底部出现「查看更多文献」→ /papers", async () => {
    vi.mocked(fetchFeed).mockResolvedValue({
      page: 1,
      pageSize: 14,
      total: 1,
      totalPages: 1,
      items: [makePaper()],
    });
    render(await ExploreFeed({ tab: "recent" }));
    expect(screen.getByRole("link", { name: /查看更多文献/ })).toHaveAttribute("href", "/papers");
  });

  it("空流 / 取数失败时不显示", async () => {
    vi.mocked(fetchFeed).mockResolvedValue({
      page: 1,
      pageSize: 14,
      total: 0,
      totalPages: 0,
      items: [],
    });
    const { unmount } = render(await ExploreFeed({ tab: "recent" }));
    expect(screen.queryByRole("link", { name: /查看更多文献/ })).not.toBeInTheDocument();
    unmount();
    vi.mocked(fetchFeed).mockRejectedValue(new Error("boom"));
    render(await ExploreFeed({ tab: "recent" }));
    expect(screen.queryByRole("link", { name: /查看更多文献/ })).not.toBeInTheDocument();
  });
});
