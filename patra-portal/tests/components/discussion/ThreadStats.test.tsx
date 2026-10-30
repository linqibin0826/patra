import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ThreadStats } from "@/components/discussion/ThreadStats";

const NOW = Date.parse("2026-10-30T12:00:00Z");
const TWENTY_HOURS_AGO = "2026-10-29T16:00:00Z";

describe("ThreadStats", () => {
  it("should_show_reply_count_and_relative_last_reply_time", () => {
    const { container } = render(
      <ThreadStats replyCount={4} lastReplyAt={TWENTY_HOURS_AGO} now={NOW} />,
    );
    expect(container).toHaveTextContent("4 条回复 · 最后回复 20 小时前");
    const time = screen.getByText("20 小时前");
    expect(time.tagName).toBe("TIME");
    expect(time).toHaveAttribute("dateTime", TWENTY_HOURS_AGO);
    expect(time).toHaveAttribute("title", expect.stringMatching(/2026/));
  });

  it("should_group_thousands", () => {
    const { container } = render(
      <ThreadStats replyCount={1200} lastReplyAt={TWENTY_HOURS_AGO} now={NOW} />,
    );
    expect(container).toHaveTextContent("1,200 条回复");
  });

  it("should_render_nothing_without_replies", () => {
    const { container } = render(<ThreadStats replyCount={0} lastReplyAt={null} now={NOW} />);
    expect(container).toBeEmptyDOMElement();
  });
});
