/**
 * Registry 基础设施层根包 - 持久化和外部系统集成。
 *
 * <p>本包是六边形架构基础设施层的根包,包含仓储实现、数据库实体、MyBatis-Plus Mapper、实体转换器等基础设施组件。基础设施层实现领域层定义的端口接口,将领域对象持久化到数据库或与外部系统集成。
 *
 * <h2>架构定位</h2>
 *
 * <p>在六边形架构中,本层位于被驱动适配器侧,实现领域端口:
 *
 * <ul>
 *   <li><b>上游依赖</b>: 被应用层通过仓储接口调用
 *   <li><b>下游依赖</b>: 依赖 {@code patra-registry-domain} 领域模型和端口接口
 *   <li><b>实现职责</b>: 实现领域层定义的 {@code *Repository} 端口接口
 *   <li><b>技术选型</b>: 使用 MyBatis-Plus 作为持久化框架
 * </ul>
 *
 * <h2>包结构</h2>
 *
 * <ul>
 *   <li>{@code persistence} - 持久化根包
 *       <ul>
 *         <li>{@code repository} - 仓储接口实现(领域端口实现)</li>
 *         <li>{@code entity} - 数据库实体对象(DO)</li>
 *         <li>{@code mapper} - MyBatis-Plus Mapper 接口</li>
 *         <li>{@code converter} - 实体与领域对象转换器</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>核心职责</h2>
 *
 * <ul>
 *   <li>实现领域仓储接口,提供数据持久化能力
 *   <li>将领域对象转换为数据库实体(DO)并持久化
 *   <li>从数据库查询实体并转换为领域对象
 *   <li>处理数据库事务和并发控制
 *   <li>优化数据库查询性能(索引、批量操作等)
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><b>端口适配器模式</b>: 实现领域端口接口,隔离领域层和持久化技术
 *   <li><b>DO/VO 分离</b>: 数据库实体(DO)与领域值对象(VO)分离,通过转换器映射
 *   <li><b>MyBatis-Plus 规范</b>: 使用 MyBatis-Plus BaseMapper 和 IService 简化 CRUD
 *   <li><b>只读查询优化</b>: 对只读查询使用投影和 DTO,避免加载完整聚合根
 * </ul>
 *
 * <h2>数据转换流程</h2>
 *
 * <pre>{@code
 * [查询侧]
 * 数据库实体(DO) → EntityConverter → 领域值对象(VO) → QueryAssembler → 查询 DTO
 *
 * [命令侧]
 * 命令 DTO → 领域聚合根 → EntityConverter → 数据库实体(DO) → 数据库
 * }</pre>
 *
 * <h2>技术栈</h2>
 *
 * <ul>
 *   <li><b>MyBatis-Plus</b>: 持久化框架,提供增强的 CRUD 能力
 *   <li><b>MapStruct</b>: 实体转换器自动生成
 *   <li><b>MySQL</b>: 关系型数据库
 *   <li><b>Flyway</b>: 数据库版本管理和迁移
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Repository
 * @RequiredArgsConstructor
 * public class ProvenanceConfigRepositoryMpImpl implements ProvenanceConfigRepository {
 *     private final RegProvenanceMapper provenanceMapper;
 *     private final ProvenanceEntityConverter converter;
 *
 *     @Override
 *     public Optional<Provenance> findProvenanceByCode(ProvenanceCode code) {
 *         return provenanceMapper.selectByCode(code.getCode())
 *             .map(converter::toDomain);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.infra;
