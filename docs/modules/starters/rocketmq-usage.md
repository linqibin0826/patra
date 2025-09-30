# RocketMQ Starter 使用速览

> 详细规范与示例已迁移至 `rocketmq.md`。此文件保留为快速入口。

- Starter 说明：`./rocketmq.md`
- Outbox 集成示例：`../../ingest/deep-dive.md`
- 配置参考：见 `rocketmq.md` 第 2 节（未显式配置 `naming.namespace` 时，将按 `spring.profiles.active` 自动推导命名空间）

运行期错误处理：发布入口对命名/参数不合规会抛出 `ApplicationException(422)`；Web/Feign 将统一输出 `ProblemDetail`。

推荐落地：
- 发送：统一使用 `publisher.sendByChannel("domain.resource.event", PatraMessage.of(payload))`，或通过 `ChannelKey` `.channel()` 生成；
- 接收：统一使用 `@Consumes(channel="domain.resource.event", consumer="roleName")` 注解，Starter 自动映射 `topic/tag/group` 并做强校验；
- 不再手写 `@RocketMQMessageListener` 的 `topic/tag/group` 字符串，降低维护成本与出错概率。

如需更新 RocketMQ 规范，请同步编辑 `rocketmq.md` 并在此保留链接。
