import { QueryClient } from "@tanstack/react-query";
import { makeItemStatus } from "@tests/fixtures/interaction";
import { describe, expect, it } from "vitest";
import { invalidateLibrary, patchItemStatus } from "@/lib/library/cache";
import { queryKeys } from "@/lib/query-keys";

describe("patchItemStatus", () => {
  it("改到所有含这个 key 的批次，不碰不含它的批次", () => {
    const client = new QueryClient();
    client.setQueryData(queryKeys.libraryStatus(["11", "12"]), [
      makeItemStatus({ key: "11" }),
      makeItemStatus({ key: "12" }),
    ]);
    client.setQueryData(queryKeys.libraryStatus(["11"]), [makeItemStatus({ key: "11" })]);
    client.setQueryData(queryKeys.libraryStatus(["13"]), [makeItemStatus({ key: "13" })]);

    patchItemStatus(client, "11", { collected: true, itemId: "501" });

    expect(client.getQueryData(queryKeys.libraryStatus(["11", "12"]))).toEqual([
      makeItemStatus({ key: "11", collected: true, itemId: "501" }),
      makeItemStatus({ key: "12" }),
    ]);
    expect(client.getQueryData(queryKeys.libraryStatus(["11"]))).toEqual([
      makeItemStatus({ key: "11", collected: true, itemId: "501" }),
    ]);
    expect(client.getQueryData(queryKeys.libraryStatus(["13"]))).toEqual([
      makeItemStatus({ key: "13" }),
    ]);
  });
});

describe("invalidateLibrary", () => {
  it("让文献库的概览与条目页失效，不碰我的状态", async () => {
    const client = new QueryClient();
    client.setQueryData(queryKeys.libraryOverview, { total: 1 });
    client.setQueryData(queryKeys.libraryItems("all", "added", 1), { items: [] });
    client.setQueryData(queryKeys.libraryStatus(["11"]), []);
    await invalidateLibrary(client);
    expect(client.getQueryState(queryKeys.libraryOverview)?.isInvalidated).toBe(true);
    expect(client.getQueryState(queryKeys.libraryItems("all", "added", 1))?.isInvalidated).toBe(
      true,
    );
    expect(client.getQueryState(queryKeys.libraryStatus(["11"]))?.isInvalidated).toBe(false);
  });
});
