import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { JournalCoverCard } from "@/components/portal/JournalCoverCard";
import type { VenueBrowse } from "@/types/portal";

const journal: VenueBrowse = {
  id: "319041872872550658",
  name: "Annals of oncology",
  abbr: "Ann Oncol",
  coverObjectKey: null,
  impactFactor: 65.4,
  jcrQuartile: "Q1",
  jcrSubject: null,
  casMajorCategory: null,
  casMajorQuartile: null,
  casIsTop: null,
  countryCode: null,
  citedByCount: null,
  foundedYear: 1990,
  isOpenAccess: null,
  isInDoaj: null,
  issnL: null,
};

describe("JournalCoverCard", () => {
  it("渲染期刊全名", () => {
    render(<JournalCoverCard journal={journal} />);
    expect(screen.getByText("Annals of oncology")).toBeInTheDocument();
  });

  it("封面文字来自缩写", () => {
    render(<JournalCoverCard journal={journal} />);
    // abbr 在封面与信息区各出现一次
    expect(screen.getAllByText("Ann Oncol").length).toBeGreaterThanOrEqual(1);
  });

  it("渲染影响因子 (65.4)", () => {
    render(<JournalCoverCard journal={journal} />);
    expect(screen.getByText("65.4")).toBeInTheDocument();
  });

  it("渲染 JCR 分区 (Q1)", () => {
    render(<JournalCoverCard journal={journal} />);
    expect(screen.getByText("Q1")).toBeInTheDocument();
  });

  it("渲染创刊年 (est. 1990)", () => {
    render(<JournalCoverCard journal={journal} />);
    expect(screen.getByText(/1990/)).toBeInTheDocument();
  });

  it("foundedYear 为 null 时不渲染 est. 行", () => {
    render(<JournalCoverCard journal={{ ...journal, foundedYear: null }} />);
    expect(screen.queryByText(/est\./)).not.toBeInTheDocument();
  });

  it("impactFactor 为 null 时渲染 '—' 不抛错", () => {
    render(<JournalCoverCard journal={{ ...journal, impactFactor: null }} />);
    expect(screen.getByText("—")).toBeInTheDocument();
  });

  it("impactFactor 有值时渲染 toFixed(1)", () => {
    render(<JournalCoverCard journal={{ ...journal, impactFactor: 12.345 }} />);
    expect(screen.getByText("12.3")).toBeInTheDocument();
  });

  it("影响因子为 0（后端用 0 表示没有影响因子）显示 —", () => {
    render(<JournalCoverCard journal={{ ...journal, impactFactor: 0 }} />);
    expect(screen.queryByText("0.0")).not.toBeInTheDocument();
    expect(screen.getByText("—")).toBeInTheDocument();
  });

  it("封面底部的 vol 年份取当前年份，不写死", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2027-03-01T00:00:00Z"));
    try {
      render(<JournalCoverCard journal={journal} />);
      expect(screen.getByText("vol · 2027")).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it("链接到对应期刊详情页", () => {
    render(<JournalCoverCard journal={journal} />);
    expect(screen.getByRole("link", { name: /Annals of oncology/ })).toHaveAttribute(
      "href",
      "/journals/319041872872550658",
    );
  });
});
