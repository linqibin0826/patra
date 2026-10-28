import { describe, expect, it } from "vitest";
import { createQueryClient } from "@/lib/query-client";
import { queryKeys } from "@/lib/query-keys";

const A = { userId: "1", email: "a@example.com" };
const B = { userId: "2", email: "b@example.com" };

/// 写入一份文献库缓存：概览、条目页、我的状态各一条
function seedLibrary(client: ReturnType<typeof createQueryClient>) {
  client.setQueryData(queryKeys.libraryOverview, { collections: [] });
  client.setQueryData(queryKeys.libraryItems("all", "added", 1), { items: [] });
  client.setQueryData(queryKeys.libraryStatus(["11"]), [{ key: "11", collected: true }]);
}

/// 文献库相关的缓存还剩几条
function libraryEntries(client: ReturnType<typeof createQueryClient>) {
  return [
    ...client.getQueryCache().findAll({ queryKey: queryKeys.library }),
    ...client.getQueryCache().findAll({ queryKey: queryKeys.libraryStatusAll }),
  ].length;
}

describe("createQueryClient · 当前用户变化时清掉文献库缓存", () => {
  it("退出登录（用户变成 null）后，文献库与我的状态缓存都被清掉", () => {
    const client = createQueryClient();
    client.setQueryData(queryKeys.currentUser, A);
    seedLibrary(client);
    client.setQueryData(queryKeys.currentUser, null);
    expect(libraryEntries(client)).toBe(0);
  });

  it("同一标签页换成另一个用户，A 的文献库缓存不会留给 B", () => {
    const client = createQueryClient();
    client.setQueryData(queryKeys.currentUser, A);
    seedLibrary(client);
    client.setQueryData(queryKeys.currentUser, B);
    expect(libraryEntries(client)).toBe(0);
  });

  it("同一用户重新取回（资料刷新），文献库缓存保留", () => {
    const client = createQueryClient();
    client.setQueryData(queryKeys.currentUser, A);
    seedLibrary(client);
    client.setQueryData(queryKeys.currentUser, { ...A, email: "a2@example.com" });
    expect(libraryEntries(client)).toBe(3);
  });

  it("当前用户第一次取回之前已有的文献库缓存不受影响", () => {
    const client = createQueryClient();
    seedLibrary(client);
    client.setQueryData(queryKeys.currentUser, A);
    expect(libraryEntries(client)).toBe(3);
  });
});

describe("createQueryClient · 当前用户变化时清掉讨论缓存", () => {
  it("退出登录后讨论区的列表、单楼、联想缓存都被清掉", () => {
    const client = createQueryClient();
    client.setQueryData(queryKeys.currentUser, A);
    client.setQueryData(queryKeys.discussionThreads("publication", "1", "asc"), { pages: [] });
    client.setQueryData(queryKeys.discussionSingle("publication", "1", "5"), { missing: true });
    client.setQueryData(queryKeys.discussionMentions("publication", "1", "zh"), []);
    client.setQueryData(queryKeys.currentUser, null);
    expect(client.getQueryCache().findAll({ queryKey: queryKeys.discussion })).toHaveLength(0);
  });

  it("第一次取回当前用户之前的讨论缓存不动", () => {
    const client = createQueryClient();
    client.setQueryData(queryKeys.discussionThreads("publication", "1", "asc"), { pages: [] });
    client.setQueryData(queryKeys.currentUser, A);
    expect(client.getQueryCache().findAll({ queryKey: queryKeys.discussion })).toHaveLength(1);
  });
});
