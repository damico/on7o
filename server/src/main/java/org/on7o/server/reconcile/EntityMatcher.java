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
            String uri = HcinUris.entity(candidate.kind(), candidate.label());
            boolean known = repository.exists(uri) || alreadyMintedHere(matches, uri);
            matches.put(candidate.localUri(), new EntityMatch(candidate, uri, !known));
        }

        return matches;
    }

    /** Two candidates in one thought may reduce to the same entity; only the first creates it. */
    private static boolean alreadyMintedHere(Map<String, EntityMatch> matches, String uri) {
        return matches.values().stream().anyMatch(match -> match.hcinUri().equals(uri));
    }
}
