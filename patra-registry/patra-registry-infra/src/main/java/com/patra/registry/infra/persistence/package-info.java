/**
 * 持久化根包 - 数据库访问层实现。
 *
 * <p>本包是基础设施层的持久化根包,包含仓储实现、数据库实体、MyBatis-Plus Mapper 和实体转换器。持久化层负责将领域对象持久化到 MySQL 数据库,并提供高效的查询能力。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>实现领域仓储接口,提供 CRUD 操作
 *   <li>管理数据库实体对象(DO)的生命周期
 *   <li>通过 MyBatis-Plus Mapper 执行 SQL 操作
 *   <li>将数据库实体转换为领域对象,反之亦然
 * </ul>
 *
 * <h2>包结构</h2>
 *
 * <ul>
 *   <li>{@code repository} - 仓储接口实现,实现领域端口
 *   <li>{@code entity} - 数据库实体对象(DO),映射数据库表
 *   <li>{@code mapper} - MyBatis-Plus Mapper 接口,定义 SQL 操作
 *   <li>{@code converter} - 实体转换器,DO ↔ 领域对象
 * </ul>
 *
 * <h2>分层职责</h2>
 *
 * <ul>
 *   <li><b>Repository 层</b>: 实现领域仓储接口,协调 Mapper 和 Converter
 *   <li><b>Mapper 层</b>: 定义数据库操作,继承 {@code BaseMapper<DO>}
 *   <li><b>Entity 层</b>: 数据库实体对象,使用 MyBatis-Plus 注解
 *   <li><b>Converter 层</b>: 双向转换器,DO ↔ VO
 * </ul>
 *
 * <h2>命名约定</h2>
 *
 * <ul>
 *   <li>仓储实现: {@code *RepositoryMpImpl}
 *   <li>Mapper 接口: {@code *Mapper extends BaseMapper<DO>}
 *   <li>数据库实体: {@code *DO}(Domain Object 或 Data Object)
 *   <li>转换器: {@code *EntityConverter}
 * </ul>
 *
 * <h2>数据流转</h2>
 *
 * <pre>{@code
 * [查询流程]
 * Repository.findXxx()
 *     ↓ 调用
 * Mapper.selectXxx()
 *     ↓ 返回 DO
 * Converter.toDomain(DO)
 *     ↓ 返回
 * 领域对象(VO)
 *
 * [命令流程]
 * 领域聚合根
 *     ↓
 * Converter.toEntity(VO)
 *     ↓ 返回 DO
 * Mapper.insert/update(DO)
 *     ↓ 持久化
 * 数据库
 * }</pre>
 *
 * <h2>技术实现</h2>
 *
 * <ul>
 *   <li><b>MyBatis-Plus BaseMapper</b>: 提供基础 CRUD 方法
 *   <li><b>自定义 SQL</b>: 通过 {@code @Select} 注解或 XML 定义复杂查询
 *   <li><b>时态查询</b>: 支持 {@code effectiveFrom} 和 {@code effectiveUntil} 的时间范围查询
 *   <li><b>配置优先级</b>: 实现 TASK 级覆盖 SOURCE 级的配置层次
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.infra.persistence;
