import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { JournalSearchSortControls } from "@/components/portal/journals/JournalSearchSortControls";
import { useBrowseFilterUiStore } from "@/store/browse-filter-ui";
import type { VenueBrowseQuery } from "@/types/portal";

// mock next/navigation
const mockReplace = vi.fn<(url: string) => void>();
const mockPush = vi.fn<(url: string) => void>();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace, push: mockPush }),
}));

const baseQuery: VenueBrowseQuery = {
  q: "",
  sort: "if",
  page: 1,
  subject: [],
  jcr: [],
  cas: [],
  casTop: false,
  oa: false,
  doaj: false,
  country: [],
};

describe("JournalSearchSortControls", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockReplace.mockClear();
    mockPush.mockClear();
    useBrowseFilterUiStore.setState({ sheetOpen: false });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe("检索框", () => {
    it("展示当前 query.q 初始值", () => {
      render(<JournalSearchSortControls query={{ ...baseQuery, q: "nature" }} />);
      const input = screen.getByRole("searchbox");
      expect(input).toHaveValue("nature");
    });

    it("输入 300ms 内不触发 router.replace", () => {
      render(<JournalSearchSortControls query={baseQuery} />);
      const input = screen.getByRole("searchbox");
      fireEvent.change(input, { target: { value: "cell" } });
      act(() => {
        vi.advanceTimersByTime(200);
      });
      expect(mockReplace).not.toHaveBeenCalled();
    });

    it("输入后 300ms 触发 router.replace 且 URL 含 q 与 page 归 1", () => {
      render(<JournalSearchSortControls query={{ ...baseQuery, page: 3 }} />);
      const input = screen.getByRole("searchbox");
      fireEvent.change(input, { target: { value: "cell" } });
      act(() => {
        vi.advanceTimersByTime(300);
      });
      expect(mockReplace).toHaveBeenCalledTimes(1);
      const url = mockReplace.mock.calls[0]?.[0];
      expect(url).toContain("q=cell");
      expect(url).not.toContain("page=");
    });

    it("清除按钮清空输入并立即触发 router.replace", () => {
      render(<JournalSearchSortControls query={{ ...baseQuery, q: "nature" }} />);
      const clearBtn = screen.getByRole("button", { name: /清除/i });
      fireEvent.click(clearBtn);
      expect(mockReplace).toHaveBeenCalledTimes(1);
      const url = mockReplace.mock.calls[0]?.[0];
      expect(url).not.toContain("q=");
    });
  });

  describe("排序段控件", () => {
    it("渲染全部 SORT_OPTIONS", () => {
      render(<JournalSearchSortControls query={baseQuery} />);
      expect(screen.getByRole("button", { name: "影响因子" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "中科院分区" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "刊名 A–Z" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "被引总数" })).toBeInTheDocument();
    });

    it("当前 sort 按钮 aria-pressed=true", () => {
      render(<JournalSearchSortControls query={{ ...baseQuery, sort: "cas" }} />);
      const casBtn = screen.getByRole("button", { name: "中科院分区" });
      expect(casBtn).toHaveAttribute("aria-pressed", "true");
      const ifBtn = screen.getByRole("button", { name: "影响因子" });
      expect(ifBtn).toHaveAttribute("aria-pressed", "false");
    });

    it("点击排序触发 router.push 含 sort + page 归 1", () => {
      render(<JournalSearchSortControls query={{ ...baseQuery, page: 5 }} />);
      fireEvent.click(screen.getByRole("button", { name: "刊名 A–Z" }));
      expect(mockPush).toHaveBeenCalledTimes(1);
      const url = mockPush.mock.calls[0]?.[0];
      expect(url).toContain("sort=az");
      expect(url).not.toContain("page=");
    });

    it("输入后立刻点排序 → 取消 pending 防抖，不用旧 query 回滚排序", () => {
      render(<JournalSearchSortControls query={baseQuery} />);
      const input = screen.getByRole("searchbox");
      fireEvent.change(input, { target: { value: "cell" } });
      // 防抖未到点就点排序
      fireEvent.click(screen.getByRole("button", { name: "中科院分区" }));
      expect(mockPush).toHaveBeenCalledTimes(1);
      // 越过防抖窗口：pending 的 replace 应已被取消，否则会用旧 query 把排序回滚
      act(() => {
        vi.advanceTimersByTime(300);
      });
      expect(mockReplace).not.toHaveBeenCalled();
    });
  });

  describe("移动端筛选按钮", () => {
    it("点击「筛选」按钮调用 store.open()", () => {
      render(<JournalSearchSortControls query={baseQuery} />);
      const filterBtn = screen.getByRole("button", { name: /筛选/i });
      fireEvent.click(filterBtn);
      expect(useBrowseFilterUiStore.getState().sheetOpen).toBe(true);
    });

    it("有已选项时显示角标", () => {
      render(
        <JournalSearchSortControls query={{ ...baseQuery, jcr: ["Q1", "Q2"], casTop: true }} />,
      );
      // 角标文本 = 3（2 + 1）
      expect(screen.getByText("3")).toBeInTheDocument();
    });

    it("无已选项时不渲染角标", () => {
      render(<JournalSearchSortControls query={baseQuery} />);
      // 无 "0" 角标
      expect(screen.queryByTestId("filter-badge")).not.toBeInTheDocument();
    });
  });
});
