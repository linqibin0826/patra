// 单测只测 .ts 纯逻辑（content/lib），node 环境即可
import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: { include: ["src/**/*.test.ts"], environment: "node" },
  resolve: { alias: { "@": path.resolve(import.meta.dirname, "src") } },
});
