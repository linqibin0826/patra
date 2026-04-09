package com.patra.catalog.infra.batch.venue.scopus;

import com.patra.starter.batch.core.JobParams;
import lombok.NoArgsConstructor;

/// Scopus 富化 Job 参数。
///
/// 空参数类——Scopus 富化不需要外部参数，处理范围由 Reader 的 JPQL
/// 条件（NOT EXISTS 子查询）自动确定。
///
/// @author linqibin
/// @since 0.1.0
@NoArgsConstructor
public class ScopusEnrichmentJobParams implements JobParams {}
