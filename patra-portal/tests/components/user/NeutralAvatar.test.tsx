import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { NeutralAvatar } from "@/components/user/NeutralAvatar";

describe("NeutralAvatar（简报 §3.20）", () => {
  it("匿名作者：灰底 + 人形图标，对读屏隐藏", () => {
    const { container } = render(<NeutralAvatar kind="anonymous" size={32} mobileSize={28} />);
    const root = container.firstElementChild;
    expect(root).toHaveClass("bg-ink-200", "size-8", "max-[880px]:size-7");
    expect(root?.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });

  it("已注销用户：灰底、无字无图标", () => {
    const { container } = render(<NeutralAvatar kind="deleted" size={24} />);
    expect(container.firstElementChild).toHaveClass("bg-ink-200", "size-6");
    expect(container.firstElementChild?.childElementCount).toBe(0);
    expect(container.textContent).toBe("");
  });
});
