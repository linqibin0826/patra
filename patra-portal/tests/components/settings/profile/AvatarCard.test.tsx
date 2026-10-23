import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { AvatarCard } from "@/components/settings/profile/AvatarCard";
import type { CurrentUser } from "@/lib/auth/types";
import { checkAvatarFile, exportCrop } from "@/lib/identity/avatar-file";
import { removeAvatar, uploadAvatar } from "@/lib/identity/avatar-upload";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn(), refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/lib/identity/avatar-upload", () => ({ uploadAvatar: vi.fn(), removeAvatar: vi.fn() }));
vi.mock("@/lib/identity/avatar-file", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/identity/avatar-file")>()),
  checkAvatarFile: vi.fn(),
  exportCrop: vi.fn(),
}));
/// 裁剪库换成一个挂上就报一次裁剪区域的桩：jsdom 里量不出图片尺寸
vi.mock("react-easy-crop", async () => {
  const { useEffect } = await import("react");
  return {
    default: function CropperStub({
      onCropComplete,
    }: {
      onCropComplete: (a: unknown, b: unknown) => void;
    }) {
      // biome-ignore lint/correctness/useExhaustiveDependencies: 桩只在挂载时报一次
      useEffect(() => {
        onCropComplete(
          { x: 0, y: 0, width: 100, height: 100 },
          { x: 0, y: 0, width: 800, height: 800 },
        );
      }, []);
      return <div data-testid="cropper" />;
    },
  };
});

import { toast } from "sonner";

const AVATAR_URL = "/users/chen-yu/avatar?v=0123456789abcdef";

function renderCard(user: CurrentUser = USER) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(queryKeys.currentUser, user);
  const invalidate = vi.spyOn(client, "invalidateQueries");
  render(
    <QueryClientProvider client={client}>
      <AvatarCard user={user} />
    </QueryClientProvider>,
  );
  return { user: userEvent.setup(), client, invalidate };
}

async function chooseFile(type = "image/png") {
  const input = screen.getByLabelText("选择头像图片");
  await act(async () => {
    fireEvent.change(input, { target: { files: [new File(["x"], "me.png", { type })] } });
  });
}

beforeEach(() => {
  vi.stubGlobal(
    "URL",
    Object.assign(URL, { createObjectURL: vi.fn(() => "blob:x"), revokeObjectURL: vi.fn() }),
  );
  vi.mocked(checkAvatarFile).mockResolvedValue(null);
  vi.mocked(exportCrop).mockResolvedValue(new Blob(["png"], { type: "image/png" }));
  vi.mocked(uploadAvatar).mockResolvedValue({ ok: true, avatarUrl: AVATAR_URL });
  vi.mocked(removeAvatar).mockResolvedValue({ ok: true, avatarUrl: null });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("AvatarCard", () => {
  it("卡片：标题、说明、上传按钮与要求；没有头像时没有「移除」", () => {
    renderCard();
    expect(screen.getByRole("heading", { name: "头像" })).toBeInTheDocument();
    expect(screen.getByText("上传后立即生效，不需要点保存。")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "上传新头像" })).toBeInTheDocument();
    expect(
      screen.getByText("JPG、PNG 或 WebP，不超过 5 MB，短边至少 128 像素"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "移除" })).not.toBeInTheDocument();
  });

  it.each([
    ["type", "只支持 JPG、PNG 或 WebP 图片"],
    ["size", "图片不能超过 5 MB"],
    ["small", "图片太小了，短边至少 128 像素"],
    ["unreadable", "这个文件打不开，换一张试试"],
  ] as const)("本地校验 %s：卡片下方提示，不开对话框", async (issue, message) => {
    vi.mocked(checkAvatarFile).mockResolvedValue(issue);
    renderCard();
    await chooseFile("image/gif");
    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("合格：打开「调整头像」，有主页与导航栏两个预览", async () => {
    renderCard();
    await chooseFile();
    const dialog = await screen.findByRole("dialog", { name: "调整头像" });
    expect(dialog).toHaveTextContent("主页");
    expect(dialog).toHaveTextContent("导航栏");
    expect(screen.getByRole("slider", { name: "缩放" })).toBeInTheDocument();
  });

  it("上传成功：按钮「上传中…」，关闭对话框，提示「头像已更新」，当前用户换上新头像", async () => {
    let finish: (value: { ok: true; avatarUrl: string }) => void = () => {};
    vi.mocked(uploadAvatar).mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const { user, client } = renderCard();
    await chooseFile();
    await user.click(await screen.findByRole("button", { name: "使用这张头像" }));
    expect(await screen.findByRole("button", { name: "上传中…" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(vi.mocked(exportCrop).mock.calls[0]?.[1]).toEqual({
      x: 0,
      y: 0,
      width: 800,
      height: 800,
    });
    await act(async () => finish({ ok: true, avatarUrl: AVATAR_URL }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(toast.success).toHaveBeenCalledWith("头像已更新");
    expect(client.getQueryData<CurrentUser>(queryKeys.currentUser)?.avatarUrl).toBe(AVATAR_URL);
    expect(router.refresh).toHaveBeenCalled();
  });

  it("服务端拒绝：对话框不关，标题下提示条", async () => {
    vi.mocked(uploadAvatar).mockResolvedValue({ ok: false, reason: "rejected", code: "TOO_SMALL" });
    const { user } = renderCard();
    await chooseFile();
    await user.click(await screen.findByRole("button", { name: "使用这张头像" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("图片太小了，短边至少 128 像素");
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("冷静期：去取消注销页", async () => {
    vi.mocked(uploadAvatar).mockResolvedValue({ ok: false, reason: "pending-deletion" });
    const { user } = renderCard();
    await chooseFile();
    await user.click(await screen.findByRole("button", { name: "使用这张头像" }));
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith(
        "/account/deletion-pending?next=%2Fsettings%2Fprofile",
      ),
    );
  });

  it("有头像时可以移除：立即生效，提示「已恢复默认头像」", async () => {
    const { user, client } = renderCard({ ...USER, avatarUrl: AVATAR_URL });
    await user.click(screen.getByRole("button", { name: "移除" }));
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith("已恢复默认头像"));
    expect(client.getQueryData<CurrentUser>(queryKeys.currentUser)?.avatarUrl).toBeNull();
  });

  it("移除失败：卡片下方提示", async () => {
    vi.mocked(removeAvatar).mockResolvedValue({ ok: false, reason: "unavailable" });
    const { user } = renderCard({ ...USER, avatarUrl: AVATAR_URL });
    await user.click(screen.getByRole("button", { name: "移除" }));
    expect(await screen.findByText("没有移除成功，请稍后再试")).toBeInTheDocument();
  });
});
