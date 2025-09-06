package com.patra.starter.web.req;

import java.util.Set;

/**
 * 排序契约：DTO 自报白名单，供校验与文档展示
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface Sortable {

	/** 排序表达式：field,asc|desc;field2,desc */
	String getSort();

	/** 小驼峰白名单（向客户端公开），例如：{"updatedAt","createdAt","id"} */
	default Set<String> allowedSortFields() {
		return Set.of("id", "createdAt", "updatedAt");
	}

	/** 最大排序字段数，默认 3（必要时 DTO 可重写） */
	default int maxSortFields() {
		return 3;
	}

}
