import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import { postReauth } from "@/lib/reauth/post-reauth";

afterEach(() => vi.unstubAllGlobals());

describe("postReauth", () => {
  it("POST 同源的 /api/auth/reauth，返回响应体里的结果", async () => {
    const fetchMock = vi.fn(async () => Response.json({ ok: true, user: USER }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(postReauth("secret-pass")).resolves.toEqual({ ok: true, user: USER });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/auth/reauth");
    expect(init.method).toBe("POST");
    expect(JSON.parse(String(init.body))).toEqual({ password: "secret-pass" });
  });

  it("失败的状态码也读响应体", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ ok: false, reason: "wrong-password" }, { status: 401 })),
    );
    await expect(postReauth("x")).resolves.toEqual({ ok: false, reason: "wrong-password" });
  });

  it.each([
    ["网络断开", () => Promise.reject(new TypeError("failed"))],
    ["响应体不是结果", () => Promise.resolve(new Response("<html>", { status: 502 }))],
  ])("%s：unavailable", async (_label, reply) => {
    vi.stubGlobal("fetch", vi.fn(reply));
    await expect(postReauth("x")).resolves.toEqual({ ok: false, reason: "unavailable" });
  });
});
