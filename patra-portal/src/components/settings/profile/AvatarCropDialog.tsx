"use client";

import { Minus, Plus } from "lucide-react";
import { type KeyboardEvent, useEffect, useMemo, useState } from "react";
import Cropper, { type Area, type Point } from "react-easy-crop";
import { FormAlert } from "@/components/auth/FormAlert";
import { Dialog } from "@/components/ui/dialog";
import { Slider } from "@/components/ui/slider";
import { type CropArea, exportCrop } from "@/lib/identity/avatar-file";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";

/// 方向键平移 4px，按住 Shift 16px（简报 §3.7）
const STEP = 4;
const FAST_STEP = 16;
const ZOOM_STEP = 0.1;
/// 缩放上限：短边缩到 128px 为止
const MIN_EDGE = 128;

interface AvatarCropDialogProps {
  /// 通过本地校验的原图；null 时对话框关闭
  file: File | null;
  onCancel: () => void;
  /// 上传裁好的正方形，成功返回 null，失败返回提示条文案（对话框不关）
  onUpload: (blob: Blob, fileName: string) => Promise<string | null>;
}

/// 头像裁剪对话框（简报 §3.7）：正方形视窗加圆形遮罩（最终显示是圆，存的是正方形），拖动平移、滑块 /
/// 滚轮 / 双指缩放；右侧（移动在下方）96px 与 36px 两个预览。上传中按钮「上传中…」、不能关。
export function AvatarCropDialog({ file, onCancel, onUpload }: AvatarCropDialogProps) {
  const src = useMemo(() => (file ? URL.createObjectURL(file) : null), [file]);
  const [crop, setCrop] = useState<Point>({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [maxZoom, setMaxZoom] = useState(3);
  const [area, setArea] = useState<Area | null>(null);
  const [pixels, setPixels] = useState<CropArea | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setCrop({ x: 0, y: 0 });
    setZoom(1);
    setError(null);
    return () => {
      if (src) URL.revokeObjectURL(src);
    };
  }, [src]);

  async function confirm() {
    if (!file || !src || !pixels || busy) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const blob = await exportCrop(file, pixels, file.type);
      const message = await onUpload(
        blob,
        blob.type === "image/jpeg" ? "avatar.jpg" : "avatar.png",
      );
      setError(message);
    } catch {
      setError("头像没有上传成功，请稍后再试");
    } finally {
      setBusy(false);
    }
  }

  /// 裁剪库自带方向键平移（4px）；这里补上 Shift 加速与 + / - 缩放
  function onKeyDownCapture(event: KeyboardEvent<HTMLDivElement>) {
    const offsets: Record<string, Point> = {
      ArrowLeft: { x: FAST_STEP, y: 0 },
      ArrowRight: { x: -FAST_STEP, y: 0 },
      ArrowUp: { x: 0, y: FAST_STEP },
      ArrowDown: { x: 0, y: -FAST_STEP },
    };
    const offset = offsets[event.key];
    if (event.shiftKey && offset) {
      event.preventDefault();
      event.stopPropagation();
      setCrop((current) => ({ x: current.x + offset.x, y: current.y + offset.y }));
    } else if (event.key === "+" || event.key === "=") {
      event.preventDefault();
      setZoom((current) => Math.min(maxZoom, current + ZOOM_STEP));
    } else if (event.key === "-") {
      event.preventDefault();
      setZoom((current) => Math.max(1, current - ZOOM_STEP));
    }
  }

  return (
    <Dialog
      open={file !== null}
      onOpenChange={(open) => {
        if (!open) onCancel();
      }}
      title="调整头像"
      size="lg"
      dismissible={!busy}
      alert={error && <FormAlert>{error}</FormAlert>}
    >
      <div className="flex items-start gap-7 max-[880px]:flex-col max-[880px]:items-center max-[880px]:gap-5">
        <div
          onKeyDownCapture={onKeyDownCapture}
          className="relative size-90 shrink-0 overflow-hidden rounded-lg bg-ink-900 max-[880px]:size-[calc(100vw-2rem)]"
        >
          {src && (
            <Cropper
              image={src}
              crop={crop}
              zoom={zoom}
              minZoom={1}
              maxZoom={maxZoom}
              aspect={1}
              cropShape="round"
              showGrid={false}
              keyboardStep={STEP}
              onCropChange={setCrop}
              onZoomChange={setZoom}
              onMediaLoaded={(media) =>
                setMaxZoom(
                  Math.max(1, Math.min(media.naturalWidth, media.naturalHeight) / MIN_EDGE),
                )
              }
              onCropComplete={(percentages, croppedPixels) => {
                setArea(percentages);
                setPixels(croppedPixels);
              }}
            />
          )}
        </div>
        <div className="flex min-w-0 flex-1 flex-col items-start gap-7 pt-2 max-[880px]:w-full max-[880px]:items-center max-[880px]:gap-5 max-[880px]:pt-0">
          <div className="flex items-end gap-5">
            <Preview src={src} area={area} size="size-24" label="主页" />
            <Preview src={src} area={area} size="size-9" label="导航栏" />
          </div>
          <div className="flex w-full items-center gap-2.5">
            <button
              type="button"
              aria-label="缩小"
              onClick={() => setZoom((current) => Math.max(1, current - ZOOM_STEP))}
              className="inline-flex size-8 items-center justify-center rounded-full text-fg-3 hover:bg-paper-200 hover:text-ink-900"
            >
              <Minus className="size-4" aria-hidden="true" />
            </button>
            <Slider
              aria-label="缩放"
              value={zoom}
              min={1}
              max={maxZoom}
              step={0.01}
              onValueChange={setZoom}
            />
            <button
              type="button"
              aria-label="放大"
              onClick={() => setZoom((current) => Math.min(maxZoom, current + ZOOM_STEP))}
              className="inline-flex size-8 items-center justify-center rounded-full text-fg-3 hover:bg-paper-200 hover:text-ink-900"
            >
              <Plus className="size-4" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>
      <div className="flex justify-end gap-3 max-[880px]:flex-col-reverse">
        <button
          type="button"
          aria-disabled={busy || undefined}
          onClick={() => {
            if (!busy) onCancel();
          }}
          className={btnSecondary}
        >
          取消
        </button>
        <button
          type="button"
          aria-disabled={busy || !pixels || undefined}
          onClick={() => void confirm()}
          className={btnPrimary}
        >
          {busy ? "上传中…" : "使用这张头像"}
        </button>
      </div>
    </Dialog>
  );
}

/// 圆形预览：按裁剪区域（百分比）把原图放大、平移到圆里
function Preview({
  src,
  area,
  size,
  label,
}: {
  src: string | null;
  area: Area | null;
  size: string;
  label: string;
}) {
  return (
    <div className="flex flex-col items-center gap-2">
      <span className={`relative overflow-hidden rounded-full bg-ink-900 ${size}`}>
        {src && area && (
          // biome-ignore lint/performance/noImgElement: 预览的是本地对象地址，不经 next/image
          <img
            src={src}
            alt=""
            className="absolute max-w-none"
            // 位置随裁剪区域每一帧变化，只能写成行内样式
            style={{
              width: `${10_000 / area.width}%`,
              height: `${10_000 / area.height}%`,
              left: `${(-area.x / area.width) * 100}%`,
              top: `${(-area.y / area.height) * 100}%`,
            }}
          />
        )}
      </span>
      <span className="font-mono text-3xs tracking-caps text-fg-3">{label}</span>
    </div>
  );
}
