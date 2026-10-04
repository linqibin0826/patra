import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Composer } from "@/components/portal/Composer";

describe("Composer", () => {
  it("默认 keyword tab active", () => {
    render(<Composer />);
    expect(screen.getByRole("tab", { name: /关键词/ })).toHaveAttribute("aria-selected", "true");
  });

  it("切换到 PMID tab 后 input placeholder 变为 PMID 示例", async () => {
    const user = userEvent.setup();
    render(<Composer />);
    await user.click(screen.getByRole("tab", { name: "PMID" }));
    expect(screen.getByPlaceholderText("38491203")).toBeInTheDocument();
  });

  it("切换到等宽模式时输入框行高不变（检索框高度不跳）", async () => {
    const user = userEvent.setup();
    render(<Composer />);
    const leading = () => screen.getByRole("textbox").className.match(/(?:^|\s)leading-\S+/g);
    const before = leading();
    expect(before).not.toBeNull();
    await user.click(screen.getByRole("tab", { name: "PMID" }));
    expect(leading()).toEqual(before);
  });

  it("DOI tab 切换后 input 应用 font-mono", async () => {
    const user = userEvent.setup();
    render(<Composer />);
    await user.click(screen.getByRole("tab", { name: "DOI" }));
    const input = screen.getByRole("textbox");
    expect(input.className).toMatch(/font-mono/);
  });

  it("提交时调用 onSubmit prop（含 value 与 mode）", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<Composer onSubmit={onSubmit} />);
    await user.type(screen.getByRole("textbox"), "GLP-1");
    await user.click(screen.getByRole("button", { name: /搜索/ }));
    expect(onSubmit).toHaveBeenCalledWith({ mode: "keyword", value: "GLP-1" });
  });

  it("表单内有焦点时描边换成焦点色，外面再加一圈淡陶土光晕", () => {
    const { container } = render(<Composer />);
    expect(container.querySelector("form")).toHaveClass(
      "focus-within:border-border-focus",
      "focus-within:ring-4",
      "ring-clay-500/15",
    );
  });

  it("页面上用长投影；快速检索对话框里浮在遮罩上，用对话框阴影 shadow-xl", () => {
    const { container, unmount } = render(<Composer />);
    expect(container.querySelector("form")).toHaveClass("shadow-composer");
    unmount();
    const dialog = render(<Composer surface="dialog" />);
    const form = dialog.container.querySelector("form");
    expect(form).toHaveClass("shadow-xl");
    expect(form).not.toHaveClass("shadow-composer");
  });

  it("输入框 id 默认 hero-input，可由 inputId 覆盖（快速检索对话框与 Hero 同屏时避免重复 id）", () => {
    const { unmount } = render(<Composer />);
    expect(screen.getByRole("textbox")).toHaveAttribute("id", "hero-input");
    unmount();
    render(<Composer inputId="quick-search-input" />);
    expect(screen.getByRole("textbox")).toHaveAttribute("id", "quick-search-input");
  });

  it("点击试试 chip 后填入对应 mode + text", async () => {
    const user = userEvent.setup();
    render(<Composer />);
    await user.click(screen.getByRole("button", { name: /AlphaFold/ }));
    const input = screen.getByRole("textbox") as HTMLInputElement;
    expect(input.value).toBe("AlphaFold 蛋白结构预测");
  });
});
