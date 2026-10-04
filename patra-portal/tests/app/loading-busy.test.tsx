import { render, screen } from "@testing-library/react";
import type { ComponentType } from "react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import JournalDetailLoading from "@/app/journals/[id]/loading";
import JournalsLoading from "@/app/journals/loading";
import PaperDetailLoading from "@/app/papers/[id]/loading";
import PapersLoading from "@/app/papers/loading";

vi.mock("next/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next/navigation")>();
  return { ...actual, usePathname: () => "/", useRouter: () => ({ push: vi.fn() }) };
});

describe("各路由加载骨架", () => {
  it.each<[string, ComponentType]>([
    ["/papers", PapersLoading],
    ["/journals", JournalsLoading],
    ["/papers/[id]", PaperDetailLoading],
    ["/journals/[id]", JournalDetailLoading],
  ])("%s 的 main 标记 aria-busy", (_, Loading) => {
    render(<Loading />);
    expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
  });
});
