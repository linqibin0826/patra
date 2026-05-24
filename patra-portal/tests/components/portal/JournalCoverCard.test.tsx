import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { JournalCoverCard } from "@/components/portal/JournalCoverCard";
import { JOURNALS } from "@/data/journals";

describe("JournalCoverCard", () => {
  const nejm = JOURNALS.find((j) => j.id === "nejm");
  if (!nejm) throw new Error("unreachable");

  it("渲染期刊全名", () => {
    render(<JournalCoverCard journal={nejm} />);
    expect(screen.getByText("New England Journal of Medicine")).toBeInTheDocument();
  });

  it("渲染封面文字 (NEJM)", () => {
    render(<JournalCoverCard journal={nejm} />);
    expect(screen.getByText("NEJM")).toBeInTheDocument();
  });

  it("渲染影响因子 (158.5)", () => {
    render(<JournalCoverCard journal={nejm} />);
    expect(screen.getByText("158.5")).toBeInTheDocument();
  });

  it("渲染本周收录数 (312)", () => {
    render(<JournalCoverCard journal={nejm} />);
    expect(screen.getByText("312")).toBeInTheDocument();
  });
});
