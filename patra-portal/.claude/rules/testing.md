# 测试规范

## 测试位置

- 测试文件与被测文件并列：`Venue.tsx` 对应 `Venue.test.tsx`
- 或集中在 `tests/` 目录（与现有结构一致）
- 测试工具：Vitest（runner + assertion）+ Testing Library（DOM 查询）

## 查询优先级

按可访问性优先级，从高到低：

1. `getByRole`（最优，反映用户感知）
2. `getByLabelText`（表单字段）
3. `getByPlaceholderText`
4. `getByText`
5. ⚠️ `getByTestId`（**仅作最后手段**，禁止作为首选）

## 禁止行为

- ❌ Snapshot 测试（脆弱，无信息价值）
- ❌ 测试实现细节（如 state 内部值、私有方法）
- ❌ 裸 `setTimeout` 等异步——必须用 `await findBy*` / `waitFor`

## TDD 红绿循环

遵循 workspace 根 `CLAUDE.md` 的 TDD 强制要求：写测试 → 验证失败 → 写最少代码 → 验证通过 → commit。
