import { makeCollection, makeOverview } from "@tests/fixtures/interaction";
import { USER } from "@tests/fixtures/user";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
  notFound: vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
  unstable_rethrow: vi.fn(),
}));
vi.mock("@/lib/auth/current-user", () => ({ lookupSession: vi.fn() }));
vi.mock("@/lib/interaction/library", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/interaction/library")>()),
  fetchLibraryOverview: vi.fn(),
  fetchLibraryPage: vi.fn(),
}));
vi.mock("@/lib/identity/profile", () => ({ fetchMyPrivacy: vi.fn() }));
vi.mock("@/lib/public-url", () => ({
  publicUrl: vi.fn(async (path: string) => `http://mini${path}`),
}));

import CollectionPage, {
  generateMetadata as collectionMetadata,
} from "@/app/library/[collectionId]/page";
import LibraryPage, { metadata as libraryMetadata } from "@/app/library/page";
import UnfiledPage, { metadata as unfiledMetadata } from "@/app/library/unfiled/page";
import { lookupSession } from "@/lib/auth/current-user";
import { fetchLibraryOverview, fetchLibraryPage } from "@/lib/interaction/library";

const search = (query: Record<string, string> = {}) => ({ searchParams: Promise.resolve(query) });

beforeEach(() => {
  vi.mocked(lookupSession).mockResolvedValue({ status: "authenticated", user: USER });
  vi.mocked(fetchLibraryOverview).mockResolvedValue({
    kind: "ok",
    data: makeOverview({ collections: [makeCollection({ collectionId: "9", name: "组会" })] }),
  });
  vi.mocked(fetchLibraryPage).mockResolvedValue({ kind: "unavailable" });
});

describe("/library 页面", () => {
  it("未登录：去登录，next 带上地址与查询串", async () => {
    vi.mocked(lookupSession).mockResolvedValue({ status: "anonymous" });
    await expect(LibraryPage(search({ sort: "journal" }))).rejects.toThrow(
      "NEXT_REDIRECT:/login?next=%2Flibrary%3Fsort%3Djournal",
    );
  });

  it("标题", () => {
    expect(libraryMetadata.title).toBe("我的文献库 · Patra");
    expect(unfiledMetadata.title).toBe("未分类 · 我的文献库 · Patra");
  });

  it("未分类未登录：next 是 /library/unfiled", async () => {
    vi.mocked(lookupSession).mockResolvedValue({ status: "anonymous" });
    await expect(UnfiledPage(search())).rejects.toThrow(
      "NEXT_REDIRECT:/login?next=%2Flibrary%2Funfiled",
    );
  });
});

describe("/library/[collectionId] 页面", () => {
  const params = (collectionId: string) => ({
    params: Promise.resolve({ collectionId }),
    ...search(),
  });

  it("ID 不合格：404，不请求", async () => {
    await expect(CollectionPage(params("abc"))).rejects.toThrow("NEXT_NOT_FOUND");
    expect(fetchLibraryOverview).not.toHaveBeenCalled();
  });

  it("不是自己的收藏夹：404", async () => {
    await expect(CollectionPage(params("77"))).rejects.toThrow("NEXT_NOT_FOUND");
  });

  it("自己的：渲染", async () => {
    await expect(CollectionPage(params("9"))).resolves.toBeTruthy();
  });

  it("概览取不到：不判 404，交给页面里的错误块", async () => {
    vi.mocked(fetchLibraryOverview).mockResolvedValue({ kind: "unavailable" });
    await expect(CollectionPage(params("77"))).resolves.toBeTruthy();
  });

  it("标题带收藏夹名", async () => {
    expect((await collectionMetadata(params("9"))).title).toBe("组会 · 我的文献库 · Patra");
  });
});
