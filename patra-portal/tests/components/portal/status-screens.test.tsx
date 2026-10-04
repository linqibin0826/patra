import "@testing-library/jest-dom/vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ErrorState } from "@/components/portal/status/ErrorState";
import { NotFoundState } from "@/components/portal/status/NotFoundState";

describe("状态页文案里的破折号", () => {
  it.each([
    ["NotFoundState", () => render(<NotFoundState kind="paper" />)],
    ["ErrorState", () => render(<ErrorState onRetry={() => {}} context="加载期刊" />)],
  ])("%s：破折号交给中文字体渲染", (_, renderScreen) => {
    const { container } = renderScreen();
    expect(container.querySelector("[data-cjk-dash]")).toHaveTextContent("——");
  });
});

describe("NotFoundState", () => {
  it("渲染 404 标题、期刊文案与返回首页链接", () => {
    render(<NotFoundState kind="journal" />);
    expect(screen.getByRole("heading", { name: "没有这一页" })).toBeInTheDocument();
    expect(screen.getByText(/这本期刊不在 Patra/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /返回首页/ })).toHaveAttribute("href", "/");
  });

  it("只有末尾图标标记为 trailing（hover 前移），前置图标不标", () => {
    render(<NotFoundState kind="paper" />);
    const home = screen.getByRole("link", { name: /返回首页/ }).querySelector("svg");
    const browse = screen.getByRole("link", { name: /浏览全部文献/ }).querySelector("svg");
    expect(home).not.toHaveAttribute("data-icon");
    expect(browse).toHaveAttribute("data-icon", "trailing");
  });
});

describe("ErrorState", () => {
  it("点击重试调用 onRetry，不渲染原始堆栈", async () => {
    const onRetry = vi.fn();
    render(<ErrorState onRetry={onRetry} context="加载期刊" />);
    expect(screen.getByRole("heading", { name: "这一页没能加载出来" })).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: /重试/ }));
    expect(onRetry).toHaveBeenCalledOnce();
  });
});
