import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { TopicCloud } from "@/components/portal/TopicCloud";

describe("TopicCloud", () => {
  it("含 '此刻热议' eyebrow", () => {
    render(<TopicCloud />);
    expect(screen.getByText(/此刻热议/)).toBeInTheDocument();
  });

  it("渲染 32 个 topic 列表项", () => {
    render(<TopicCloud />);
    const items = screen.getAllByRole("listitem");
    expect(items).toHaveLength(32);
  });

  it("tier 1 项显示 delta (例 GLP-1 +34%)", () => {
    render(<TopicCloud />);
    expect(screen.getByText(/\+34%/)).toBeInTheDocument();
  });

  it("含 'GLP-1 受体激动剂' 文字 (最热 topic)", () => {
    render(<TopicCloud />);
    expect(screen.getByText("GLP-1 受体激动剂")).toBeInTheDocument();
  });
});
