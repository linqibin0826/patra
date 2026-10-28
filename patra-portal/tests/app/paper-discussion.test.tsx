import { render, screen } from "@testing-library/react";
import { makePaperDetail } from "@tests/fixtures/paper";
import { renderWithQuery } from "@tests/support/render-with-query";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { DiscussionSection } from "@/components/discussion/DiscussionSection";
import { PublicationDetailView } from "@/components/portal/paper-detail/PublicationDetailView";
import { PAPER_DISCUSSION_COPY } from "@/lib/discussion/copy";

vi.mock("@/components/discussion/Discussion", () => ({
  Discussion: () => <p>讨论区内容</p>,
}));

describe("文献页讨论区", () => {
  it("讨论区插槽在主栏最后、全文区块之后", () => {
    renderWithQuery(
      <PublicationDetailView
        paper={makePaperDetail()}
        shareLink="http://patra.test/papers/1"
        fullText={<div data-testid="fulltext">全文</div>}
        discussion={<div data-testid="discussion">讨论</div>}
      />,
    );
    const fullText = screen.getByTestId("fulltext");
    const discussion = screen.getByTestId("discussion");
    expect(
      fullText.compareDocumentPosition(discussion) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(discussion.parentElement).toBe(fullText.parentElement);
  });

  it("DiscussionSection：section#discussion，由讨论区里的标题命名", () => {
    render(
      <DiscussionSection
        subject={{ type: "publication", key: "1" }}
        view={{ mode: "list", sort: "asc" }}
        copy={PAPER_DISCUSSION_COPY}
        basePath="/papers/1"
      />,
    );
    const section = screen.getByText("讨论区内容").closest("section");
    expect(section).toHaveAttribute("id", "discussion");
    expect(section).toHaveAttribute("aria-labelledby", "discussion-title");
  });
});
