package com.patra.registry.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.model.aggregate.Provenance;

import java.util.List;
import java.util.Optional;

/**
 * 文献数据源聚合的仓储端口（Repository Port）。
 * <p>以聚合为单位进行加载与保存；不暴露持久化细节。
 */
public interface LiteratureProvenanceRepository {

	/**
	 * 按业务键（code）查找聚合。
	 * @param code 数据源业务码
	 * @return 聚合
	 */
	Optional<Provenance> findByCode(ProvenanceCode code);

	/**
	 * 按 ID 查找聚合。
	 * @param id 技术键
	 * @return 聚合
	 */
	Optional<Provenance> findById(Long id);

	/**
	 * 保存聚合（新建或更新）。
	 * @param aggregate 聚合
	 * @return 持久化后的聚合
	 */
	Provenance save(Provenance aggregate);

	/**
	 * 分页查询聚合。
	 * @param offset 偏移量
	 * @param limit 数量
	 * @return 聚合列表
	 */
	List<Provenance> findAll(int offset, int limit);

	/**
	 * 判断业务键是否存在。
	 * @param code 数据源业务码
	 * @return 是否存在
	 */
	boolean existsByCode(ProvenanceCode code);
}
