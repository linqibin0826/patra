import { screen, within } from "@testing-library/react";
import { makeRevision } from "@tests/fixtures/experience";
import { renderDiscussion } from "@tests/support/discussion";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import userEvent from "@testing-library/user-event";
import { PostRevisionDialog } from "@/components/experience/PostRevisionDialog";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});

function renderDialog() {
  return renderDiscussion(<PostRevisionDialog postId="9" open onOpenChange={() => {}} />);
}

describe("PostRevisionDialog（spec 8.3）", () => {
  it("当前版本在前，列出字段改动与正文增删；最初版本只列值", async () => {
    fetchMock.mockResolvedValue(
      Response.json({
        items: [
          makeRevision({
            current: true,
            writtenAt: "2026-06-10T02:00:00Z",
            firstDecision: "MINOR_REVISION",
            body: "审稿人有三位",
          }),
          makeRevision({ writtenAt: "2026-06-03T02:00:00Z", body: "审稿人有两位" }),
        ],
      }),
    );
    renderDialog();
    const dialog = await screen.findByRole("dialog", { name: "修改历史" });
    await within(dialog).findAllByRole("listitem");
    const versions = [...dialog.querySelectorAll("ol > li")];
    expect(versions).toHaveLength(2);
    expect(versions[0]).toHaveTextContent("当前版本");
    expect(versions[0]).toHaveTextContent("首轮结果：大修 → 小修");
    expect(versions[0]?.querySelector("ins")).toHaveTextContent("三");
    expect(versions[0]?.querySelector("del")).toHaveTextContent("两");
    expect(versions[1]).toHaveTextContent("最初版本");
    expect(versions[1]).toHaveTextContent("首轮结果大修");
    expect(versions[1]?.querySelector("ins")).toBeNull();
  });

  it("经验已删除", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "not-found" }, { status: 404 }));
    renderDialog();
    expect(await screen.findByText("这条投稿经验已被删除。")).toBeInTheDocument();
  });

  it("取不到时可以重试", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    const user = userEvent.setup();
    renderDialog();
    expect(await screen.findByText("修改历史暂时打不开")).toBeInTheDocument();
    fetchMock.mockResolvedValue(Response.json({ items: [makeRevision({ current: true })] }));
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByText(/最初版本/)).toBeInTheDocument();
  });
});
