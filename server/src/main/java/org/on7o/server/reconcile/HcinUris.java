package org.on7o.server.reconcile;

import org.on7o.server.hcin.HcinVocabulary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Mints the URIs the HCIN identifies things by.
 *
 * <p>Every URI here is a function of what the thing is, never of when it was
 * created. That is what makes reconciliation idempotent: the same entity
 * reconciled twice lands on the same URI, so the second merge adds nothing.
 */
public final class HcinUris {

    private HcinUris() {
    }

    /** The URI an entity of this kind and name gets. */
    public static String entity(EntityKind kind, String label) {
        return HcinVocabulary.ENTITY_NS + kind.segment() + ":" + slug(label);
    }

    /** The URI of a relationship, derived from its two ends and its meaning. */
    public static String relationship(String sourceUri, String predicateUri, String targetUri) {
        return HcinVocabulary.ENTITY_NS + "relationship:" + hash(sourceUri + "|" + predicateUri + "|" + targetUri);
    }

    /** The URI of an event reconciled from a thought, stable across re-runs. */
    public static String event(String segment, String thoughtId, String localName) {
        return HcinVocabulary.ENTITY_NS + segment + ":" + hash(thoughtId + "|" + localName);
    }

    /** The URI of the record of having learned something from a thought. */
    public static String observation(String thoughtId, String aboutUri) {
        return HcinVocabulary.ENTITY_NS + "observation:" + hash(thoughtId + "|" + aboutUri);
    }

    /** The URI a thought is known by inside the HCIN. */
    public static String thought(String thoughtId) {
        return HcinVocabulary.ENTITY_NS + "thought:" + thoughtId;
    }

    /**
     * A label reduced to what matching should care about: lower case, no
     * accents, no punctuation. "José Damico" and "jose damico" are the same
     * person, and an HCIN that creates two of him is worse than useless.
     */
    public static String normalize(String label) {
        if (label == null) {
            return "";
        }
        String stripped = Normalizer.normalize(label, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return stripped.replaceAll("\\s+", " ");
    }

    /** A label as a URI-safe segment. */
    public static String slug(String label) {
        String normalized = normalize(label).replace(' ', '-');
        return normalized.isEmpty() ? "unnamed" : normalized;
    }

    private static String hash(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required and missing", e);
        }
    }
}
