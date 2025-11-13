package com.patra.ingest.infra.exception;

/**
 * 数据源异常
 *
 * <p>当Infrastructure层调用Framework层的DataSourceProvider失败时抛出此异常。
 *
 * <p>异常转换链:
 * <ul>
 *   <li>Framework层: ProvenanceClientException</li>
 *   <li>Infrastructure层: DataSourceException (本类)</li>
 *   <li>Domain层: 无异常定义,由上层决定如何处理</li>
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
public class DataSourceException extends RuntimeException {

    public DataSourceException(String message) {
        super(message);
    }

    public DataSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
