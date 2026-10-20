import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { AbstractBlock } from "@/components/portal/paper-detail/AbstractBlock";
import type { PaperDetail } from "@/types/portal";

const base = { abstractSections: [], abstractPlainText: null } as unknown as PaperDetail;

describe("AbstractBlock", () => {
  it("结构化：渲染段落标签与正文", () => {
    render(
      <AbstractBlock
        paper={{ ...base, abstractSections: [{ label: "BACKGROUND", text: "bg text" }] }}
      />,
    );
    expect(screen.getByText("BACKGROUND")).toBeInTheDocument();
    expect(screen.getByText("bg text")).toBeInTheDocument();
  });
  it("纯文本：渲染整段", () => {
    render(<AbstractBlock paper={{ ...base, abstractPlainText: "plain abs" }} />);
    expect(screen.getByText("plain abs")).toBeInTheDocument();
  });
  it("空：渲染占位文案", () => {
    render(<AbstractBlock paper={base} />);
    expect(screen.getByText(/暂无摘要/)).toBeInTheDocument();
  });
  it("混合：无 label 段渲染正文且不渲染空标签槽", () => {
    const { container } = render(
      <AbstractBlock
        paper={{
          ...base,
          abstractSections: [
            { label: "BACKGROUND", text: "bg text" },
            { label: null, text: "unlabeled tail" },
          ],
        }}
      />,
    );
    expect(screen.getByText("BACKGROUND")).toBeInTheDocument();
    expect(screen.getByText("unlabeled tail")).toBeInTheDocument();
    // 无 label 段的标签格留空占位，读屏跳过
    const tailRow = screen.getByText("unlabeled tail").closest(".grid") as HTMLElement;
    expect(tailRow.firstElementChild).toHaveAttribute("aria-hidden", "true");
    expect(container.querySelectorAll(".grid").length).toBe(2);
  });

  it("已知标签在标签格里加中文译名，未知标签只显示原文（PAP-89）", () => {
    render(
      <AbstractBlock
        paper={{
          ...base,
          abstractSections: [
            { label: "OBJECTIVE", text: "obj text" },
            { label: "HYPOTHESIS", text: "hyp text" },
          ],
        }}
      />,
    );
    const objRow = screen.getByText("obj text").closest(".grid") as HTMLElement;
    expect(objRow.firstElementChild).toHaveTextContent("OBJECTIVE目的");
    const hypRow = screen.getByText("hyp text").closest(".grid") as HTMLElement;
    expect(hypRow.firstElementChild).toHaveTextContent(/^HYPOTHESIS$/);
  });

  it("纯文本摘要：标签格留空", () => {
    render(<AbstractBlock paper={{ ...base, abstractPlainText: "plain only" }} />);
    const row = screen.getByText("plain only").closest(".grid") as HTMLElement;
    expect(row.firstElementChild).toHaveAttribute("aria-hidden", "true");
  });
  it("结构化段落正文按白名单渲染内联标签（回归：防止改回纯文本插值）", () => {
    const { container } = render(
      <AbstractBlock
        paper={{
          ...base,
          abstractSections: [{ label: "RESULTS", text: "CO<sub>2</sub> fixation" }],
        }}
      />,
    );
    const sub = container.querySelector("sub");
    expect(sub).not.toBeNull();
    expect(sub?.textContent).toBe("2");
  });
});
