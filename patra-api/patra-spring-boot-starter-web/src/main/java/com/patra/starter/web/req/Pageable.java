package com.patra.starter.web.req;

/**
 * 分页契约
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface Pageable {

	/** 获取页码（从1开始） */
	Integer getPageNo();

	/** 获取分页大小 */
	Integer getPageSize();

}
