"use client";

import { Slider as SliderPrimitive } from "@base-ui/react/slider";

interface SliderProps {
  value: number;
  min: number;
  max: number;
  step: number;
  onValueChange: (value: number) => void;
  "aria-label": string;
  disabled?: boolean;
}

/// 单值滑块（base-ui Slider）：纸色轨道、陶土色已选段与圆形拇指，方向键、Home / End 调值是 base-ui 自带的。
export function Slider({ value, min, max, step, onValueChange, disabled, ...props }: SliderProps) {
  return (
    <SliderPrimitive.Root
      value={value}
      min={min}
      max={max}
      step={step}
      disabled={disabled}
      onValueChange={(next) => onValueChange(next)}
      className="w-full"
    >
      <SliderPrimitive.Control className="flex h-6 w-full touch-none items-center select-none">
        <SliderPrimitive.Track className="relative h-1 w-full rounded-full bg-paper-300">
          <SliderPrimitive.Indicator className="rounded-full bg-clay-500" />
          <SliderPrimitive.Thumb
            aria-label={props["aria-label"]}
            className="size-4 rounded-full border border-border-strong bg-paper-50 shadow-sm outline-none data-dragging:scale-110"
          />
        </SliderPrimitive.Track>
      </SliderPrimitive.Control>
    </SliderPrimitive.Root>
  );
}
