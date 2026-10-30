import { render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("@/lib/interaction/stats", () => ({ fetchHelpfulReceived: vi.fn() }));

import { ProfileHelpfulReceived } from "@/components/profile/ProfileHelpfulReceived";
import { fetchHelpfulReceived } from "@/lib/interaction/stats";

async function renderLikes(count: number | null) {
  vi.mocked(fetchHelpfulReceived).mockResolvedValue(count);
  return render(<div>{await ProfileHelpfulReceived({ userId: "42" })}</div>).container;
}

describe("ProfileHelpfulReceived", () => {
  beforeEach(() => vi.mocked(fetchHelpfulReceived).mockReset());

  it("should_show_likes_received_for_this_user", async () => {
    const container = await renderLikes(7);
    expect(fetchHelpfulReceived).toHaveBeenCalledWith("42");
    expect(container).toHaveTextContent("获赞 7");
  });

  it("should_group_thousands", async () => {
    expect(await renderLikes(1234)).toHaveTextContent("获赞 1,234");
  });

  it.each([0, null])("should_render_nothing_when_%s", async (count) => {
    expect((await renderLikes(count)).firstChild).toBeEmptyDOMElement();
  });
});
