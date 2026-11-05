// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";
import { GET } from "@/app/api/me/reading-history/route";
import { fetchReadingHistory } from "@/lib/analytics/history";

vi.mock("@/lib/analytics/history", () => ({ fetchReadingHistory: vi.fn() }));

afterEach(() => vi.mocked(fetchReadingHistory).mockReset());

const get = (query = "") =>
  GET(new Request(`http://localhost:4000/api/me/reading-history${query}`));

describe("GET /api/me/reading-history", () => {
  it("200：回拼好卡片的一页，游标原样转下去", async () => {
    const data = { recordingEnabled: true, items: [], nextCursor: null };
    vi.mocked(fetchReadingHistory).mockResolvedValue({ kind: "ok", data });
    const res = await get("?cursor=abc");
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual(data);
    expect(fetchReadingHistory).toHaveBeenCalledWith("abc");
  });

  it("没有游标时不传", async () => {
    vi.mocked(fetchReadingHistory).mockResolvedValue({ kind: "unavailable" });
    await get();
    expect(fetchReadingHistory).toHaveBeenCalledWith(undefined);
  });

  it("未登录 401、取不到 502", async () => {
    vi.mocked(fetchReadingHistory).mockResolvedValue({ kind: "signed-out" });
    expect((await get()).status).toBe(401);
    vi.mocked(fetchReadingHistory).mockResolvedValue({ kind: "unavailable" });
    expect((await get()).status).toBe(502);
  });
});
