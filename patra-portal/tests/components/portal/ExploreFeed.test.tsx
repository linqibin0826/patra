import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ExploreFeed } from "@/components/portal/ExploreFeed";

describe("ExploreFeed", () => {
  it("渲染 3 个 tab trigger（今日热门 / 最近更新 / 本周高引）", () => {
    render(<ExploreFeed />);
    expect(screen.getByRole("tab", { name: /今日热门/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /最近更新/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /本周高引/ })).toBeInTheDocument();
  });

  it("默认 trending tab active", () => {
    render(<ExploreFeed />);
    const trending = screen.getByRole("tab", { name: /今日热门/ });
    const isActive =
      trending.getAttribute("aria-selected") === "true" ||
      trending.getAttribute("data-selected") !== null ||
      trending.hasAttribute("data-active");
    expect(isActive).toBe(true);
  });

  it("切到最近更新 tab 后 panel 显示 4 个 paper", async () => {
    const user = userEvent.setup();
    render(<ExploreFeed />);
    await user.click(screen.getByRole("tab", { name: /最近更新/ }));
    const articles = await screen.findAllByRole("article");
    expect(articles.length).toBeGreaterThanOrEqual(4);
  });

  it("section 标题为 '值得读一读的文献'", () => {
    render(<ExploreFeed />);
    expect(screen.getByRole("heading", { name: "值得读一读的文献" })).toBeInTheDocument();
  });
});
