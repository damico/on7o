package org.on7o.server.reconcile;

/**
 * What became of one candidate entity.
 *
 * @param candidate the entity as the thought described it
 * @param hcinUri   the URI it is known by in the HCIN
 * @param created   true when the HCIN had never heard of it before
 */
public record EntityMatch(CandidateEntity candidate, String hcinUri, boolean created) {
}
