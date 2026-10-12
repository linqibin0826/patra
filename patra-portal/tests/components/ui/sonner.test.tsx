import { act, render, screen } from "@testing-library/react";
import { toast } from "sonner";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Toaster } from "@/components/ui/sonner";

describe("Toaster", () => {
  it("render 不崩", () => {
    const { container } = render(<Toaster />);
    // sonner 渲染到 portal 内，container 本身为空但不会抛错
    expect(container).toBeTruthy();
  });

  it("透传 position prop 不报错", () => {
    const { unmount } = render(<Toaster position="top-right" />);
    expect(() => unmount()).not.toThrow();
  });
});

describe("Toaster · 成功提示的外观", () => {
  /// 弹一条成功提示，返回提示本身（`<li>`）和装提示的列表（`<ol>`）
  async function showSuccess() {
    render(<Toaster />);
    act(() => {
      toast.success("已登录");
    });
    const item = (await screen.findByText("已登录")).closest("[data-sonner-toast]");
    const list = item?.closest<HTMLElement>("[data-sonner-toaster]");
    if (!(item instanceof HTMLElement) || !list) throw new Error("没有找到提示");
    return { item, list };
  }

  it("底色、文字、边框、圆角用 token，写在行内样式上：sonner 插入的样式不分层，压得过 Tailwind 的类", async () => {
    const { list } = await showSuccess();
    expect(list.style.getPropertyValue("--normal-bg")).toBe("var(--bg-elevated)");
    expect(list.style.getPropertyValue("--normal-text")).toBe("var(--fg-1)");
    expect(list.style.getPropertyValue("--normal-border")).toBe("var(--border-default)");
    expect(list.style.getPropertyValue("--border-radius")).toBe("var(--radius-lg)");
  });

  it("阴影用 shadow-lg（带 !important 才压得过 sonner 写死的阴影），字体用门户的无衬线栈", async () => {
    const { item } = await showSuccess();
    expect(item).toHaveClass("font-sans", "shadow-lg!");
  });

  it("成功图标是 moss 色", async () => {
    const { item } = await showSuccess();
    expect(item.querySelector("[data-icon] svg")).toHaveClass("text-status-success");
  });
});
