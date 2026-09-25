import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { JournalFilters } from "@/components/portal/journals/JournalFilters";
import { JournalsQueryProvider } from "@/components/portal/journals/JournalsQueryProvider";
import { useBrowseFilterUiStore } from "@/store/browse-filter-ui";
import type { VenueBrowseFacets, VenueBrowseQuery } from "@/types/portal";

const mockPush = vi.fn<(url: string) => void>();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn() }),
}));

const baseQuery: VenueBrowseQuery = {
  q: "",
  sort: "if",
  page: 3,
  subject: [],
  jcr: [],
  cas: [],
  casTop: false,
  oa: false,
  doaj: false,
  country: [],
};

const baseFacets: VenueBrowseFacets = {
  subject: [
    { value: "医学", count: 50 },
    { value: "生物", count: 20 },
    { value: "化学", count: 0 },
  ],
  jcr: [
    { value: "Q1", count: 30 },
    { value: "Q2", count: 15 },
  ],
  cas: [
    { value: "1区", count: 25 },
    { value: "2区", count: 10 },
  ],
  country: [
    { value: "US", count: 40 },
    { value: "UK", count: 8 },
  ],
  casTop: 12,
  oa: 35,
  doaj: 18,
};

function renderFilters(query: VenueBrowseQuery = baseQuery) {
  return render(
    <JournalsQueryProvider query={query}>
      <JournalFilters facets={baseFacets} />
    </JournalsQueryProvider>,
  );
}

describe("JournalFilters", () => {
  beforeEach(() => {
    mockPush.mockClear();
    useBrowseFilterUiStore.setState({ sheetOpen: false });
  });

  describe("facet 勾选", () => {
    it("勾选 JCR Q1 → router.push 含 jcr=Q1 且 page 归 1", () => {
      renderFilters(baseQuery);
      const q1Checkbox = screen.getByRole("checkbox", { name: /Q1/i });
      fireEvent.click(q1Checkbox);
      expect(mockPush).toHaveBeenCalledTimes(1);
      const url = mockPush.mock.calls[0]?.[0];
      expect(url).toContain("jcr=Q1");
      expect(url).not.toContain("page=");
    });

    it("已勾选的 JCR Q1 再次勾选 → 移除 Q1", () => {
      renderFilters({ ...baseQuery, jcr: ["Q1"] });
      const q1Checkbox = screen.getByRole("checkbox", { name: /Q1/i });
      fireEvent.click(q1Checkbox);
      expect(mockPush).toHaveBeenCalledTimes(1);
      const url = mockPush.mock.calls[0]?.[0];
      expect(url).not.toContain("jcr=Q1");
    });

    it("勾选学科 → URL 含 subject 值", () => {
      renderFilters(baseQuery);
      const checkbox = screen.getByRole("checkbox", { name: /医学/i });
      fireEvent.click(checkbox);
      const url = mockPush.mock.calls[0]?.[0];
      expect(url).toContain("subject=");
      expect(decodeURIComponent(url as string)).toContain("医学");
    });
  });

  describe("count 渲染", () => {
    it("渲染 Q1 的 count（30）", () => {
      renderFilters(baseQuery);
      expect(screen.getByText("30")).toBeInTheDocument();
    });

    it("count=0 的项（化学）灰显（has is-zero class 或 opacity 样式）", () => {
      renderFilters(baseQuery);
      // 化学存在于 DOM 中（不隐藏）
      const label = screen.getByText("化学").closest("label");
      expect(label).not.toBeNull();
      // 灰显：含 is-zero class 或 opacity 相关 class
      expect((label as HTMLElement).className).toMatch(/is-zero|opacity/);
    });
  });

  describe("学科本地搜索", () => {
    it("本地搜索过滤后只显示匹配项，不触发 router", () => {
      renderFilters(baseQuery);
      const searchInput = screen.getByPlaceholderText(/搜索学科/i);
      fireEvent.change(searchInput, { target: { value: "医" } });
      // 医学可见，生物和化学不可见
      expect(screen.getByText("医学")).toBeInTheDocument();
      expect(screen.queryByText("生物")).not.toBeInTheDocument();
      expect(screen.queryByText("化学")).not.toBeInTheDocument();
      // 未触发 router
      expect(mockPush).not.toHaveBeenCalled();
    });

    it("清空搜索后恢复所有项", () => {
      renderFilters(baseQuery);
      const searchInput = screen.getByPlaceholderText(/搜索学科/i);
      fireEvent.change(searchInput, { target: { value: "医" } });
      fireEvent.change(searchInput, { target: { value: "" } });
      expect(screen.getByText("生物")).toBeInTheDocument();
      expect(screen.getByText("化学")).toBeInTheDocument();
    });
  });

  describe("国家本地搜索", () => {
    it("本地搜索过滤后只显示匹配项，不触发 router", () => {
      renderFilters(baseQuery);
      const searchInput = screen.getByPlaceholderText(/搜索国家/i);
      fireEvent.change(searchInput, { target: { value: "US" } });
      expect(screen.getByText("US")).toBeInTheDocument();
      expect(screen.queryByText("UK")).not.toBeInTheDocument();
      expect(mockPush).not.toHaveBeenCalled();
    });

    it("清空搜索后恢复所有国家项", () => {
      renderFilters(baseQuery);
      const searchInput = screen.getByPlaceholderText(/搜索国家/i);
      fireEvent.change(searchInput, { target: { value: "US" } });
      fireEvent.change(searchInput, { target: { value: "" } });
      expect(screen.getByText("US")).toBeInTheDocument();
      expect(screen.getByText("UK")).toBeInTheDocument();
    });
  });

  describe("布尔开关", () => {
    it("切换「仅开放获取」→ URL 含 oa=true", () => {
      renderFilters(baseQuery);
      const oaCheckbox = screen.getByRole("checkbox", { name: /仅开放获取/i });
      fireEvent.click(oaCheckbox);
      expect(mockPush).toHaveBeenCalledTimes(1);
      const url = mockPush.mock.calls[0]?.[0];
      expect(url).toContain("oa=true");
    });

    it("oa=true 时再次切换 → URL 不含 oa", () => {
      renderFilters({ ...baseQuery, oa: true });
      const oaCheckbox = screen.getByRole("checkbox", { name: /仅开放获取/i });
      fireEvent.click(oaCheckbox);
      const url = mockPush.mock.calls[0]?.[0];
      expect(url).not.toContain("oa=true");
    });

    it("切换「仅 Top 期刊」→ URL 含 casTop=true", () => {
      renderFilters(baseQuery);
      const topCheckbox = screen.getByRole("checkbox", { name: /仅 Top 期刊/i });
      fireEvent.click(topCheckbox);
      const url = mockPush.mock.calls[0]?.[0];
      expect(url).toContain("casTop=true");
    });

    it("切换「收录于 DOAJ」→ URL 含 doaj=true", () => {
      renderFilters(baseQuery);
      const doajCheckbox = screen.getByRole("checkbox", { name: /DOAJ/i });
      fireEvent.click(doajCheckbox);
      const url = mockPush.mock.calls[0]?.[0];
      expect(url).toContain("doaj=true");
    });
  });

  describe("连点（乐观 query）", () => {
    it("连续勾选 Q1、Q2：勾选框立即打勾，第二次跳转两项都在", () => {
      renderFilters();
      fireEvent.click(screen.getByRole("checkbox", { name: /Q1/i }));
      expect(screen.getByRole("checkbox", { name: /Q1/i })).toBeChecked();
      fireEvent.click(screen.getByRole("checkbox", { name: /Q2/i }));
      expect(mockPush).toHaveBeenCalledTimes(2);
      expect(mockPush.mock.calls[1]?.[0]).toContain("jcr=Q1%2CQ2");
    });
  });
});
