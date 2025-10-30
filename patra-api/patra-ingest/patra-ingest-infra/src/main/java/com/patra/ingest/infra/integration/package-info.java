/**
 * External system integration adapters (domain-centric organization).
 *
 * <p>This package contains infrastructure adapters that integrate with external systems and bounded
 * contexts. Each subdirectory represents integration with a specific external system.
 *
 * <p>Organization philosophy:
 *
 * <ul>
 *   <li><strong>Domain-centric</strong>: Organized by external system (PubMed, Registry, Storage)
 *   <li><strong>Bounded context</strong>: Each external system is treated as a separate bounded
 *       context
 *   <li><strong>ACL co-location</strong>: Anti-Corruption Layers are co-located with consuming
 *       adapters
 *   <li><strong>Scalability</strong>: Clear pattern for adding new data sources (10+ sources
 *       expected)
 * </ul>
 *
 * <p>Structure:
 *
 * <ul>
 *   <li><strong>pubmed/</strong> - PubMed E-Utilities API integration
 *   <li><strong>registry/</strong> - patra-registry service integration for configurations
 *   <li><strong>storage/</strong> - patra-storage service and object storage (S3/MinIO) integration
 * </ul>
 *
 * <p>Naming conventions:
 *
 * <ul>
 *   <li>Adapters: {@code *Adapter.java} (e.g., {@code PubmedSearchAdapter})
 *   <li>ACL converters: {@code *Converter.java} in {@code acl/} subdirectory
 * </ul>
 *
 * @since 0.1.0
 */
package com.patra.ingest.infra.integration;
