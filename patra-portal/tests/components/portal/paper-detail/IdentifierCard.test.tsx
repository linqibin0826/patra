import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makePaperDetail } from "@tests/fixtures/paper";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { IdentifierCard } from "@/components/portal/paper-detail/IdentifierCard";

describe("IdentifierCard", () => {
  const full = makePaperDetail({
    doi: "10.1177/03000605251411752",
    pmid: "41575322",
    pmcid: "PMC12833109",
  });

  it("DOI、PMID、PMCID 三行，分别链到 doi.org、PubMed、PMC", () => {
    render(<IdentifierCard paper={full} />);
    expect(screen.getByRole("link", { name: "10.1177/03000605251411752" })).toHaveAttribute(
      "href",
      "https://doi.org/10.1177/03000605251411752",
    );
    expect(screen.getByRole("link", { name: "41575322" })).toHaveAttribute(
      "href",
      "https://pubmed.ncbi.nlm.nih.gov/41575322/",
    );
    expect(screen.getByRole("link", { name: "PMC12833109" })).toHaveAttribute(
      "href",
      "https://pmc.ncbi.nlm.nih.gov/articles/PMC12833109/",
    );
  });

  it("点「复制 DOI」写入剪贴板，并给读屏一句「已复制」", async () => {
    const user = userEvent.setup();
    const writeText = vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue();
    render(<IdentifierCard paper={full} />);
    await user.click(screen.getByRole("button", { name: "复制 DOI" }));
    expect(writeText).toHaveBeenCalledWith("10.1177/03000605251411752");
    expect(await screen.findByRole("status")).toHaveTextContent("已复制");
  });

  it("值为空的行不显示；三个都空时整卡不渲染", () => {
    const { container, rerender } = render(
      <IdentifierCard paper={makePaperDetail({ doi: "10.1/x", pmid: null, pmcid: null })} />,
    );
    expect(screen.queryByRole("button", { name: "复制 PMID" })).toBeNull();
    rerender(<IdentifierCard paper={makePaperDetail({ doi: null, pmid: null, pmcid: null })} />);
    expect(container).toBeEmptyDOMElement();
  });
});
