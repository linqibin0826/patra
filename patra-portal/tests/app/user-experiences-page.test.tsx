import { screen } from "@testing-library/react";
import { makeUserExperience } from "@tests/fixtures/experience";
import { PUBLIC_PROFILE } from "@tests/fixtures/public-profile";
import { USER } from "@tests/fixtures/user";
import { renderWithQuery } from "@tests/support/render-with-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
  notFound: vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
  usePathname: () => "/u/chen-yu/experiences",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/components/portal/TopNav", () => ({ TopNav: () => <nav aria-label="导航栏桩" /> }));
vi.mock("@/components/portal/Footer", () => ({ Footer: () => <footer /> }));
vi.mock("@/lib/identity/public-profile", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/identity/public-profile")>()),
  fetchPublicProfile: vi.fn(),
}));
vi.mock("@/lib/auth/current-user", () => ({ lookupSession: vi.fn() }));
vi.mock("@/lib/interaction/collections", () => ({ fetchUserPublicCollections: vi.fn() }));
vi.mock("@/lib/interaction/experience", () => ({ fetchUserExperiences: vi.fn() }));
vi.mock("@/lib/identity/verification-actions", () => ({ resendVerificationEmail: vi.fn() }));
vi.mock("@/lib/public-url", () => ({
  publicUrl: vi.fn(async (path: string) => `http://mini${path}`),
}));

import ExperiencesPage, { generateMetadata } from "@/app/u/[username]/experiences/page";
import { lookupSession } from "@/lib/auth/current-user";
import { fetchPublicProfile } from "@/lib/identity/public-profile";
import { fetchUserPublicCollections } from "@/lib/interaction/collections";
import { fetchUserExperiences } from "@/lib/interaction/experience";

const params = (username = "chen-yu") => ({ params: Promise.resolve({ username }) });

function viewAs(self: boolean) {
  vi.mocked(lookupSession).mockResolvedValue({
    status: "authenticated",
    user: { ...USER, username: self ? "chen-yu" : "someone-else" },
  });
}

beforeEach(() => {
  vi.mocked(fetchPublicProfile).mockResolvedValue({ kind: "found", profile: PUBLIC_PROFILE });
  vi.mocked(fetchUserPublicCollections).mockResolvedValue({ kind: "ok", data: [] });
  vi.mocked(fetchUserExperiences).mockResolvedValue({
    kind: "ok",
    data: { items: [makeUserExperience()], nextCursor: null },
  });
});

describe("/u/[username]/experiences", () => {
  it("旧用户名：跳到新名的投稿经验标签", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue({ kind: "moved", username: "new-name" });
    viewAs(false);
    await expect(ExperiencesPage(params("old"))).rejects.toThrow(
      "NEXT_REDIRECT:/u/new-name/experiences",
    );
  });

  it("不存在：404", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue({ kind: "not-found" });
    viewAs(false);
    await expect(ExperiencesPage(params())).rejects.toThrow("NEXT_NOT_FOUND");
  });

  it("标签栏总在，「投稿经验」是当前；首屏按 20 条取", async () => {
    viewAs(false);
    renderWithQuery(await ExperiencesPage(params()));
    expect(screen.getByRole("link", { name: "投稿经验" })).toHaveAttribute("aria-current", "page");
    expect(screen.queryByRole("link", { name: "收藏夹" })).not.toBeInTheDocument();
    expect(fetchUserExperiences).toHaveBeenCalledWith(PUBLIC_PROFILE.userId, { limit: 20 });
    expect(
      screen.getByRole("link", { name: /Journal of the American Heart Association/ }),
    ).toHaveAttribute("href", "/posts/9");
  });

  it("标题、canonical；不允许收录时 noindex", async () => {
    const metadata = await generateMetadata(params());
    expect(metadata.title).toBe("陈宇 (@chen-yu) 的投稿经验 · Patra");
    expect(metadata.alternates?.canonical).toMatch(/\/u\/chen-yu\/experiences$/);
    vi.mocked(fetchPublicProfile).mockResolvedValue({
      kind: "found",
      profile: { ...PUBLIC_PROFILE, indexable: false },
    });
    expect((await generateMetadata(params())).robots).toEqual({ index: false });
  });
});
