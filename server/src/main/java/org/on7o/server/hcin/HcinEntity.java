package org.on7o.server.hcin;

/**
 * A person or an organization as the HCIN holds it.
 *
 * @param uri   stable HCIN URI
 * @param label the name the ego knows it by
 * @param tier  how strongly its existence is believed
 */
public record HcinEntity(String uri, String label, KnowledgeTier tier) {
}
