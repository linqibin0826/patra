// @vitest-environment node
import { makeLibraryItem } from "@tests/fixtures/interaction";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { collectPublication, removeItems, saveNote } from "@/lib/interaction/library-actions";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";

let jar: ReturnType<typeof resetCookieJar>;

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  jar = resetCookieJar({ patra_session: "patra_user_abc" });
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

describe("collectPublication", () => {
  it("PUT /me/library/publication/{key}，回条目与是否新建", async () => {
    fetchMock.mockResolvedValue(reply(200, { created: true, item: makeLibraryItem() }));
    await expect(collectPublication("11")).resolves.toEqual({
      ok: true,
      created: true,
      item: makeLibraryItem(),
    });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/me/library/publication/11");
    expect(call()[1]?.method).toBe("PUT");
  });

  it.each([
    [409, { traits: ["LIBRARY_FULL"] }, "library-full"],
    [404, { code: "INTERACTION-0404" }, "gone"],
    [503, { code: "INTERACTION-0503" }, "catalog-unavailable"],
    [503, { code: "GW-0503" }, "unavailable"],
    [500, {}, "unavailable"],
  ])("%i %o → %s", async (status, body, reason) => {
    fetchMock.mockResolvedValue(reply(status, body));
    await expect(collectPublication("11")).resolves.toEqual({ ok: false, reason });
  });

  it("401：signed-out，并清掉会话 Cookie", async () => {
    fetchMock.mockResolvedValue(reply(401, { code: "GW-0401" }));
    await expect(collectPublication("11")).resolves.toEqual({ ok: false, reason: "signed-out" });
    expect(jar.written).toEqual([CLEARED]);
  });

  it("没有会话：signed-out，不打网关", async () => {
    resetCookieJar();
    await expect(collectPublication("11")).resolves.toEqual({ ok: false, reason: "signed-out" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("key 不合格：unavailable，不打网关", async () => {
    await expect(collectPublication("01")).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("removeItems", () => {
  it("DELETE /me/library/items?itemIds=…，204 成功", async () => {
    fetchMock.mockResolvedValue(reply(204));
    await expect(removeItems(["501", "502"])).resolves.toEqual({ ok: true });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/me/library/items?itemIds=501,502");
    expect(call()[1]?.method).toBe("DELETE");
  });

  it("空数组或超过 100 个：unavailable，不打网关", async () => {
    await expect(removeItems([])).resolves.toEqual({ ok: false, reason: "unavailable" });
    const many = Array.from({ length: 101 }, (_, i) => String(i + 1));
    await expect(removeItems(many)).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("saveNote", () => {
  it("PUT 备注，回条目", async () => {
    const item = makeLibraryItem({ note: "组会讲" });
    fetchMock.mockResolvedValue(reply(200, item));
    await expect(saveNote("501", "组会讲")).resolves.toEqual({ ok: true, item });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/me/library/items/501/note");
    expect(JSON.parse(String(call()[1]?.body))).toEqual({ note: "组会讲" });
  });

  it("空白备注发 null（= 删除）", async () => {
    fetchMock.mockResolvedValue(reply(200, makeLibraryItem()));
    await saveNote("501", "  ");
    expect(JSON.parse(String(call()[1]?.body))).toEqual({ note: null });
  });

  it("422：字段错误", async () => {
    fetchMock.mockResolvedValue(reply(422, { errors: [{ field: "note", code: "TOO_LONG" }] }));
    await expect(saveNote("501", "x")).resolves.toEqual({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "note", code: "TOO_LONG" }],
    });
  });

  it("404：gone", async () => {
    fetchMock.mockResolvedValue(reply(404, {}));
    await expect(saveNote("501", "x")).resolves.toEqual({ ok: false, reason: "gone" });
  });
});
