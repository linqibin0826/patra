import { screen } from "@testing-library/react";
import { renderWithQuery } from "@tests/support/render-with-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("@/lib/interaction/stats", () => ({ fetchPaperStats: vi.fn() }));

import { PaperInteractionFacts } from "@/components/portal/paper-detail/PaperInteractionFacts";
import { fetchPaperStats } from "@/lib/interaction/stats";

const cells = () =>
  screen
    .queryAllByRole("term")
    .map((dt) => `${dt.textContent} ${dt.nextElementSibling?.textContent ?? ""}`);

async function renderFacts() {
  renderWithQuery(<dl>{await PaperInteractionFacts({ paperId: "11" })}</dl>);
}

describe("PaperInteractionFacts", () => {
  beforeEach(() => vi.mocked(fetchPaperStats).mockReset());

  it("should_show_saved_and_discussion_cells_for_this_paper", async () => {
    vi.mocked(fetchPaperStats).mockResolvedValue(
      new Map([["11", { collectorCount: 23, discussionCount: 7 }]]),
    );
    await renderFacts();
    expect(fetchPaperStats).toHaveBeenCalledWith(["11"]);
    expect(cells()).toEqual(["收藏 23 人", "讨论 7 条"]);
  });

  it("should_show_only_discussion_when_no_collectors", async () => {
    vi.mocked(fetchPaperStats).mockResolvedValue(
      new Map([["11", { collectorCount: 0, discussionCount: 3 }]]),
    );
    await renderFacts();
    expect(cells()).toEqual(["讨论 3 条"]);
  });

  it("should_render_no_cells_when_unavailable", async () => {
    vi.mocked(fetchPaperStats).mockResolvedValue(null);
    await renderFacts();
    expect(cells()).toEqual([]);
  });
});
