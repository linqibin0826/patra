// patra-learn/src/teaching/logic.ts
// 教学组件里与渲染无关的逻辑：行内代码切分、参数组件的匹配与占位替换、逐步动画的推进。纯函数

export interface InlineSegment {
  code: boolean;
  text: string;
}

/// 把字符串按成对的反引号切成普通文字与行内代码
export function segmentInline(source: string): InlineSegment[] {
  const out: InlineSegment[] = [];
  const re = /`([^`]+)`/g;
  let last = 0;
  for (const m of source.matchAll(re)) {
    const at = m.index ?? 0;
    if (at > last) out.push({ code: false, text: source.slice(last, at) });
    out.push({ code: true, text: m[1] ?? "" });
    last = at + m[0].length;
  }
  if (last < source.length) out.push({ code: false, text: source.slice(last) });
  return out;
}

export type ControlValue = boolean | number;

type Control =
  | { type: "switch"; id: string }
  | { type: "slider"; id: string; unit?: string | undefined };

interface ParamCase {
  when?: Record<string, boolean | readonly number[] | undefined> | undefined;
}

/// 第一个所有条件都满足的 case；滑块条件是闭区间 [下限, 上限]
export function matchCase<C extends ParamCase>(
  cases: readonly C[],
  values: Record<string, ControlValue>,
): C | undefined {
  return cases.find((c) =>
    Object.entries(c.when ?? {}).every(([key, cond]) => {
      const v = values[key];
      if (cond === undefined) return true;
      if (typeof cond === "boolean") return v === cond;
      const [lo = Number.NEGATIVE_INFINITY, hi = Number.POSITIVE_INFINITY] = cond;
      return typeof v === "number" && v >= lo && v <= hi;
    }),
  );
}

/// 控件当前值的展示文字：滑块带单位
export function formatControlValue(control: Control | undefined, value: ControlValue): string {
  if (typeof value === "boolean") return value ? "开" : "关";
  return control?.type === "slider" && control.unit ? `${value} ${control.unit}` : String(value);
}

/// 把文字里的 `{控件 id}` 换成控件当前值；不认识的 id 原样保留
export function interpolate(
  source: string,
  controls: readonly Control[],
  values: Record<string, ControlValue>,
): string {
  return source.replace(/\{([a-z][a-z0-9-]*)\}/g, (whole, key: string) => {
    const v = values[key];
    return v === undefined
      ? whole
      : formatControlValue(
          controls.find((c) => c.id === key),
          v,
        );
  });
}

export interface StepperState {
  /// 当前显示第几步（从 0 数）
  step: number;
  /// 正在问的「你猜」下标；null 表示没在问
  gate: number | null;
  /// 「你猜」选了第几项（从 0 数）
  picked: number | null;
  /// 已经答过的「你猜」下标
  passed: number[];
}

export const STEPPER_START: StepperState = { step: 0, gate: null, picked: null, passed: [] };

/// 点「下一步」：要去的那一步前有没答过的「你猜」就先停下问；在问且没答时不动；最后一步回到开头
export function stepperNext(
  state: StepperState,
  total: number,
  guesses: readonly { before: number }[],
): StepperState {
  if (state.gate !== null && state.picked === null) return state;
  if (state.gate === null && state.step >= total - 1) return STEPPER_START;
  const target = state.step + 1;
  if (state.gate === null) {
    const gate = guesses.findIndex((g, i) => g.before === target + 1 && !state.passed.includes(i));
    if (gate >= 0) return { ...state, gate, picked: null };
  }
  const passed =
    state.gate === null || state.passed.includes(state.gate)
      ? state.passed
      : [...state.passed, state.gate];
  return { step: target, gate: null, picked: null, passed };
}

/// 点「上一步」：在问「你猜」时先关掉它，否则退一步
export function stepperPrev(state: StepperState): StepperState {
  if (state.gate !== null) return { ...state, gate: null, picked: null };
  if (state.step === 0) return state;
  return { ...state, step: state.step - 1 };
}
