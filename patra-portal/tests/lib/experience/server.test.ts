// @vitest-environment node
import { makeExperience, makeSummary } from "@tests/fixtures/experience";
import { afterEach, describe, expect, it, vi } from "vitest";
import { prefetchJournalExperiences } from "@/lib/experience/server";
import { fetchExperienceSummary, fetchJournalExperiences } from "@/lib/interaction/experience";
import { queryKeys } from "@/lib/query-keys";

vi.mock("server-only", () => ({}));
vi.mock("@/lib/interaction/experience", () => ({
  fetchExperienceSummary: vi.fn(),
  fetchJournalExperiences: vi.fn(),
}));

afterEach(() => {
  vi.mocked(fetchExperienceSummary).mockReset();
  vi.mocked(fetchJournalExperiences).mockReset();
});

const keysOf = (state: Awaited<ReturnType<typeof prefetchJournalExperiences>>) =>
  state.queries.map((query) => query.queryKey);

describe("prefetchJournalExperiences：期刊页经验区首屏", () => {
  it("汇总（近 3 年）与列表第一页都取到时都放进去", async () => {
    vi.mocked(fetchExperienceSummary).mockResolvedValue({ kind: "ok", data: makeSummary() });
    const page = { items: [makeExperience()], nextCursor: null, total: 1 };
    vi.mocked(fetchJournalExperiences).mockResolvedValue({ kind: "ok", data: page });
    const state = await prefetchJournalExperiences("100", "latest");
    expect(fetchExperienceSummary).toHaveBeenCalledWith("100", "3y");
    expect(fetchJournalExperiences).toHaveBeenCalledWith("100", "latest");
    expect(keysOf(state)).toEqual([
      queryKeys.experienceSummary("100", "3y"),
      queryKeys.experienceList("100", "latest"),
    ]);
    expect(state.queries[1]?.state.data).toEqual({ pages: [page], pageParams: [null] });
  });

  it("一个取不到时只放取到的那份", async () => {
    vi.mocked(fetchExperienceSummary).mockResolvedValue({ kind: "unavailable" });
    vi.mocked(fetchJournalExperiences).mockResolvedValue({
      kind: "ok",
      data: { items: [], nextCursor: null, total: 0 },
    });
    expect(keysOf(await prefetchJournalExperiences("100", "helpful"))).toEqual([
      queryKeys.experienceList("100", "helpful"),
    ]);
  });
});
