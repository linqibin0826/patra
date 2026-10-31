/**
 * Patra Interaction API
 *
 * 服务契约层 - 服务间调用的 HTTP Interface、DTO 与对外发布的集成事件 record，按子域分包；
 * 集成事件（{子域}.event）由 notification 消费（PAP-141）；notification 读提醒列表、拼提醒邮件时
 * 调对象视图（subject）与经验结果（post）两个内部接口（PAP-143）
 */

plugins {
    id("patra.hexagonal-api")
}
