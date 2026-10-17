package dev.graphnous.github;

import dev.graphnous.application.organization.OrganizationId;

/**
 * The organization the App works in for an installation: its projects are
 * the ones it scans and reports on. A server of one organization works in
 * that one for every installation; a server of many maps each installation
 * to the organization that installed the App.
 */
@FunctionalInterface
public interface InstallationOrganizations {

    OrganizationId organizationOf(long installation);
}
