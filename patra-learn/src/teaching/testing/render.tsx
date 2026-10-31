// 组件测试的渲染入口：每个用例后卸载（vitest 没开 globals，Testing Library 不会自动清理）
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

afterEach(cleanup);

export { act, fireEvent, render, screen, within } from "@testing-library/react";
