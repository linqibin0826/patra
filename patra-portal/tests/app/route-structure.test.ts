import { existsSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const APP = join(__dirname, "../../src/app");

describe("路由结构", () => {
  // loading.tsx 包住所在目录及全部子目录：列表骨架若放在 papers/ 下，
  // 从首页等别处进入 /papers/[id] 时会先闪一下列表骨架。列表页放进路由组 (list)，与 [id] 平级。
  it.each(["papers", "journals"])("%s 的列表骨架只包列表页，不包详情页", (segment) => {
    expect(existsSync(join(APP, segment, "loading.tsx"))).toBe(false);
    expect(existsSync(join(APP, segment, "(list)", "loading.tsx"))).toBe(true);
    expect(existsSync(join(APP, segment, "(list)", "page.tsx"))).toBe(true);
    expect(existsSync(join(APP, segment, "[id]", "page.tsx"))).toBe(true);
  });
});
