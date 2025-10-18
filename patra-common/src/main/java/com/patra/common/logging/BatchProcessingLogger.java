package com.patra.common.logging;

import java.time.Duration;
import org.slf4j.Logger;

/**
 * Utility for standardized logging of batch processing operations.
 *
 * <p>Implements FR-010 (Batch Processing Logging) for consistent batch operation tracking across
 * microservices.
 *
 * <h3>Purpose:</h3>
 *
 * Provides consistent logging format for batch operations (data ingestion, scheduled jobs, bulk
 * processing):
 *
 * <ul>
 *   <li>Batch start/end with correlation ID
 *   <li>Progress tracking (items processed, success/failure counts)
 *   <li>Performance metrics (duration, throughput)
 *   <li>Summary at INFO level, details at DEBUG level (FR-010)
 * </ul>
 *
 * <h3>Usage Example:</h3>
 *
 * <pre>{@code
 * // In orchestrator or scheduled job
 * private static final Logger log = LoggerFactory.getLogger(DataIngestOrchestrator.class);
 * private final BatchProcessingLogger batchLogger = new BatchProcessingLogger(log);
 *
 * public void processBatchIngest(List<Article> articles) {
 *     String correlationId = "batch-" + UUID.randomUUID();
 *     long startTime = System.currentTimeMillis();
 *
 *     batchLogger.logStart(correlationId, "ArticleIngest", articles.size());
 *
 *     int successCount = 0;
 *     int failureCount = 0;
 *
 *     for (Article article : articles) {
 *         try {
 *             processArticle(article);
 *             successCount++;
 *             batchLogger.logProgress(correlationId, successCount, articles.size());
 *         } catch (Exception e) {
 *             failureCount++;
 *             batchLogger.logItemFailure(correlationId, article.getId(), e);
 *         }
 *     }
 *
 *     Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
 *     batchLogger.logComplete(correlationId, successCount, failureCount, duration);
 * }
 * }</pre>
 *
 * <h3>Log Format:</h3>
 *
 * <pre>
 * INFO  Batch START [correlationId=batch-abc-123] operation=ArticleIngest totalItems=1000
 * DEBUG Batch PROGRESS [correlationId=batch-abc-123] processed=100/1000 (10%)
 * DEBUG Batch PROGRESS [correlationId=batch-abc-123] processed=500/1000 (50%)
 * WARN  Batch ITEM FAILURE [correlationId=batch-abc-123] itemId=article-456: Validation error
 * INFO  Batch COMPLETE [correlationId=batch-abc-123] success=980 failure=20 duration=45s throughput=22.2/s
 * </pre>
 *
 * <h3>Log Levels (FR-010):</h3>
 *
 * <ul>
 *   <li><strong>INFO</strong>: Batch start, completion, summary statistics
 *   <li><strong>DEBUG</strong>: Progress updates, individual item processing
 *   <li><strong>WARN</strong>: Individual item failures (recoverable)
 *   <li><strong>ERROR</strong>: Batch-level failures (unrecoverable)
 * </ul>
 *
 * @since 0.1.0 (Phase 6 - User Story 4)
 */
public class BatchProcessingLogger {

  private final Logger logger;

  /**
   * Creates a BatchProcessingLogger with the given logger.
   *
   * @param logger The SLF4J logger for the calling class
   */
  public BatchProcessingLogger(Logger logger) {
    this.logger = logger;
  }

  /**
   * Logs batch start at INFO level.
   *
   * <p>Format: {@code Batch START [correlationId=X] operation=Y totalItems=Z}
   *
   * @param correlationId Unique batch identifier for tracking
   * @param operationName Descriptive name for the batch operation
   * @param totalItems Total number of items to process
   */
  public void logStart(String correlationId, String operationName, int totalItems) {
    logger.info(
        "Batch START [correlationId={}] operation={} totalItems={}",
        correlationId,
        operationName,
        totalItems);
  }

  /**
   * Logs batch completion at INFO level with summary statistics.
   *
   * <p>Format: {@code Batch COMPLETE [correlationId=X] success=Y failure=Z duration=Ws
   * throughput=N/s}
   *
   * @param correlationId Batch identifier
   * @param successCount Number of successfully processed items
   * @param failureCount Number of failed items
   * @param duration Total batch processing duration
   */
  public void logComplete(
      String correlationId, int successCount, int failureCount, Duration duration) {
    int totalItems = successCount + failureCount;
    double throughput = totalItems / Math.max(duration.toSeconds(), 1.0);

    logger.info(
        "Batch COMPLETE [correlationId={}] success={} failure={} duration={}s throughput={}/s",
        correlationId,
        successCount,
        failureCount,
        duration.toSeconds(),
        String.format("%.1f", throughput));
  }

  /**
   * Logs batch progress at DEBUG level (use for periodic updates, not every item).
   *
   * <p>Format: {@code Batch PROGRESS [correlationId=X] processed=Y/Z (P%)}
   *
   * <p><strong>Performance Note:</strong> Call this periodically (e.g., every 100 items, every 10
   * seconds), NOT for every item. For high-frequency batches (>1000 items), consider sampling
   * (e.g., log every 5%).
   *
   * @param correlationId Batch identifier
   * @param processedCount Number of items processed so far
   * @param totalItems Total number of items
   */
  public void logProgress(String correlationId, int processedCount, int totalItems) {
    if (logger.isDebugEnabled()) {
      double percentage = (processedCount / (double) totalItems) * 100;
      logger.debug(
          "Batch PROGRESS [correlationId={}] processed={}/{} ({}%)",
          correlationId, processedCount, totalItems, String.format("%.1f", percentage));
    }
  }

  /**
   * Logs individual item failure at WARN level (recoverable error).
   *
   * <p>Format: {@code Batch ITEM FAILURE [correlationId=X] itemId=Y: error message}
   *
   * <p>Use this for expected failures (validation errors, data quality issues) that don't stop the
   * entire batch.
   *
   * @param correlationId Batch identifier
   * @param itemId Identifier for the failed item
   * @param error The exception that caused the failure
   */
  public void logItemFailure(String correlationId, String itemId, Throwable error) {
    logger.warn(
        "Batch ITEM FAILURE [correlationId={}] itemId={}: {}",
        correlationId,
        itemId,
        error.getMessage(),
        error);
  }

  /**
   * Logs batch-level failure at ERROR level (unrecoverable error).
   *
   * <p>Format: {@code Batch FAILURE [correlationId=X] operation=Y after processing Z items: error
   * message}
   *
   * <p>Use this for catastrophic failures that stop the entire batch (database connection lost, out
   * of memory, etc.).
   *
   * @param correlationId Batch identifier
   * @param operationName Batch operation name
   * @param processedSoFar Number of items processed before failure
   * @param error The exception that caused the batch failure
   */
  public void logBatchFailure(
      String correlationId, String operationName, int processedSoFar, Throwable error) {
    logger.error(
        "Batch FAILURE [correlationId={}] operation={} after processing {} items: {}",
        correlationId,
        operationName,
        processedSoFar,
        error.getMessage(),
        error);
  }

  /**
   * Logs retry attempt at WARN level (for transient failures).
   *
   * <p>Format: {@code Batch RETRY [correlationId=X] attempt=Y/Z for itemId=I due to: error}
   *
   * @param correlationId Batch identifier
   * @param itemId Identifier for the item being retried
   * @param attemptNumber Current retry attempt (1-based)
   * @param maxAttempts Maximum retry attempts
   * @param error The error that triggered the retry
   */
  public void logRetry(
      String correlationId, String itemId, int attemptNumber, int maxAttempts, Throwable error) {
    logger.warn(
        "Batch RETRY [correlationId={}] attempt={}/{} for itemId={} due to: {}",
        correlationId,
        attemptNumber,
        maxAttempts,
        itemId,
        error.getMessage());
  }

  /**
   * Logs checkpoint/milestone at INFO level (for long-running batches).
   *
   * <p>Format: {@code Batch CHECKPOINT [correlationId=X] milestone=Y processed=Z elapsed=Ws}
   *
   * <p>Use this for long-running batches (>10 minutes) to provide visibility without waiting for
   * completion.
   *
   * @param correlationId Batch identifier
   * @param milestone Descriptive milestone name (e.g., "25% complete", "halfway", "final stage")
   * @param processedCount Number of items processed so far
   * @param elapsedTime Time elapsed since batch start
   */
  public void logCheckpoint(
      String correlationId, String milestone, int processedCount, Duration elapsedTime) {
    logger.info(
        "Batch CHECKPOINT [correlationId={}] milestone={} processed={} elapsed={}s",
        correlationId,
        milestone,
        processedCount,
        elapsedTime.toSeconds());
  }
}
