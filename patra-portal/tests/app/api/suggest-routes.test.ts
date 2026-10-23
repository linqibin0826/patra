import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/portal-api/suggest", () => ({
  fetchOrganizationSuggestions: vi.fn(),
  fetchMeshSuggestions: vi.fn(),
}));

import { GET as getMesh } from "@/app/api/mesh/suggest/route";
import { GET as getOrganizations } from "@/app/api/organizations/suggest/route";
import { fetchMeshSuggestions, fetchOrganizationSuggestions } from "@/lib/portal-api/suggest";

const request = (path: string, q: string) =>
  new Request(`http://localhost:4000${path}?q=${encodeURIComponent(q)}`);

afterEach(() => vi.clearAllMocks());

describe("GET /api/organizations/suggest", () => {
  it.each([["协"], ["  a "], ["x".repeat(101)]])(
    "q 归一后不在 2–100 个码点（%j）：空列表，不打网关",
    async (q) => {
      const res = await getOrganizations(request("/api/organizations/suggest", q));
      await expect(res.json()).resolves.toEqual({ items: [] });
      expect(fetchOrganizationSuggestions).not.toHaveBeenCalled();
    },
  );

  it("转发归一后的 q，回 { items }", async () => {
    vi.mocked(fetchOrganizationSuggestions).mockResolvedValue([]);
    const res = await getOrganizations(request("/api/organizations/suggest", " 北京　协和 "));
    expect(fetchOrganizationSuggestions).toHaveBeenCalledWith("北京 协和");
    await expect(res.json()).resolves.toEqual({ items: [] });
  });

  it("网关失败：502 + 空列表", async () => {
    vi.mocked(fetchOrganizationSuggestions).mockRejectedValue(new Error("boom"));
    const res = await getOrganizations(request("/api/organizations/suggest", "协和"));
    expect(res.status).toBe(502);
    await expect(res.json()).resolves.toEqual({ items: [] });
  });
});

describe("GET /api/mesh/suggest", () => {
  it("转发并回 { items }", async () => {
    const items = [{ ui: "D009369", name: "Neoplasms", matchedTerm: null, treeNumbers: ["C04"] }];
    vi.mocked(fetchMeshSuggestions).mockResolvedValue(items);
    const res = await getMesh(request("/api/mesh/suggest", "neo"));
    await expect(res.json()).resolves.toEqual({ items });
  });

  it("q 太短：空列表，不打网关", async () => {
    const res = await getMesh(request("/api/mesh/suggest", "n"));
    await expect(res.json()).resolves.toEqual({ items: [] });
    expect(fetchMeshSuggestions).not.toHaveBeenCalled();
  });
});
