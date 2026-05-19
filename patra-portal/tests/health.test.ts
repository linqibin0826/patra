import { describe, expect, it } from "vitest";
import { GET } from "@/app/api/health/route";

describe("GET /api/health", () => {
  it("returns 200 with status ok and ISO timestamp", async () => {
    const res = await GET();
    expect(res.status).toBe(200);

    const body = (await res.json()) as {
      status: string;
      version: string;
      timestamp: string;
    };
    expect(body.status).toBe("ok");
    expect(body.version).toMatch(/^\d+\.\d+\.\d+/);
    expect(() => new Date(body.timestamp).toISOString()).not.toThrow();
  });
});
