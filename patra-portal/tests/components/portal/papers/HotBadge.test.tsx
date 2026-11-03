import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { HotBadge } from "@/components/portal/papers/HotBadge";

const TIP = "近 14 天读的人多，在热读榜前 100 名。只是标记，不影响检索排序。";

describe("HotBadge（简报 v0.18 §3.4）", () => {
  it("写「近期热读」", () => {
    render(<HotBadge />);
    expect(screen.getByText("近期热读")).toBeInTheDocument();
  });

  it("search.hot.tooltip：悬停出说明", async () => {
    const user = userEvent.setup();
    render(<HotBadge />);
    await user.hover(screen.getByText("近期热读"));
    expect(await screen.findByRole("tooltip")).toHaveTextContent(TIP);
  });

  it("键盘聚焦也出说明", async () => {
    const user = userEvent.setup();
    render(<HotBadge />);
    await user.tab();
    expect(screen.getByRole("button", { name: /近期热读/ })).toHaveFocus();
    expect(await screen.findByRole("tooltip")).toHaveTextContent(TIP);
  });

  it("读屏也读得到说明（按钮带描述）", () => {
    render(<HotBadge />);
    expect(screen.getByRole("button", { name: /近期热读/ })).toHaveAccessibleDescription(TIP);
  });
});
