# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy>=2", "pillow>=11"]
# ///
"""录屏测量工具，给 recording-analyst agent 用。

用法：
  uv run .claude/scripts/recording-probe.py timeline <录屏> --out DIR
  uv run .claude/scripts/recording-probe.py motion   <录屏> --out DIR [--css-width 1440]

timeline（每次都跑）：把录屏按画面变化切成段——静止 / 滚动 / 内容切换 / 局部变化，
  给出每段的帧号、时间、变化区域，近空白帧（白屏）时长，变化段里的停顿帧；
  生成 DIR/storyboard.png（每段首尾帧，末帧用红框标出变化区域）。
motion（有滚动、晃动、跳动时跑）：把画面切成「栏 × 横带」的格子测纵向位移——
  视口位移（通栏发丝线整体偏移，即整页被拖动）、正文滚动量、锚定元素（吸顶条、吸顶侧栏等）
  扣除视口位移后的偏移轨迹；轨迹全 0 为「固定」，否则为「晃动」。
两个子命令都会导出 DIR/frames/（全部帧）和 DIR/sheet.png（带帧号的缩略总览），结果另存 JSON。
"""

import argparse
import json
import subprocess
import sys
from collections import Counter
from pathlib import Path

import numpy as np
from numpy.lib.stride_tricks import sliding_window_view
from PIL import Image, ImageDraw

BAND = 48  # 横带高度（录屏像素）
MAX_SHIFT = 160  # 相邻帧最大搜索位移
BLOCKS = 8  # 每栏切成几个列块做行剖面，提高匹配区分度
MATCH_OK = 2.5  # 最优匹配的平均灰度差上限，超过视为内容变化 / 被遮挡
FLAT = 3.0  # 格子灰度标准差低于此值视为空白，不参与匹配
ANCHOR_RANGE = 0.085  # 锚定元素扣除视口位移后的偏移上限（占画面高度比例）
STILL_PIXELS = 60  # 相邻帧变化像素少于此数视为静止（GIF 抖动噪点会让整体平均差略大于 0）
PIXEL_CHANGED = 20  # 单像素灰度差超过此值算「变了」
LOCAL = 0.03  # 变化像素占比低于此值算局部变化（hover、光标、小图标）
BLANK_INK = 0.02  # 非背景像素占比低于此值算近空白帧


# ---------- 公共 ----------


def run(cmd: list[str]) -> str:
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout


def extract(video: Path, out: Path) -> tuple[list[Path], list[float]]:
    frames_dir = out / "frames"
    frames_dir.mkdir(parents=True, exist_ok=True)
    for old in frames_dir.glob("f_*.png"):
        old.unlink()
    run(["ffmpeg", "-v", "error", "-y", "-i", str(video), "-fps_mode", "passthrough",
         str(frames_dir / "f_%04d.png")])
    info = json.loads(run(["ffprobe", "-v", "error", "-select_streams", "v:0",
                           "-show_entries", "frame=pts_time", "-of", "json", str(video)]))
    times = [float(f.get("pts_time", 0) or 0) for f in info.get("frames", [])]
    paths = sorted(frames_dir.glob("f_*.png"))
    if len(times) != len(paths):
        times = [i / 25 for i in range(len(paths))]
    return paths, times


def thumb(path: Path, width: int, label: str, box: tuple[int, int, int, int] | None = None) -> Image.Image:
    im = Image.open(path).convert("RGB")
    if box:
        ImageDraw.Draw(im).rectangle(box, outline="red", width=max(2, im.width // 300))
    im = im.resize((width, round(im.height * width / im.width)))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 7 * len(label) + 8, 16], fill="white")
    d.text((4, 3), label, fill="red")
    return im


def grid(images: list[Image.Image], dest: Path, per_row: int = 4) -> None:
    w, h = images[0].size
    rows = (len(images) + per_row - 1) // per_row
    sheet = Image.new("RGB", (w * per_row, h * rows), "black")
    for k, im in enumerate(images):
        sheet.paste(im, ((k % per_row) * w, (k // per_row) * h))
    sheet.save(dest)


def contact_sheet(paths: list[Path], dest: Path) -> None:
    """每隔若干帧取一张，左上角标帧号，拼成 4 列总览。"""
    step = max(1, len(paths) // 16)
    grid([thumb(paths[i], 480, f"f{i + 1}") for i in range(0, len(paths), step)][:16], dest)


def load_gray(paths: list[Path]) -> np.ndarray:
    return np.stack([np.asarray(Image.open(p).convert("L"), dtype=np.float32) for p in paths])


def background(frame: np.ndarray) -> int:
    vals, counts = np.unique(frame.astype(np.int16), return_counts=True)
    return int(vals[counts.argmax()])


def profiles(frame: np.ndarray, x0: int, x1: int) -> np.ndarray:
    """(行, 列块) 的平均灰度。"""
    edges = np.linspace(x0, x1, BLOCKS + 1).astype(int)
    return np.stack([frame[:, a:b].mean(axis=1) for a, b in zip(edges[:-1], edges[1:])], axis=1)


def band_shift(prof_a: np.ndarray, prof_b: np.ndarray, y0: int, y1: int,
               max_shift: int = MAX_SHIFT) -> int | None:
    """prof_a 中 [y0, y1) 这一段在 prof_b 里的纵向位移；空白或找不到返回 None。"""
    patch = prof_a[y0:y1]
    if patch.std() < FLAT:
        return None
    windows = sliding_window_view(prof_b, (y1 - y0, BLOCKS))[:, 0]  # (起点, 带高, 列块)
    lo, hi = max(0, y0 - max_shift), min(len(windows) - 1, y0 + max_shift)
    diffs = np.abs(windows[lo : hi + 1] - patch).mean(axis=(1, 2))
    best = int(diffs.argmin())
    if diffs[best] > MATCH_OK:
        return None
    return lo + best - y0


def rle(values: list) -> list[str]:
    out, i = [], 0
    while i < len(values):
        j = i
        while j + 1 < len(values) and values[j + 1] == values[i]:
            j += 1
        v = "?" if values[i] is None else f"{values[i]:+d}" if isinstance(values[i], int) else values[i]
        out.append(f"f{i + 1}" + (f"-{j + 1}" if j > i else "") + f":{v}")
        i = j + 1
    return out


# ---------- timeline ----------


def timeline(args: argparse.Namespace, out: Path, paths: list[Path], times: list[float]) -> None:
    stack = load_gray(paths)
    n, h, w = stack.shape
    bg = background(stack[0])
    step_ms = float(np.median(np.diff(times)) * 1000) if n > 1 else 0.0

    masks = [None] + [np.abs(stack[i] - stack[i - 1]) > PIXEL_CHANGED for i in range(1, n)]
    ink = [float((np.abs(f - bg) > 18).mean()) for f in stack]

    # 每帧相对上一帧的变化类型。滚动只看最宽那栏（正文），避免吸顶侧栏等不动的区域干扰匹配；
    # 上、中、下各放一条采样带，任意一条在大范围内匹配上即为滚动，快速滚动也能测到
    cx0, cx1 = max(columns(stack, bg), key=lambda c: c[1] - c[0])
    prof = [profiles(f, cx0, cx1) for f in stack]
    probes = [(int(h * r), int(h * r) + BAND * 2) for r in (0.18, 0.42, 0.66)]
    kind = ["起始"]
    for i in range(1, n):
        changed = int(masks[i].sum())
        if changed < STILL_PIXELS:
            kind.append("静止")
            continue
        if changed / masks[i].size < LOCAL:
            kind.append("局部变化")
            continue
        found = [band_shift(prof[i - 1], prof[i], a, b, max_shift=int(h * 0.8)) for a, b in probes]
        moved = [v for v in found if v not in (None, 0)]
        kind.append("滚动" if moved else "局部变化" if 0 in found else "内容切换或极快滚动")

    # 按「静止 / 变化」切段，变化段中间夹的单帧静止并入变化段
    active = [k not in ("静止", "起始") for k in kind]
    for i in range(1, n - 1):
        if not active[i] and active[i - 1] and active[i + 1]:
            active[i] = True
    segs, i = [], 0
    while i < n:
        j = i
        while j + 1 < n and active[j + 1] == active[i]:
            j += 1
        segs.append((i, j))
        i = j + 1

    rows, thumbs = [], []
    for k, (a, b) in enumerate(segs, 1):
        t0, t1 = times[a], times[b]
        if not active[a]:
            label = "静止"
            box = None
            note = ""
        else:
            c = Counter(kind[i] for i in range(a, b + 1) if kind[i] not in ("静止", "起始"))
            label = " / ".join(f"{name}×{cnt}" for name, cnt in c.most_common())
            union = np.any(np.stack([masks[i] for i in range(a, b + 1) if masks[i] is not None]), axis=0)
            ys, xs = np.nonzero(union)
            box = (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())) if len(xs) else None
            stalls = [i + 1 for i in range(a, b + 1) if kind[i] == "静止"]
            note = f"段内停顿帧 {stalls}" if stalls else ""
        blank = [i + 1 for i in range(a, b + 1) if ink[i] < BLANK_INK]
        if blank:
            note = (note + "；" if note else "") + f"近空白帧 {blank}"
        rows.append({"seg": f"S{k}", "frames": [a + 1, b + 1], "time_s": [round(t0, 3), round(t1, 3)],
                     "duration_ms": round((t1 - t0) * 1000 + step_ms), "state": label,
                     "change_box": box, "note": note})
        if len(thumbs) < 32:
            thumbs.append(thumb(paths[a], 360, f"S{k} f{a + 1} {t0:.2f}s"))
            if b > a:
                thumbs.append(thumb(paths[b], 360, f"S{k} f{b + 1} {t1:.2f}s", box))

    blank_frames = [i for i in range(n) if ink[i] < BLANK_INK]
    report = {
        "video": str(args.video), "size": [w, h], "frames": n,
        "duration_s": round(times[-1] - times[0], 3) if n > 1 else 0, "frame_step_ms": round(step_ms, 1),
        "segments": rows, "blank_frames": [i + 1 for i in blank_frames],
        "blank_ms": round(len(blank_frames) * step_ms),
        "frames_dir": str(out / "frames"), "sheet": str(out / "sheet.png"),
        "storyboard": str(out / "storyboard.png"),
    }
    if thumbs:
        grid(thumbs, out / "storyboard.png")
    (out / "timeline.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")

    p = print
    p(f"# 录屏 {args.video.name}：{w}×{h}，{n} 帧，约 {report['duration_s']}s，"
      f"帧间隔约 {step_ms:.0f}ms（时间精度以此为限）")
    p("\n## 分镜（变化区域为录屏像素 x0,y0,x1,y1）")
    p("| 段 | 帧 | 时间 | 状态 | 变化区域 | 备注 |")
    p("|---|---|---|---|---|---|")
    for r in rows:
        p(f"| {r['seg']} | f{r['frames'][0]}–f{r['frames'][1]} | {r['time_s'][0]:.2f}–{r['time_s'][1]:.2f}s"
          f"（{r['duration_ms']}ms） | {r['state']} | {r['change_box'] or '-'} | {r['note'] or '-'} |")
    p(f"\n近空白帧：{report['blank_frames'] or '无'}" + (f"，约 {report['blank_ms']}ms" if blank_frames else ""))
    if any("滚动" in r["state"] for r in rows):
        p("有滚动段：需要看滚动时元素是否晃动 / 跳动，再跑 motion 子命令。")
    p(f"\n分镜图：{out / 'storyboard.png'}；总览：{out / 'sheet.png'}；逐帧：{out / 'frames'}")


# ---------- motion ----------


def columns(stack: np.ndarray, bg: int) -> list[tuple[int, int]]:
    """按栏间空白切出纵向栏。只看画面上 20% 以下，避开通栏的吸顶条。"""
    h, w = stack.shape[1:]
    sample = stack[:: max(1, len(stack) // 20), int(h * 0.2):, :]
    ink = (np.abs(sample - bg) > 18).mean(axis=(0, 1))
    ink = np.convolve(ink, np.ones(15) / 15, mode="same")
    filled = ink > 0.015
    runs, start = [], None
    for x, on in enumerate(filled):
        if on and start is None:
            start = x
        elif not on and start is not None:
            runs.append([start, x])
            start = None
    if start is not None:
        runs.append([start, w])
    merged: list[list[int]] = []
    for r in runs:
        if merged and r[0] - merged[-1][1] < w * 0.015:
            merged[-1][1] = r[1]
        else:
            merged.append(r)
    cols = [(a, b) for a, b in merged if b - a >= w * 0.05]
    return cols or [(0, w)]


def hairlines(frame: np.ndarray) -> list[int]:
    """通栏横线：左右边距里 80% 以上的采样点比上方 2px 暗。"""
    h, w = frame.shape
    xs = np.r_[np.arange(2, int(w * 0.08), 3), np.arange(int(w * 0.92), w - 2, 3)]
    ys = []
    for y in range(2, min(h, int(h * 0.45))):
        darker = (frame[y - 2, xs] - frame[y, xs]) >= 6
        if darker.mean() > 0.8 and (not ys or y - ys[-1] > 2):
            ys.append(y)
    return ys


def motion(args: argparse.Namespace, out: Path, paths: list[Path], times: list[float]) -> None:
    stack = load_gray(paths)
    n, h, w = stack.shape
    bg = background(stack[0])
    scale = args.css_width / w if args.css_width else None

    cols = columns(stack, bg)
    lines = [hairlines(f) for f in stack]

    # 视口偏移：发丝线相对基准（最常见的一组发丝线）的整体位移；找不到发丝线的帧沿用上一帧
    base = list(Counter(tuple(l) for l in lines).most_common(1)[0][0])
    viewport: list[int] = []
    for l in lines:
        prev = viewport[-1] if viewport else 0
        if not base or not l:
            viewport.append(prev)
            continue
        cands = Counter()
        for y in l:
            for b in base:
                cands[y - b] += 1
        top_n = max(cands.values())
        viewport.append(min((d for d, c in cands.items() if c == top_n), key=lambda d: abs(d - prev)))

    # 格子：吸顶区（最下一条基准发丝线以上）单独一格，其下按 BAND 切横带，避免吸顶与正文混在一格
    head = max(base) + 2 if base else 0
    bands = ([(0, head)] if head >= 8 else []) + [
        (y, min(h, y + BAND)) for y in range(head, h - BAND // 2, BAND)]

    profs = [[profiles(f, a, b) for f in stack] for a, b in cols]
    widest = max(range(len(cols)), key=lambda c: cols[c][1] - cols[c][0])

    # 正文滚动：最宽那栏每对帧里最常见的位移
    scroll = []
    for k in range(n - 1):
        seen = Counter(band_shift(profs[widest][k], profs[widest][k + 1], y0, y1) for y0, y1 in bands)
        seen.pop(None, None)
        scroll.append(seen.most_common(1)[0][0] if seen else None)

    # 锚定元素：每个格子直接与第 1 帧比对（不逐帧累加，避免误差漂移）
    anchored: dict[int, list[dict]] = {}
    for c in range(len(cols)):
        for y0, y1 in bands:
            raw = [0] + [band_shift(profs[c][0], profs[c][i], y0, y1) for i in range(1, n)]
            hit = [r for r in raw if r is not None]
            if len(hit) < n * 0.8 or profs[c][0][y0:y1].std() < FLAT:
                continue
            comp = [None if r is None else r - (viewport[i] - viewport[0]) for i, r in enumerate(raw)]
            # 真正锚定的元素偏移有界；正文重复行会被错配到很远处，偏移散乱
            near = [v for v in comp if v is not None and abs(v) <= h * ANCHOR_RANGE]
            if len(near) < len(hit) * 0.9:
                continue
            anchored.setdefault(c, []).append({"y": [y0, y1], "raw": raw, "offset": comp})

    def same(a: list, b: list) -> bool:
        pairs = [(x, y) for x, y in zip(a, b) if x is not None and y is not None]
        return bool(pairs) and sum(abs(x - y) <= 3 for x, y in pairs) >= len(pairs) * 0.9

    groups = []
    for c, cells in anchored.items():
        for cell in cells:
            g = groups[-1] if groups and groups[-1]["col"] == c else None
            if g and g["y"][1] == cell["y"][0] and same(g["cells"][0]["offset"], cell["offset"]):
                g["y"][1] = cell["y"][1]
                g["cells"].append(cell)
            else:
                groups.append({"col": c, "x": list(cols[c]), "y": list(cell["y"]), "cells": [cell]})
    for g in groups:
        seqs = [cell["offset"] for cell in g["cells"]]
        med = []
        for i in range(n):
            vals_i = [s[i] for s in seqs if s[i] is not None]
            med.append(int(np.median(vals_i)) if vals_i else None)
        known = [v for v in med if v is not None]
        g["amplitude"] = max(known) - min(known) if known else 0
        g["kind"] = "固定" if g["amplitude"] <= 2 else "晃动"
        g["offset"] = rle(med)
        del g["cells"]

    report = {
        "video": str(args.video), "size": [w, h], "frames": n,
        "duration_s": round(times[-1] - times[0], 3) if len(times) > 1 else None,
        "css_scale": scale, "background_gray": bg, "columns": cols,
        "hairlines_base": base, "hairlines": rle([",".join(map(str, l)) or "-" for l in lines]),
        "viewport_offset": rle(viewport), "scroll_per_pair": rle(scroll),
        "anchored": groups, "frames_dir": str(out / "frames"), "sheet": str(out / "sheet.png"),
    }
    (out / "motion.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")

    unit = f"（录屏像素；×{scale:.2f} ≈ CSS 像素）" if scale else "（录屏像素；给 --css-width 可换算 CSS 像素）"
    p = print
    p(f"# 录屏 {args.video.name}：{w}×{h}，{n} 帧，约 {report['duration_s']}s")
    p(f"栏 x 区间：{cols}；位移单位{unit}")
    p(f"\n## 视口位移（发丝线基准 y={base}）")
    moved = [v for v in viewport if v != 0]
    p(("整页被拖动过：" + " | ".join(report["viewport_offset"])) if moved else "全程为 0，没有整页位移")
    p("\n## 正文滚动（最宽栏每对帧）")
    p(" | ".join(report["scroll_per_pair"]))
    p("\n## 锚定元素（已扣除视口位移）")
    for g in groups:
        tag = "固定" if g["kind"] == "固定" else f"晃动，幅度 {g['amplitude']}"
        p(f"- 栏{g['col']} x{g['x']} y{g['y']}：{tag}" + ("" if g["kind"] == "固定" else "；" + " | ".join(g["offset"])))
    if not groups:
        p("- 没有找到锚定元素")
    p(f"\n完整数据：{out / 'motion.json'}；总览：{out / 'sheet.png'}；逐帧：{out / 'frames'}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("command", choices=["timeline", "motion"])
    ap.add_argument("video", type=Path)
    ap.add_argument("--out", type=Path)
    ap.add_argument("--css-width", type=float, help="录屏对应的页面 CSS 宽度，用于换算 CSS 像素（motion）")
    args = ap.parse_args()
    out = args.out or Path.cwd() / f"recording-{args.video.stem}"
    out.mkdir(parents=True, exist_ok=True)

    paths, times = extract(args.video, out)
    contact_sheet(paths, out / "sheet.png")
    {"timeline": timeline, "motion": motion}[args.command](args, out, paths, times)


if __name__ == "__main__":
    sys.exit(main())
