package com.patra.starter.provenance.epmc.model.response;

/**
 * EPMC author object
 *
 * @author linqibin
 * @since 0.1.0
 */
public record Author(
    String fullName,        // Full name
    String firstName,       // First name
    String lastName,        // Last name
    String initials,        // Initials
    String affiliation      // Affiliation
) {
}
