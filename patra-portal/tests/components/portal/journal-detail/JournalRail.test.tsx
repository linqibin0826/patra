import "@testing-library/jest-dom/vitest";
import { render, screen } from "@testing-library/react";
import { makeVenueDetail } from "@tests/fixtures/venue";
import { describe, expect, it } from "vitest";
import { JournalRail } from "@/components/portal/journal-detail/JournalRail";
import { buildPapersHref } from "@/lib/portal-api/paper-search";
import { deriveMetrics } from "@/lib/portal-api/venue-derive";

describe("JournalRail", () => {
  it("侧栏引导到该刊文献列表，不再显示过时的版本边界说明", () => {
    const venue = makeVenueDetail({ id: "42" });
    render(<JournalRail venue={venue} metrics={deriveMetrics(venue)} />);
    expect(screen.queryByText(/后续版本/)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /该刊文献/ })).toHaveAttribute(
      "href",
      buildPapersHref({ venue: ["42"] }),
    );
  });
});
