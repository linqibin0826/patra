import { render, screen } from "@testing-library/react";
import { makeVenueDetail } from "@tests/fixtures/venue";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import JournalDetailPage from "@/app/journals/[id]/page";
import { ExperienceSection } from "@/components/experience/ExperienceSection";
import { JournalDetailView } from "@/components/portal/journal-detail/JournalDetailView";
import { fetchVenueDetail } from "@/lib/portal-api/venues";

const experiencesProps = vi.fn();
vi.mock("@/lib/portal-api/venues", () => ({ fetchVenueDetail: vi.fn() }));
vi.mock("@/components/portal/TopNav", () => ({ TopNav: () => null }));
vi.mock("@/components/portal/Footer", () => ({ Footer: () => null }));
vi.mock("@/components/experience/Experiences", () => ({
  Experiences: (props: unknown) => {
    experiencesProps(props);
    return <p>经验区内容</p>;
  },
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
    render(<ExperienceSection journalId="100" journalTitle="J Am Heart Assoc" sort="helpful" />);
    expect(experiencesProps).toHaveBeenCalledWith(expect.objectContaining({ sort: "helpful" }));
    const section = screen.getByText("经验区内容").closest("section");
    expect(section).toHaveAttribute("id", "experiences");
    expect(section).toHaveAttribute("aria-labelledby", "experiences-title");
  });

  it("页面按地址的 sort 交给经验区（同名参数多次不认）", async () => {
    vi.mocked(fetchVenueDetail).mockResolvedValue(makeVenueDetail());
    const sortOf = async (sort: string | string[] | undefined) => {
      const page = await JournalDetailPage({
        params: Promise.resolve({ id: "100" }),
        searchParams: Promise.resolve(sort === undefined ? {} : { sort }),
      });
      const { container } = render(page);
      container.remove();
      return experiencesProps.mock.lastCall?.[0];
    };
    expect(await sortOf("helpful")).toMatchObject({ sort: "helpful" });
    expect(await sortOf(undefined)).toMatchObject({ sort: "latest" });
    expect(await sortOf(["helpful", "latest"])).toMatchObject({ sort: "latest" });
  });
});
