// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { exportScopeSchema, fetchScopeKeys } from "@/lib/interaction/scope";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar({ patra_session: "patra_user_abc" });
  fetchMock.mockImplementation(async () => Response.json({ keys: ["11"], skippedUnavailable: 2 }));
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

describe("fetchScopeKeys", () => {
  it.each([
    [
      { kind: "library", view: "unfiled", sort: "added" } as const,
      "/patra-interaction/me/library/keys?view=unfiled&sort=added",
    ],
    [
      { kind: "collection", id: "9", sort: "journal" } as const,
      "/patra-interaction/me/collections/9/keys?sort=journal",
    ],
    [
      { kind: "public-collection", id: "9", sort: "published" } as const,
      "/patra-interaction/collections/9/keys?sort=published",
    ],
  ])("%o → %s", async (scope, path) => {
    await expect(fetchScopeKeys(scope)).resolves.toEqual({
      kind: "ok",
      data: { keys: ["11"], skippedUnavailable: 2 },
    });
    expect(fetchMock.mock.calls[0]?.[0]).toBe(`http://gw.test${path}`);
  });

  it("公开收藏夹的范围匿名也能取", async () => {
    resetCookieJar();
    await fetchScopeKeys({ kind: "public-collection", id: "9", sort: "added" });
    expect(fetchMock.mock.calls[0]?.[1]?.headers).not.toHaveProperty("Authorization");
  });

  it("超 5,000：too-large", async () => {
    fetchMock.mockImplementation(async () =>
      Response.json({ traits: ["EXPORT_TOO_LARGE"] }, { status: 422 }),
    );
    await expect(fetchScopeKeys({ kind: "library", view: "all", sort: "added" })).resolves.toEqual({
      kind: "too-large",
    });
  });
});

describe("exportScopeSchema", () => {
  it("收藏夹 ID 不合格时拒绝", () => {
    expect(
      exportScopeSchema.safeParse({ kind: "collection", id: "01", sort: "added" }).success,
    ).toBe(false);
    expect(exportScopeSchema.safeParse({ kind: "library", view: "x", sort: "added" }).success).toBe(
      false,
    );
  });
});
