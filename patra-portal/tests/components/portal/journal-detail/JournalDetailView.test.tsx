import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeVenueDetail } from "@tests/fixtures/venue";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { JournalDetailView } from "@/components/portal/journal-detail/JournalDetailView";

const identifiers = [
  { type: "NLM", value: "9007735", primary: false },
  { type: "ISSN_L", value: "0923-7534", primary: false },
  { type: "CODEN", value: "ANONE2", primary: false },
  { type: "ISSN", value: "0923-7534", primary: false },
  { type: "ISSN", value: "1569-8041", primary: false },
  { type: "OPENALEX", value: "S41454044", primary: false },
];

describe("JournalDetailView 关系与标识", () => {
  it("标识符用可读键名，ISSN-L 与 ISSN 相同时只出现一次", async () => {
    render(<JournalDetailView venue={makeVenueDetail({ identifiers })} />);
    await userEvent.setup().click(screen.getByRole("button", { name: /关系与标识/ }));

    expect(screen.getAllByRole("button", { name: /^复制 .+：0923-7534$/ })).toHaveLength(1);
    expect(screen.getByRole("button", { name: "复制 ISSN：0923-7534" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "复制 NLM ID：9007735" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "复制 OpenAlex：S41454044" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /ISSN_L|OPENALEX/ })).not.toBeInTheDocument();
  });
});
