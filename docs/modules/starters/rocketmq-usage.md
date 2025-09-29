# RocketMQ Starter 使用速览

> 详细规范与示例已迁移至 `rocketmq.md`。此文件保留为快速入口。

- Starter 说明：`./rocketmq.md`
- Outbox 集成示例：`../../ingest/deep-dive.md`
- 配置参考：见 `rocketmq.md` 第 2 节（未显式配置 `naming.namespace` 时，将按 `spring.profiles.active` 自动推导命名空间）

运行期错误处理：发布入口对命名/参数不合规会抛出 `ApplicationException(422)`；Web/Feign 将统一输出 `ProblemDetail`。

推荐落地（去魔法值）：
- 在 `patra-{service}-domain` 定义强类型通道目录（`ChannelKey` + `{Service}Channels`），如 `IngestChannels.TASK_READY`；
- 发送：`publisher.sendByChannel(IngestChannels.TASK_READY.channel(), PatraMessage.of(payload))`；
- 接收：使用组合注解（建议后续统一为 `@Consumes(IngestChannels.TASK_READY)`）生成监听参数，禁止在注解中手写字符串。

如需更新 RocketMQ 规范，请同步编辑 `rocketmq.md` 并在此保留链接。
