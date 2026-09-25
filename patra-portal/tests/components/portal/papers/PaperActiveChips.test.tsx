import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PaperActiveChips } from "@/components/portal/papers/PaperActiveChips";
import { PapersQueryProvider } from "@/components/portal/papers/PapersQueryProvider";
import { EMPTY_PAPER_QUERY } from "@/lib/portal-api/paper-search";
import { queryKeys } from "@/lib/query-keys";
import type { PaperSearchQuery } from "@/types/portal";

const mockPush = vi.fn<(url: string) => void>();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: mockPush, replace: vi.fn() }) }));

function renderChips(
  query: PaperSearchQuery,
  venueNames: Record<string, string> = {},
  client = new QueryClient(),
) {
  return render(
    <QueryClientProvider client={client}>
      <PapersQueryProvider query={query}>
        <PaperActiveChips currentYear={2026} venueNames={venueNames} />
      </PapersQueryProvider>
    </QueryClientProvider>,
  );
}

describe("PaperActiveChips", () => {
  beforeEach(() => {
    mockPush.mockClear();
  });

  it("浏览态不渲染", () => {
    const { container } = renderChips(EMPTY_PAPER_QUERY);
    expect(container).toBeEmptyDOMElement();
  });

  it("期刊刊名：服务端名优先，其次候选缓存里的刊名，最后回退 期刊 #id", () => {
    const client = new QueryClient();
    client.setQueryData(queryKeys.venueSuggest("ce"), [
      { id: "456", name: "Cell", abbr: "Cell" },
      { id: "123", name: "候选里的旧名", abbr: "" },
    ]);
    renderChips(
      { ...EMPTY_PAPER_QUERY, venue: ["123", "456", "789"] },
      { "123": "The Lancet" },
      client,
    );
    expect(screen.getByText("The Lancet")).toBeInTheDocument();
    expect(screen.getByText("Cell")).toBeInTheDocument();
    expect(screen.getByText("期刊 #789")).toBeInTheDocument();
  });

  it("移除关键词 chip 保留其余条件", () => {
    renderChips({ ...EMPTY_PAPER_QUERY, q: "GLP-1", yearFrom: 2024 });
    expect(screen.getByText("近 3 年")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "移除 GLP-1" }));
    expect(mockPush).toHaveBeenCalledWith("/papers?yearFrom=2024");
  });

  it("清除全部保留排序", () => {
    renderChips({ ...EMPTY_PAPER_QUERY, q: "GLP-1", sort: "year" });
    fireEvent.click(screen.getByRole("button", { name: "清除全部" }));
    expect(mockPush).toHaveBeenCalledWith("/papers?sort=year");
  });

  it("移动端单行横向滚动", () => {
    renderChips({ ...EMPTY_PAPER_QUERY, q: "GLP-1" });
    expect(screen.getByRole("list", { name: "已选筛选条件" }).className).toMatch(
      /max-md:overflow-x-auto/,
    );
  });
});
