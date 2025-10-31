/**
 * XXL-Job scheduled tasks that trigger batch ingestion workflows.
 *
 * <p>This package contains driving adapters that receive external scheduling triggers and translate
 * them into application orchestrator calls. All classes here are part of the Hexagonal
 * Architecture's adapter layer (External → System direction).
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Parse XXL-Job parameters from JSON
 *   <li>Validate job parameters
 *   <li>Delegate to {@code PlanIngestionUseCase} or other orchestrators
 *   <li>Handle adapter-level error mapping
 *   <li>Report job execution results to XXL-Job admin
 * </ul>
 *
 * <h2>Design Pattern</h2>
 *
 * Uses Template Method pattern via {@link
 * com.patra.ingest.adapter.scheduler.job.AbstractProvenanceScheduleJob} to provide unified job
 * execution flow. Concrete job classes only need to:
 *
 * <ul>
 *   <li>Define provenance code (e.g., PUBMED, EMBASE)
 *   <li>Define operation code (e.g., HARVEST, PARSE)
 *   <li>Expose XXL-Job entry point with {@code @XxlJob} annotation
 * </ul>
 *
 * <h2>Naming Convention</h2>
 *
 * All job classes must end with {@code Job} suffix (e.g., {@code PubmedHarvestJob}).
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * @Component
 * public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {
 *     @Override
 *     protected ProvenanceCode getProvenanceCode() {
 *         return ProvenanceCode.PUBMED;
 *     }
 *
 *     @Override
 *     protected OperationCode getOperationCode() {
 *         return OperationCode.HARVEST;
 *     }
 *
 *     @XxlJob("pubmedHarvest")
 *     public void run() {
 *         executeScheduleJob(XxlJobHelper.getJobParam());
 *     }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.adapter.scheduler;
