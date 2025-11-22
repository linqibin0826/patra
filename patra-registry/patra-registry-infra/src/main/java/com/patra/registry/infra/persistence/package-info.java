/// 持久化根包 - 数据库访问层实现。
///
/// 本包是基础设施层的持久化根包,包含仓储实现、数据库实体、MyBatis-Plus Mapper 和实体转换器。持久化层负责将领域对象持久化到 MySQL 数据库,并提供高效的查询能力。
///
/// ## 职责
///
/// - 实现领域仓储接口,提供 CRUD 操作
///   - 管理数据库实体对象(DO)的生命周期
///   - 通过 MyBatis-Plus Mapper 执行 SQL 操作
///   - 将数据库实体转换为领域对象,反之亦然
///
/// ## 包结构
///
/// - `repository` - 仓储接口实现,实现领域端口
///   - `entity` - 数据库实体对象(DO),映射数据库表
///   - `mapper` - MyBatis-Plus Mapper 接口,定义 SQL 操作
///   - `converter` - 实体转换器,DO ↔ 领域对象
///
/// ## 分层职责
///
/// - **Repository 层**: 实现领域仓储接口,协调 Mapper 和 Converter
///   - **Mapper 层**: 定义数据库操作,继承 `BaseMapper<DO>`
///   - **Entity 层**: 数据库实体对象,使用 MyBatis-Plus 注解
///   - **Converter 层**: 双向转换器,DO ↔ VO
///
/// ## 命名约定
///
/// - 仓储实现: `*RepositoryMpImpl`
///   - Mapper 接口: `*Mapper extends BaseMapper<DO>`
///   - 数据库实体: `*DO`(Domain Object 或 Data Object)
///   - 转换器: `*EntityConverter`
///
/// ## 数据流转
///
/// ```java
/// [查询流程]
/// Repository.findXxx()
///     ↓ 调用
/// Mapper.selectXxx()
///     ↓ 返回 DO
/// Converter.toDomain(DO)
///     ↓ 返回
/// 领域对象(VO)
///
/// [命令流程]
/// 领域聚合根
///     ↓
/// Converter.toEntity(VO)
///     ↓ 返回 DO
/// Mapper.insert/update(DO)
///     ↓ 持久化
/// 数据库
/// ```
///
/// ## 技术实现
///
/// - **MyBatis-Plus BaseMapper**: 提供基础 CRUD 方法
///   - **自定义 SQL**: 通过 `@Select` 注解或 XML 定义复杂查询
///   - **时态查询**: 支持 `effectiveFrom` 和 `effectiveUntil` 的时间范围查询
///   - **配置优先级**: 实现 TASK 级覆盖 SOURCE 级的配置层次
///
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.infra.persistence;
