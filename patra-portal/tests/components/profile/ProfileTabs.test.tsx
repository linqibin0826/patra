import { render, screen } from "@testing-library/react";
import { PUBLIC_PROFILE } from "@tests/fixtures/public-profile";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ProfileTabs, ProfileTabsGate } from "@/components/profile/ProfileTabs";
import { fetchUserPublicCollections } from "@/lib/interaction/collections";

vi.mock("@/lib/interaction/collections", () => ({ fetchUserPublicCollections: vi.fn() }));

describe("ProfileTabs", () => {
  it("关于 · 收藏夹两个链接，当前标签 aria-current，不带个数", () => {
    render(<ProfileTabs username="chen-yu" current="collections" />);
    const nav = screen.getByRole("navigation", { name: "主页分区" });
    expect(nav).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "关于" })).toHaveAttribute("href", "/u/chen-yu");
    expect(screen.getByRole("link", { name: "收藏夹" })).toHaveAttribute(
      "href",
      "/u/chen-yu/collections",
    );
    expect(screen.getByRole("link", { name: "收藏夹" })).toHaveAttribute("aria-current", "page");
  });
});

describe("ProfileTabsGate（「关于」页上的标签栏）", () => {
  it("本人视角：总出标签栏，不请求", async () => {
    render(await ProfileTabsGate({ profile: PUBLIC_PROFILE, self: true }));
    expect(screen.getByRole("navigation", { name: "主页分区" })).toBeInTheDocument();
    expect(fetchUserPublicCollections).not.toHaveBeenCalled();
  });

  it("别人视角：至少一个可见的公开收藏夹才出", async () => {
    vi.mocked(fetchUserPublicCollections).mockResolvedValue({
      kind: "ok",
      data: [
        {
          collectionId: "9",
          name: "组会",
          description: null,
          itemCount: 2,
          updatedAt: "2026-10-24T02:00:00Z",
        },
      ],
    });
    render(await ProfileTabsGate({ profile: PUBLIC_PROFILE, self: false }));
    expect(screen.getByRole("navigation", { name: "主页分区" })).toBeInTheDocument();
    expect(fetchUserPublicCollections).toHaveBeenCalledWith(PUBLIC_PROFILE.userId);
  });

  it.each<[string, Awaited<ReturnType<typeof fetchUserPublicCollections>>]>([
    ["空", { kind: "ok", data: [] }],
    ["失败", { kind: "unavailable" }],
  ])("别人视角、%s：不渲染标签栏", async (_label, read) => {
    vi.mocked(fetchUserPublicCollections).mockResolvedValue(read);
    const { container } = render(await ProfileTabsGate({ profile: PUBLIC_PROFILE, self: false }));
    expect(container).toBeEmptyDOMElement();
  });
});
