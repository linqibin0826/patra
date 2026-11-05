// @vitest-environment node
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

// 埋点 spec §2.3：Next 16 读不到预取请求头，靠 loading 边界保证预取只渲染到骨架、page.tsx 不执行，
// 所以文献详情与检索页的服务端事件不会因为预取多算。删掉这两个文件前先换一种防预取的办法
describe("文献详情与检索页保留 loading.tsx", () => {
  it.each(["src/app/papers/[id]/loading.tsx", "src/app/papers/(list)/loading.tsx"])(
    "%s",
    (file) => {
      expect(existsSync(resolve(__dirname, "../../..", file))).toBe(true);
    },
  );
});
