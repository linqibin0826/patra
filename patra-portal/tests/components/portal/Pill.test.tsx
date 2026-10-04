import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Pill } from "@/components/portal/Pill";

describe("Pill", () => {
  it("默认中性语气、md 尺寸", () => {
    render(<Pill>MeSH 词</Pill>);
    expect(screen.getByText("MeSH 词")).toHaveClass(
      "rounded-full",
      "border-border-default",
      "bg-paper-50",
      "text-fg-2",
      "px-3",
      "text-sm",
    );
  });

  it("陶土语气 + sm 尺寸（贴在作者名旁的小标签）", () => {
    render(
      <Pill tone="clay" size="sm">
        通讯
      </Pill>,
    );
    expect(screen.getByText("通讯")).toHaveClass(
      "border-clay-200",
      "bg-clay-50",
      "text-clay-800",
      "px-2",
      "text-3xs",
    );
  });

  it("调用方可追加外边距等布局类，并透传 title", () => {
    render(
      <Pill className="ml-2" title="主要主题词">
        Major
      </Pill>,
    );
    const pill = screen.getByText("Major");
    expect(pill).toHaveClass("ml-2");
    expect(pill).toHaveAttribute("title", "主要主题词");
  });
});
