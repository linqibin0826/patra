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

  it("点击试试 chip 后填入对应 mode + text", async () => {
    const user = userEvent.setup();
    render(<Composer />);
    await user.click(screen.getByRole("button", { name: /AlphaFold/ }));
    const input = screen.getByRole("textbox") as HTMLInputElement;
    expect(input.value).toBe("AlphaFold 蛋白结构预测");
  });
});
