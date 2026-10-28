import { act, waitFor } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import { renderDiscussion } from "@tests/support/discussion";
import { describe, expect, it } from "vitest";
import { useRefreshOnIdentityChange } from "@/lib/experience/use-identity-refresh";
import { queryKeys } from "@/lib/query-keys";

function Probe() {
  useRefreshOnIdentityChange();
  return null;
}

describe("useRefreshOnIdentityChange", () => {
  it("第一次取回当前用户不刷新，之后换号刷新", async () => {
    const { client, router } = renderDiscussion(<Probe />);
    await act(async () => {});
    expect(router.refresh).not.toHaveBeenCalled();
    act(() => client.setQueryData(queryKeys.currentUser, { ...USER, userId: "other" }));
    await waitFor(() => expect(router.refresh).toHaveBeenCalledTimes(1));
  });

  it("同一个人资料刷新不刷新页面", async () => {
    const { client, router } = renderDiscussion(<Probe />);
    await act(async () => {});
    act(() => client.setQueryData(queryKeys.currentUser, { ...USER, displayName: "新昵称" }));
    await act(async () => {});
    expect(router.refresh).not.toHaveBeenCalled();
  });
});
