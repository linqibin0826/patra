# src/store/

Zustand store 落点。v1 脚手架阶段无业务 store。

约定（待出现首个 store 时落地）：

- 一个 store 一文件，文件名与 store 名一致（如 `user-store.ts` 导出 `useUserStore`）
- 跨页面共享的客户端状态才放这里；纯局部状态用组件 `useState`
- 不要把服务端缓存数据放 Zustand —— 那是 TanStack Query 的职责
