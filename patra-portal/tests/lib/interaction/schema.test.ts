import { makeCollection, makeLibraryItem, makePublicCollection } from "@tests/fixtures/interaction";
import { describe, expect, it } from "vitest";
import {
  collectionSchema,
  libraryItemSchema,
  pageSchema,
  publicCollectionSchema,
} from "@/lib/interaction/schema";

describe("interaction schema", () => {
  it("收藏夹 ID 必须是字符串", () => {
    expect(collectionSchema.safeParse(makeCollection()).success).toBe(true);
    expect(collectionSchema.safeParse({ ...makeCollection(), collectionId: 9 }).success).toBe(
      false,
    );
  });

  it("条目在 catalog 整行消失后 key 为 null", () => {
    expect(libraryItemSchema.safeParse(makeLibraryItem({ key: null })).success).toBe(true);
  });

  it("本人视角、identity 不可用时 owner 为 null", () => {
    expect(publicCollectionSchema.safeParse(makePublicCollection({ owner: null })).success).toBe(
      true,
    );
  });

  it("已注销的所有者没有用户名与昵称", () => {
    const owner = {
      userId: "1",
      username: null,
      displayName: null,
      avatarUrl: null,
      avatarPalette: 0,
      deleted: true,
    };
    expect(publicCollectionSchema.safeParse(makePublicCollection({ owner })).success).toBe(true);
  });

  it("分页包装", () => {
    const page = { page: 1, pageSize: 50, total: 1, totalPages: 1, items: [makeLibraryItem()] };
    expect(pageSchema(libraryItemSchema).parse(page).items).toHaveLength(1);
  });
});
