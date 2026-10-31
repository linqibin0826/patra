import { describe, expect, it } from "vitest";
import { mdxComponents } from "@/mdx-components";
import { componentRegistry } from "./registry";

describe("组件登记表与实现", () => {
  it("登记表里的每个组件都有实现，实现也都登记过", () => {
    expect(Object.keys(mdxComponents).sort()).toEqual(Object.keys(componentRegistry).sort());
  });
});
