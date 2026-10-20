import { render, screen } from "@testing-library/react";
import { makePaperDetail } from "@tests/fixtures/paper";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { RecordSection } from "@/components/portal/paper-detail/RecordSection";

const author = (order: number, name: string) => ({
  order,
  first: order === 1,
  corresponding: false,
  name,
  affiliation: null,
});

describe("RecordSection", () => {
  const paper = makePaperDetail({
    doi: "10.1/x",
    pmid: "9",
    pmcid: "PMC1",
    authors: [author(1, "Ridwan Adib Md"), author(2, "Mohi Uddin Khandaker Mohammad")],
    dates: [{ type: "received", date: "2025-09-01" }],
    funding: [{ funder: "NSFC", grantId: "123", country: null }],
  });

  it("#record 区块，标题「文献档案」；完整作者与机构默认展开，其余收起", () => {
    const { container } = render(<RecordSection paper={paper} />);
    expect(container.querySelector("section#record")).not.toBeNull();
    expect(screen.getByRole("heading", { name: /文献档案/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /完整作者与机构/ })).toHaveAttribute(
      "aria-expanded",
      "true",
    );
    expect(screen.getByRole("button", { name: /其他标识符/ })).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    expect(screen.getByText("Ridwan Adib Md")).toBeVisible();
  });

  it("DOI、PMID 归入其他标识符；有资助时有「资助信息」", () => {
    render(<RecordSection paper={paper} />);
    expect(screen.getByRole("button", { name: /资助信息/ })).toBeInTheDocument();
    expect(screen.getByText("10.1/x", { exact: false })).toBeInTheDocument();
  });
});
