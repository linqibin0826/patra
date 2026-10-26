// @vitest-environment node
import { makePaperDetail } from "@tests/fixtures/paper";
import { makeVenueDetail } from "@tests/fixtures/venue";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/portal-api/publications", () => ({ fetchPublicationDetail: vi.fn() }));
vi.mock("@/lib/portal-api/venues", () => ({ fetchVenueDetail: vi.fn() }));
vi.mock("@/lib/public-url", () => ({
  publicUrl: vi.fn(async (path: string) => `http://mini${path}`),
}));

import { generateMetadata as journalMetadata } from "@/app/journals/[id]/page";
import { generateMetadata as paperMetadata } from "@/app/papers/[id]/page";
import { fetchPublicationDetail } from "@/lib/portal-api/publications";
import { fetchVenueDetail } from "@/lib/portal-api/venues";

const params = (id: string) => ({ params: Promise.resolve({ id }) });

describe("/papers/[id] 页面信息", () => {
  it("returns paper metadata with absolute canonical", async () => {
    vi.mocked(fetchPublicationDetail).mockResolvedValue(
      makePaperDetail({ title: "Heart failure." }),
    );
    const meta = await paperMetadata(params("1"));
    expect(meta.title).toBe("Heart failure · Patra");
    expect(meta.alternates?.canonical).toBe("http://mini/papers/1");
    expect(fetchPublicationDetail).toHaveBeenCalledWith("1");
  });

  it("returns empty when paper missing", async () => {
    vi.mocked(fetchPublicationDetail).mockResolvedValue(null);
    expect(await paperMetadata(params("1"))).toEqual({});
  });
});

describe("/journals/[id] 页面信息", () => {
  it("returns journal metadata with absolute canonical", async () => {
    vi.mocked(fetchVenueDetail).mockResolvedValue(makeVenueDetail({ title: "Lancet" }));
    const meta = await journalMetadata(params("7"));
    expect(meta.title).toBe("Lancet · Patra");
    expect(meta.alternates?.canonical).toBe("http://mini/journals/7");
  });

  it("returns empty when journal missing", async () => {
    vi.mocked(fetchVenueDetail).mockResolvedValue(null);
    expect(await journalMetadata(params("7"))).toEqual({});
  });
});
