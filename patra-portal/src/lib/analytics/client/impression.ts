/// 曝光要求的可见比例与连续时长（MRC / IAB 可见曝光标准，埋点方案 §6.4）
const MIN_RATIO = 0.5;
const DWELL_MS = 1_000;

export interface ImpressionTimer {
  /// 卡片当前的可见比例（0～1）
  ratio(value: number): void;
  /// 标签页是否在前台
  visibility(visible: boolean): void;
  dispose(): void;
}

/// 一张卡片的曝光判定，不碰 DOM：可见比例 ≥ 0.5 且标签页在前台时开始计时，连续满 1 秒发一次；中途不满足就清零。
/// 只发一次（同一列表里同一篇的去重由列表负责）
export function createImpressionTimer(opts: {
  onImpress: () => void;
  setTimer: (fn: () => void, ms: number) => unknown;
  clearTimer: (handle: unknown) => void;
}): ImpressionTimer {
  let ratio = 0;
  let visible = true;
  let timer: unknown = null;
  let done = false;

  function update(): void {
    const qualifies = !done && ratio >= MIN_RATIO && visible;
    if (qualifies && timer === null) {
      timer = opts.setTimer(() => {
        timer = null;
        done = true;
        opts.onImpress();
      }, DWELL_MS);
    } else if (!qualifies && timer !== null) {
      opts.clearTimer(timer);
      timer = null;
    }
  }

  return {
    ratio(value) {
      ratio = value;
      update();
    },
    visibility(value) {
      visible = value;
      update();
    },
    dispose() {
      done = true;
      update();
    },
  };
}
