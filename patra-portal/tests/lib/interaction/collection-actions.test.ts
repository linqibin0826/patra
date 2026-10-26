// @vitest-environment node
import { makeCollection, makeCopyResult } from "@tests/fixtures/interaction";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  addToCollection,
  copyCollection,
  createCollection,
  deleteCollection,
  removeFromCollection,
  setVisibility,
  updateCollection,
} from "@/lib/interaction/collection-actions";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar({ patra_session: "patra_user_abc" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const reply = (status: number, body?: unknown) =>
  body === undefined ? new Response(null, { status }) : Response.json(body, { status });
const call = () => fetchMock.mock.calls[0] ?? [];
const INPUT = { name: "组会", description: "" };

describe("createCollection", () => {
  it("POST /me/collections，空简介发 null，201 回收藏夹", async () => {
    fetchMock.mockResolvedValue(reply(201, makeCollection()));
    await expect(createCollection(INPUT)).resolves.toEqual({
      ok: true,
      collection: makeCollection(),
    });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/me/collections");
    expect(JSON.parse(String(call()[1]?.body))).toEqual({ name: "组会", description: null });
  });

  it.each([
    [409, { traits: ["COLLECTION_NAME_TAKEN"] }, { ok: false, reason: "name-taken" }],
    [409, { traits: ["COLLECTION_LIMIT_REACHED"] }, { ok: false, reason: "collection-limit" }],
    [
      422,
      { errors: [{ field: "name", code: "TOO_LONG" }] },
      { ok: false, reason: "invalid-fields", fieldErrors: [{ field: "name", code: "TOO_LONG" }] },
    ],
  ])("%i %o", async (status, body, result) => {
    fetchMock.mockResolvedValue(reply(status, body));
    await expect(createCollection(INPUT)).resolves.toEqual(result);
  });
});

describe("updateCollection", () => {
  it("PUT /me/collections/{id}；404 是 gone", async () => {
    fetchMock.mockResolvedValueOnce(reply(200, makeCollection()));
    await expect(updateCollection("9", INPUT)).resolves.toMatchObject({ ok: true });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/me/collections/9");
    expect(call()[1]?.method).toBe("PUT");
    fetchMock.mockResolvedValueOnce(reply(404, {}));
    await expect(updateCollection("9", INPUT)).resolves.toEqual({ ok: false, reason: "gone" });
  });

  it("ID 不合格：unavailable，不打网关", async () => {
    await expect(updateCollection("abc", INPUT)).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("setVisibility", () => {
  it("PUT /visibility", async () => {
    fetchMock.mockResolvedValue(reply(200, makeCollection({ visibility: "PUBLIC" })));
    await expect(setVisibility("9", "PUBLIC")).resolves.toMatchObject({ ok: true });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/me/collections/9/visibility");
    expect(JSON.parse(String(call()[1]?.body))).toEqual({ visibility: "PUBLIC" });
  });

  it.each([
    [["EMAIL_UNVERIFIED"], "email-unverified"],
    [["ACCOUNT_RESTRICTED"], "account-restricted"],
    [["EMAIL_UNVERIFIED", "ACCOUNT_RESTRICTED"], "account-restricted"],
  ])("409 %o → %s", async (traits, reason) => {
    fetchMock.mockResolvedValue(reply(409, { traits }));
    await expect(setVisibility("9", "PUBLIC")).resolves.toEqual({ ok: false, reason });
  });

  it("identity 不可用（503）：unavailable", async () => {
    fetchMock.mockResolvedValue(reply(503, { traits: ["DEP_UNAVAILABLE"] }));
    await expect(setVisibility("9", "PUBLIC")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
  });
});

describe("deleteCollection", () => {
  it("DELETE，204 成功", async () => {
    fetchMock.mockResolvedValue(reply(204));
    await expect(deleteCollection("9")).resolves.toEqual({ ok: true });
    expect(call()[1]?.method).toBe("DELETE");
  });
});

describe("addToCollection / removeFromCollection", () => {
  it("POST 条目，回放进与因满跳过的篇数", async () => {
    const result = { added: 2, alreadyIn: 0, skippedFull: 1, skippedNotInLibrary: 0 };
    fetchMock.mockResolvedValue(reply(200, result));
    await expect(addToCollection("9", ["501", "502"])).resolves.toEqual({ ok: true, result });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/me/collections/9/items");
    expect(JSON.parse(String(call()[1]?.body))).toEqual({ itemIds: ["501", "502"] });
  });

  it("DELETE 条目", async () => {
    fetchMock.mockResolvedValue(reply(204));
    await expect(removeFromCollection("9", ["501"])).resolves.toEqual({ ok: true });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/me/collections/9/items?itemIds=501");
  });

  it("收藏夹已删除：gone", async () => {
    fetchMock.mockResolvedValue(reply(404, {}));
    await expect(addToCollection("9", ["501"])).resolves.toEqual({ ok: false, reason: "gone" });
  });
});

describe("copyCollection", () => {
  it("POST /collections/{id}/copies", async () => {
    fetchMock.mockResolvedValue(reply(201, makeCopyResult()));
    await expect(copyCollection("9")).resolves.toEqual({ ok: true, result: makeCopyResult() });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/collections/9/copies");
    expect(call()[1]?.method).toBe("POST");
  });

  it.each([
    [404, {}, "not-visible"],
    [409, { traits: ["LIBRARY_FULL"] }, "library-full"],
    [409, { traits: ["COLLECTION_LIMIT_REACHED"] }, "collection-limit"],
  ])("%i %o → %s", async (status, body, reason) => {
    fetchMock.mockResolvedValue(reply(status, body));
    await expect(copyCollection("9")).resolves.toEqual({ ok: false, reason });
  });
});
