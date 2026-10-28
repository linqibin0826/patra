import { render, screen } from "@testing-library/react";
import { PUBLIC_PROFILE } from "@tests/fixtures/public-profile";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ProfileTabs, ProfileTabsGate } from "@/components/profile/ProfileTabs";
import { visibleProfileTabs } from "@/lib/profile/tabs";

vi.mock("@/lib/profile/tabs", () => ({ visibleProfileTabs: vi.fn() }));

describe("ProfileTabs", () => {
  it("按可见标签渲染，顺序固定，当前标签 aria-current，不带个数", () => {
    render(
      <ProfileTabs
        username="chen-yu"
        tabs={["about", "collections", "experiences"]}
        current="experiences"
      />,
    );
    expect(screen.getByRole("navigation", { name: "主页分区" })).toBeInTheDocument();
    expect(screen.getAllByRole("link").map((link) => link.textContent)).toEqual([
      "关于",
      "收藏夹",
      "投稿经验",
    ]);
    expect(screen.getByRole("link", { name: "关于" })).toHaveAttribute("href", "/u/chen-yu");
    expect(screen.getByRole("link", { name: "收藏夹" })).toHaveAttribute(
      "href",
      "/u/chen-yu/collections",
    );
    expect(screen.getByRole("link", { name: "投稿经验" })).toHaveAttribute(
      "href",
      "/u/chen-yu/experiences",
    );
    expect(screen.getByRole("link", { name: "投稿经验" })).toHaveAttribute("aria-current", "page");
  });

  it("只有「关于」时不渲染", () => {
    const { container } = render(
      <ProfileTabs username="chen-yu" tabs={["about"]} current="about" />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});

describe("ProfileTabsGate（「关于」页上的标签栏）", () => {
  it("按判定结果渲染", async () => {
    vi.mocked(visibleProfileTabs).mockResolvedValue(["about", "experiences"]);
    render(await ProfileTabsGate({ profile: PUBLIC_PROFILE, self: false }));
    expect(visibleProfileTabs).toHaveBeenCalledWith(PUBLIC_PROFILE, false, "about");
    expect(screen.getAllByRole("link").map((link) => link.textContent)).toEqual([
      "关于",
      "投稿经验",
    ]);
  });

  it("只有「关于」：不渲染标签栏", async () => {
    vi.mocked(visibleProfileTabs).mockResolvedValue(["about"]);
    const { container } = render(await ProfileTabsGate({ profile: PUBLIC_PROFILE, self: false }));
    expect(container).toBeEmptyDOMElement();
  });
});
