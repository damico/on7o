package org.on7o.server.reconcile;

import org.on7o.server.hcin.HcinRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides whether a thought is talking about someone the HCIN already knows.
 *
 * <p>The strategy is deliberately the simplest one that cannot be wrong in a
 * way that matters: a normalized name plus a kind. Two mentions of "José" and
 * "jose" are one person; a person and an organization sharing a name are not
 * one thing.
 *
 * <p>Because the URI is a function of that key, matching reduces to asking
 * whether the URI already exists. Everything downstream stays idempotent for
 * free: reconciling the same thought twice mints the same URIs and merges the
 * same statements, so the second run changes nothing.
 *
 * <p>One name is resolved rather than minted. The ego is the person the network
 * is written from, and their URI is configured rather than derived from what they
 * are called, so a thought that names them would otherwise create a second person
 * and hang every edge on it. Matching that name to the configured ego is what
 * keeps the network centred on someone who is actually in it.
 *
 * <p>A better strategy, one that could tell two people with the same name apart
 * by their context, replaces this class without touching anything else.
 */
@Service
public class EntityMatcher {

    private final HcinRepository repository;

    public EntityMatcher(HcinRepository repository) {
        this.repository = repository;
    }

    /**
     * Resolves every candidate to an HCIN URI, saying which ones were already
     * known.
     *
     * @param candidates entities read out of a consolidated thought
     * @return one match per candidate, keyed by the URI it had inside the thought
     */
    public Map<String, EntityMatch> match(List<CandidateEntity> candidates) {
        Map<String, EntityMatch> matches = new LinkedHashMap<>();

        for (CandidateEntity candidate : candidates) {
            String uri = uriFor(candidate);
            boolean known = repository.exists(uri) || alreadyMintedHere(matches, uri);
            matches.put(candidate.localUri(), new EntityMatch(candidate, uri, !known));
        }

        return matches;
    }

    /** The ego when the candidate is the ego, and the name-derived URI otherwise. */
    private String uriFor(CandidateEntity candidate) {
        return isEgo(candidate)
                ? repository.ego()
                : HcinUris.entity(candidate.kind(), candidate.label());
    }

    /**
     * Whether a candidate is the person the network is written from.
     *
     * <p>Compared on the normalized name, the same way two mentions of one person
     * are recognized as one. A thought may also refer to the ego by the ego URI
     * itself, which consolidation is told to use, and that is taken at its word.
     */
    private boolean isEgo(CandidateEntity candidate) {
        if (repository.ego().equals(candidate.localUri())) {
            return true;
        }
        return candidate.kind() == EntityKind.PERSON
                && HcinUris.normalize(candidate.label())
                        .equals(HcinUris.normalize(repository.egoLabel()));
    }

    /** Two candidates in one thought may reduce to the same entity; only the first creates it. */
    private static boolean alreadyMintedHere(Map<String, EntityMatch> matches, String uri) {
        return matches.values().stream().anyMatch(match -> match.hcinUri().equals(uri));
    }
}
