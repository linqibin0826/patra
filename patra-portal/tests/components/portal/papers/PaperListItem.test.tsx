import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PaperListItem } from "@/components/portal/papers/PaperListItem";
import { PaperListSkeleton } from "@/components/portal/papers/PaperListSkeleton";
import { makePaper } from "../../../fixtures/paper";

describe("PaperListItem", () => {
  it("标题链接到详情页；来源行显示来源与 DOI", () => {
    render(<PaperListItem paper={makePaper()} />);
    expect(screen.getByRole("link", { name: /OSBPL6 protects/ })).toHaveAttribute(
      "href",
      "/papers/352303128713027974",
    );
    expect(screen.getByText("PubMed")).toBeInTheDocument();
    expect(screen.getByText("10.1016/j.jare.2026.01.066")).toBeInTheDocument();
  });

  it("highlight 时标题命中片段渲染为 mark", () => {
    const { container } = render(<PaperListItem paper={makePaper()} highlight="osbpl6" />);
    // mark 无稳定 ARIA role，querySelector 是此处最后手段
    expect(container.querySelector("mark")?.textContent).toBe("OSBPL6");
  });

  it("有 venueId 时期刊名链接到期刊详情；无则为纯文本", () => {
    const { unmount } = render(<PaperListItem paper={makePaper()} />);
    expect(screen.getByRole("link", { name: "Journal of advanced research" })).toHaveAttribute(
      "href",
      "/journals/319041872872550658",
    );
    unmount();
    render(<PaperListItem paper={makePaper({ venueId: null })} />);
    expect(
      screen.queryByRole("link", { name: "Journal of advanced research" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Journal of advanced research")).toBeInTheDocument();
  });

  it("元信息：年份 + 前 2 位作者 + 等 N 位作者；作者为空不显示", () => {
    const { unmount } = render(<PaperListItem paper={makePaper()} />);
    expect(screen.getByText("2026")).toBeInTheDocument();
    expect(screen.getByText("Chang Mengni, Zhang Kaiqi 等 1 位作者")).toBeInTheDocument();
    unmount();
    render(<PaperListItem paper={makePaper({ authors: [] })} />);
    expect(screen.queryByText(/位作者/)).not.toBeInTheDocument();
  });

  it("徽章：类型徽章总在；证据等级仅成功分级时出现", () => {
    const { unmount } = render(<PaperListItem paper={makePaper()} />);
    expect(screen.getByText("Journal Article")).toBeInTheDocument();
    expect(screen.queryByText("未分级")).not.toBeInTheDocument();
    unmount();
    render(
      <PaperListItem
        paper={makePaper({
          evidenceLevel: {
            level: "COHORT_OR_CASE_CONTROL",
            rank: 3,
            label: "队列 / 病例对照",
            derived: true,
          },
        })}
      />,
    );
    expect(screen.getByText("队列 / 病例对照")).toBeInTheDocument();
  });

  it("摘要存在时显示，为 null 时不占位", () => {
    const { unmount } = render(<PaperListItem paper={makePaper()} />);
    expect(screen.getByText(/Demyelination is associated/)).toBeInTheDocument();
    unmount();
    render(<PaperListItem paper={makePaper({ abstractSnippet: null })} />);
    expect(screen.queryByText(/Demyelination is associated/)).not.toBeInTheDocument();
  });

  it("不出现收藏 / 评论 / 引用等按钮（本版边界 D）", () => {
    render(<PaperListItem paper={makePaper()} />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.queryByText(/引用/)).not.toBeInTheDocument();
  });
});

describe("PaperListSkeleton", () => {
  it("对辅助技术隐藏", () => {
    const { container } = render(<PaperListSkeleton />);
    expect(container.firstChild).toHaveAttribute("aria-hidden", "true");
  });
});
