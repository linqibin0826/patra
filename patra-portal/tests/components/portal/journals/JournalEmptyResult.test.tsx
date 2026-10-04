import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { JournalEmptyResult } from "@/components/portal/journals/JournalEmptyResult";
import type { VenueBrowseQuery } from "@/types/portal";

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

describe("JournalEmptyResult", () => {
  describe("kind=no-results", () => {
    it("渲染「未找到匹配的期刊」标题（q 为空时）", () => {
      render(<JournalEmptyResult kind="no-results" query={baseQuery} />);
      expect(screen.getByRole("heading")).toHaveTextContent("未找到匹配的期刊");
    });

    it("q 非空时标题带上搜索词", () => {
      render(<JournalEmptyResult kind="no-results" query={{ ...baseQuery, q: "nature" }} />);
      expect(screen.getByRole("heading")).toHaveTextContent("未找到匹配「nature」的期刊");
    });

    it("渲染「清除全部筛选」链接", () => {
      render(<JournalEmptyResult kind="no-results" query={baseQuery} />);
      expect(screen.getByRole("link", { name: /清除全部筛选/ })).toBeInTheDocument();
    });

    it("渲染「返回首页」链接", () => {
      render(<JournalEmptyResult kind="no-results" query={baseQuery} />);
      expect(screen.getByRole("link", { name: /返回首页/ })).toBeInTheDocument();
    });
  });

  describe("kind=empty-library", () => {
    it("渲染「库中暂无期刊」标题", () => {
      render(<JournalEmptyResult kind="empty-library" query={baseQuery} />);
      expect(screen.getByRole("heading")).toHaveTextContent("库中暂无期刊");
    });

    it("不渲染「清除全部筛选」链接", () => {
      render(<JournalEmptyResult kind="empty-library" query={baseQuery} />);
      expect(screen.queryByRole("link", { name: /清除全部筛选/ })).not.toBeInTheDocument();
    });

    it("渲染「返回首页」链接", () => {
      render(<JournalEmptyResult kind="empty-library" query={baseQuery} />);
      expect(screen.getByRole("link", { name: /返回首页/ })).toBeInTheDocument();
    });
  });
});
