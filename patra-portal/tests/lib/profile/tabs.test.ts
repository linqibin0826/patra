// @vitest-environment node
import { makeUserExperience } from "@tests/fixtures/experience";
import { PUBLIC_PROFILE } from "@tests/fixtures/public-profile";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchUserPublicCollections } from "@/lib/interaction/collections";
import { fetchUserExperiences } from "@/lib/interaction/experience";
import { visibleProfileTabs } from "@/lib/profile/tabs";

vi.mock("server-only", () => ({}));
vi.mock("@/lib/interaction/collections", () => ({ fetchUserPublicCollections: vi.fn() }));
vi.mock("@/lib/interaction/experience", () => ({ fetchUserExperiences: vi.fn() }));

const COLLECTION = {
  collectionId: "9",
  name: "组会",
  description: null,
  itemCount: 2,
  updatedAt: "2026-10-24T02:00:00Z",
};
const collections = (has: boolean) =>
  vi.mocked(fetchUserPublicCollections).mockResolvedValue({
    kind: "ok",
    data: has ? [COLLECTION] : [],
  });
const experiences = (has: boolean) =>
  vi.mocked(fetchUserExperiences).mockResolvedValue({
    kind: "ok",
    data: { items: has ? [makeUserExperience()] : [], nextCursor: null },
  });

afterEach(() => {
  vi.mocked(fetchUserPublicCollections).mockReset();
  vi.mocked(fetchUserExperiences).mockReset();
});

describe("visibleProfileTabs（spec 7.1）", () => {
  it("本人视角：三项都在，不请求", async () => {
    await expect(visibleProfileTabs(PUBLIC_PROFILE, true, "about")).resolves.toEqual([
      "about",
      "collections",
      "experiences",
    ]);
    expect(fetchUserPublicCollections).not.toHaveBeenCalled();
    expect(fetchUserExperiences).not.toHaveBeenCalled();
  });

  it.each([
    [true, true, ["about", "collections", "experiences"]],
    [false, true, ["about", "experiences"]],
    [true, false, ["about", "collections"]],
    [false, false, ["about"]],
  ])("别人视角：收藏夹 %s、经验 %s → %j", async (hasCollections, hasExperiences, tabs) => {
    collections(hasCollections);
    experiences(hasExperiences);
    await expect(visibleProfileTabs(PUBLIC_PROFILE, false, "about")).resolves.toEqual(tabs);
    expect(fetchUserExperiences).toHaveBeenCalledWith(PUBLIC_PROFILE.userId, { limit: 1 });
  });

  it("读失败当作没有", async () => {
    vi.mocked(fetchUserPublicCollections).mockResolvedValue({ kind: "unavailable" });
    vi.mocked(fetchUserExperiences).mockResolvedValue({ kind: "unavailable" });
    await expect(visibleProfileTabs(PUBLIC_PROFILE, false, "about")).resolves.toEqual(["about"]);
  });

  it("当前所在的标签总在", async () => {
    collections(false);
    experiences(false);
    await expect(visibleProfileTabs(PUBLIC_PROFILE, false, "experiences")).resolves.toEqual([
      "about",
      "experiences",
    ]);
  });

  it("两次读同时发出", async () => {
    let resolveCollections: () => void = () => {};
    vi.mocked(fetchUserPublicCollections).mockReturnValue(
      new Promise((resolve) => {
        resolveCollections = () => resolve({ kind: "ok", data: [] });
      }),
    );
    experiences(true);
    const pending = visibleProfileTabs(PUBLIC_PROFILE, false, "about");
    await Promise.resolve();
    expect(fetchUserExperiences).toHaveBeenCalled();
    resolveCollections();
    await expect(pending).resolves.toEqual(["about", "experiences"]);
  });
});
