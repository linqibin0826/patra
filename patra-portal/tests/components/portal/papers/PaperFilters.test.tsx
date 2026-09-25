import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PaperFilters } from "@/components/portal/papers/PaperFilters";
import { PapersQueryProvider } from "@/components/portal/papers/PapersQueryProvider";
import { EMPTY_PAPER_QUERY } from "@/lib/portal-api/paper-search";
import { useBrowseFilterUiStore } from "@/store/browse-filter-ui";
import type { PaperSearchQuery, PublicationFacets } from "@/types/portal";

const mockPush = vi.fn<(url: string) => void>();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn() }),
}));

const FACETS: PublicationFacets = {
  years: [
    { value: "2026", count: 17520 },
    { value: "2025", count: 1268 },
    { value: "2024", count: 14 },
  ],
  types: Array.from({ length: 8 }, (_, i) => ({ value: `T${i}`, count: 100 - i })),
  evidence: [
    { value: "SYSTEMATIC_REVIEW", count: 264 },
    { value: "RANDOMIZED_CONTROLLED_TRIAL", count: 139 },
    { value: "COHORT_OR_CASE_CONTROL", count: 339 },
    { value: "NON_SYSTEMATIC_REVIEW", count: 2007 },
    { value: "CASE_REPORT", count: 278 },
    { value: "UNKNOWN", count: 15780 },
  ],
  languages: [{ value: "en", count: 18488 }],
  openAccess: 0,
  total: 18807,
  lastSyncedAt: "2026-08-30T04:06:51Z",
};

function renderFilters(
  query: PaperSearchQuery = EMPTY_PAPER_QUERY,
  facets: PublicationFacets = FACETS,
  venueNames: Record<string, string> = {},
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <PapersQueryProvider query={query}>
        <PaperFilters facets={facets} currentYear={2026} venueNames={venueNames} />
      </PapersQueryProvider>
    </QueryClientProvider>,
  );
}

const group = (name: string) => screen.getByRole("group", { name });

describe("PaperFilters", () => {
  beforeEach(() => {
    mockPush.mockClear();
    useBrowseFilterUiStore.setState({ sheetOpen: false });
  });

  it("近 3 年快捷项 → yearFrom=当年−2", () => {
    renderFilters();
    fireEvent.click(screen.getByRole("button", { name: "近 3 年" }));
    expect(mockPush).toHaveBeenCalledWith("/papers?yearFrom=2024");
  });

  it("逐年单选与快捷项互斥：近 3 年生效时点 2025 → 只剩 2025", () => {
    renderFilters({ ...EMPTY_PAPER_QUERY, yearFrom: 2024 });
    expect(screen.getByRole("button", { name: "近 3 年" })).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(within(group("发表年份")).getByRole("radio", { name: /2025/ }));
    expect(mockPush).toHaveBeenCalledWith("/papers?yearFrom=2025&yearTo=2025");
  });

  it("再点已选年份取消", () => {
    renderFilters({ ...EMPTY_PAPER_QUERY, yearFrom: 2025, yearTo: 2025 });
    fireEvent.click(within(group("发表年份")).getByRole("radio", { name: /2025/ }));
    expect(mockPush).toHaveBeenCalledWith("/papers");
  });

  it("类型默认前 6 项，展开后全部；已选项不被折叠", () => {
    const { unmount } = renderFilters();
    expect(within(group("文献类型")).getAllByRole("checkbox")).toHaveLength(6);
    fireEvent.click(screen.getByRole("button", { name: /更多类型/ }));
    expect(within(group("文献类型")).getAllByRole("checkbox")).toHaveLength(8);
    unmount();
    renderFilters({ ...EMPTY_PAPER_QUERY, type: ["T7"] });
    expect(within(group("文献类型")).getByRole("checkbox", { name: "T7" })).toBeChecked();
  });

  it("URL 类型与 facet 仅大小写不同：只显示一项且为已选，取消后清空", () => {
    renderFilters({ ...EMPTY_PAPER_QUERY, type: ["t7"] });
    const rows = within(group("文献类型")).getAllByRole("checkbox");
    expect(rows).toHaveLength(7);
    const t7 = within(group("文献类型")).getByRole("checkbox", { name: "T7" });
    expect(t7).toBeChecked();
    fireEvent.click(t7);
    expect(mockPush).toHaveBeenCalledWith("/papers");
  });

  it("精确定位未命中时勾选类型：退出精确模式，URL 不再带 pmid", () => {
    renderFilters({ ...EMPTY_PAPER_QUERY, pmid: "1" });
    fireEvent.click(screen.getByRole("checkbox", { name: "T0" }));
    expect(mockPush).toHaveBeenCalledWith("/papers?type=T0");
  });

  it("连点两个类型：两项都在第二次跳转里", () => {
    renderFilters();
    fireEvent.click(screen.getByRole("checkbox", { name: "T0" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "T1" }));
    expect(mockPush).toHaveBeenLastCalledWith("/papers?type=T0&type=T1");
  });

  it("证据等级 6 档，未分级弱化", () => {
    renderFilters();
    expect(within(group("证据等级")).getAllByRole("checkbox")).toHaveLength(6);
    expect(screen.getByText("未分级").closest("label")?.className).toMatch(/fg-3/);
  });

  it("OA 计数为 0 且未选中时整组不渲染；有计数时渲染", () => {
    const { unmount } = renderFilters();
    expect(screen.queryByRole("group", { name: "开放获取" })).not.toBeInTheDocument();
    unmount();
    renderFilters(EMPTY_PAPER_QUERY, { ...FACETS, openAccess: 5 });
    expect(group("开放获取")).toBeInTheDocument();
  });

  it("空组不渲染（无语言项且未选）", () => {
    renderFilters(EMPTY_PAPER_QUERY, { ...FACETS, languages: [] });
    expect(screen.queryByRole("group", { name: "语言" })).not.toBeInTheDocument();
  });

  it("已选期刊显示服务端刊名，取消勾选即移除", () => {
    renderFilters({ ...EMPTY_PAPER_QUERY, venue: ["123"] }, FACETS, { "123": "The Lancet" });
    const row = within(group("期刊")).getByRole("checkbox", { name: "The Lancet" });
    expect(row).toBeChecked();
    fireEvent.click(row);
    expect(mockPush).toHaveBeenCalledWith("/papers");
  });

  it("清除全部筛选保留排序", () => {
    renderFilters({ ...EMPTY_PAPER_QUERY, type: ["T0"], sort: "year" });
    fireEvent.click(screen.getByRole("button", { name: "清除全部筛选" }));
    expect(mockPush).toHaveBeenCalledWith("/papers?sort=year");
  });
});
