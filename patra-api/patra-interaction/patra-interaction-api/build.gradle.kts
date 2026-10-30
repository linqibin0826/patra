/**
 * Patra Interaction API
 *
 * 服务契约层 - 服务间调用的 HTTP Interface、DTO 与对外发布的集成事件 record，按子域分包；
 * 集成事件（{子域}.event）由 notification 消费（PAP-141）
 */

plugins {
    id("patra.hexagonal-api")
}
