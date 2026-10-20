import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

const state = vi.hoisted(() => ({
  active: null as string | null,
  progress: { body: 0, whole: 0, started: false },
}));
vi.mock("@/lib/use-active-heading", () => ({ useActiveHeading: () => state.active }));
vi.mock("@/lib/use-reading-progress", () => ({ useReadingProgress: () => state.progress }));

import { CrumbStatus } from "@/components/portal/paper-detail/CrumbStatus";

let observe: (entries: { isIntersecting: boolean }[]) => void = () => {};

beforeEach(() => {
  const masthead = document.createElement("section");
  masthead.id = "masthead";
  document.body.append(masthead);
  vi.stubGlobal(
    "IntersectionObserver",
    class {
      constructor(cb: (entries: { isIntersecting: boolean }[]) => void) {
        observe = cb;
      }
      observe() {}
      disconnect() {}
    },
  );
});

afterEach(() => {
  document.getElementById("masthead")?.remove();
  vi.unstubAllGlobals();
  state.active = null;
  state.progress = { body: 0, whole: 0, started: false };
});

const positions = [
  { id: "ft-s1", label: "01" },
  { id: "ft-s4-5", label: "4.5" },
  { id: "references", label: "参考文献" },
];

describe("CrumbStatus", () => {
  it("页头还在视口里时不显示短标题；滚出去后显示", () => {
    render(<CrumbStatus shortTitle="Explainable machine learning" positions={positions} />);
    act(() => observe([{ isIntersecting: true }]));
    expect(screen.queryByText("Explainable machine learning")).toBeNull();
    act(() => observe([{ isIntersecting: false }]));
    expect(screen.getByText("Explainable machine learning")).toBeInTheDocument();
  });

  it("没有全文（positions 为空）时只显示短标题，没有「已读」", () => {
    render(<CrumbStatus shortTitle="Short" positions={[]} />);
    act(() => observe([{ isIntersecting: false }]));
    expect(screen.getByText("Short")).toBeInTheDocument();
    expect(screen.queryByText(/已读/)).toBeNull();
  });

  it("在正文里显示「章节号 · 已读 p%」，在文末区块显示区块名", () => {
    state.active = "ft-s4-5";
    state.progress = { body: 0.7, whole: 0.62, started: true };
    const { rerender } = render(<CrumbStatus shortTitle="T" positions={positions} />);
    act(() => observe([{ isIntersecting: false }]));
    expect(screen.getByText("4.5 · 已读 62%")).toBeInTheDocument();

    state.active = "references";
    state.progress = { body: 1, whole: 0.91, started: true };
    rerender(<CrumbStatus shortTitle="T" positions={positions} />);
    expect(screen.getByText("参考文献 · 已读 91%")).toBeInTheDocument();
  });

  it("进入正文前只显示「已读 p%」，没有位置", () => {
    state.progress = { body: 0, whole: 0.03, started: false };
    render(<CrumbStatus shortTitle="T" positions={positions} />);
    act(() => observe([{ isIntersecting: false }]));
    expect(screen.getByText("已读 3%")).toBeInTheDocument();
  });
});
