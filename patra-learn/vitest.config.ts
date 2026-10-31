// .ts 单测（内容规则、纯逻辑）跑 node 环境；教学组件的 .test.tsx 在文件头用
// `// @vitest-environment jsdom` 切到 jsdom，渲染组件、按用户看到的东西断言
import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: { include: ["src/**/*.test.{ts,tsx}"], environment: "node" },
  resolve: { alias: { "@": path.resolve(import.meta.dirname, "src") } },
});
