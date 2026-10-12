import { type FocusEvent, useEffect, useRef } from "react";
import type { FieldPath, FieldValues, UseFormTrigger } from "react-hook-form";

/// 离开字段时校验它（spec 第 9.3 节），返回交给 `register` 的 `onBlur`。表单用 `mode: "onSubmit"`、
/// `reValidateMode: "onSubmit"`，失焦校验全部交给这里，有两处和直接失焦校验不同：
/// 1. 空着离开不校验，「请输入…」留到提交时：进入页面时焦点已经在邮箱框，点一下别处不该冒出错误。
/// 2. 不在一次点击的中途校验：错误行插进来会把下方的元素往下挤，按下和松开落在不同元素上，这次点击
///    就没了。按着指针离开时，松开再校验；手机上轻触时松开在前、离开在后，点击紧跟着派发，排到它后面。
export function useBlurValidation<T extends FieldValues>(trigger: UseFormTrigger<T>) {
  const pressing = useRef(false);
  const pending = useRef<FieldPath<T> | null>(null);

  useEffect(() => {
    const press = () => {
      pressing.current = true;
    };
    const release = () => {
      pressing.current = false;
      const field = pending.current;
      pending.current = null;
      if (field !== null) {
        void trigger(field);
      }
    };
    document.addEventListener("pointerdown", press, true);
    document.addEventListener("pointerup", release, true);
    document.addEventListener("pointercancel", release, true);
    return () => {
      document.removeEventListener("pointerdown", press, true);
      document.removeEventListener("pointerup", release, true);
      document.removeEventListener("pointercancel", release, true);
    };
  }, [trigger]);

  return (field: FieldPath<T>) => (event: FocusEvent<HTMLInputElement>) => {
    if (event.target.value === "") {
      return;
    }
    if (pressing.current) {
      pending.current = field;
      return;
    }
    // 没按着：键盘离开，或者手机上轻触（这一轮接着派发点击）
    setTimeout(() => void trigger(field));
  };
}
