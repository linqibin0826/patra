import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ExploreFeed } from "@/components/portal/ExploreFeed";

describe("ExploreFeed", () => {
  it("渲染 3 个 tab trigger（热度 / 最新 / 被引）", () => {
    render(<ExploreFeed />);
    expect(screen.getByRole("tab", { name: /热度/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /最新/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /被引/ })).toBeInTheDocument();
  });

  it("默认 trending tab active", () => {
    render(<ExploreFeed />);
    const trending = screen.getByRole("tab", { name: /热度/ });
    // base-ui Tab 用 data-active 属性表达 active 状态
    const isActive =
      trending.getAttribute("aria-selected") === "true" ||
      trending.getAttribute("data-selected") !== null ||
      trending.hasAttribute("data-active");
    expect(isActive).toBe(true);
  });

  it("切到最新 tab 后 panel 显示 4 个 paper", async () => {
    const user = userEvent.setup();
    render(<ExploreFeed />);
    await user.click(screen.getByRole("tab", { name: /最新/ }));
    const articles = await screen.findAllByRole("article");
    expect(articles.length).toBeGreaterThanOrEqual(4);
  });
});
