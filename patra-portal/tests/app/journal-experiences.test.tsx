import { render, screen } from "@testing-library/react";
import { makeVenueDetail } from "@tests/fixtures/venue";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ExperienceSection } from "@/components/experience/ExperienceSection";
import { JournalDetailView } from "@/components/portal/journal-detail/JournalDetailView";

vi.mock("@/components/experience/Experiences", () => ({
  Experiences: () => <p>经验区内容</p>,
}));

describe("期刊页经验区（spec 6.2）", () => {
  it("插槽在「影响力速览」之后、「期刊定位」之前", () => {
    render(
      <JournalDetailView
        venue={makeVenueDetail({ impactFactor: 5.1, jcrQuartile: "Q1" })}
        experiences={<div data-testid="experiences">经验区</div>}
      />,
    );
    const slot = screen.getByTestId("experiences");
    const metrics = screen.getByRole("region", { name: "影响力速览" });
    expect(metrics.compareDocumentPosition(slot) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const positioning = screen.getByRole("region", { name: "定位与范围" });
    expect(
      slot.compareDocumentPosition(positioning) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("ExperienceSection：section#experiences，由区块标题命名", () => {
    render(<ExperienceSection journalId="100" journalTitle="J Am Heart Assoc" />);
    const section = screen.getByText("经验区内容").closest("section");
    expect(section).toHaveAttribute("id", "experiences");
    expect(section).toHaveAttribute("aria-labelledby", "experiences-title");
  });
});
