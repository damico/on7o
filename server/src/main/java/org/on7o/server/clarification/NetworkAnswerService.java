package org.on7o.server.clarification;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.on7o.server.hcin.HcinRepository;
import org.on7o.server.hcin.HcinVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes the answers to the network's own questions back into the network.
 *
 * <p>{@link NetworkClarificationService} turns a gap into a question. This is the
 * other half: without it an answer would sit in the thought's answer log, true
 * and unused, while the graph went on missing the very property the user just
 * supplied and the shapes went on reporting it.
 *
 * <p>Only questions the network raised are applied. Those carry the node and the
 * property they are about, which is what makes an answer mechanically usable; a
 * question the qThought stage asked is prose about a transcript and is consumed
 * by consolidation instead.
 *
 * <p><b>An answer lands in the graph the node lives in.</b> The shapes validate
 * one graph at a time, so a layer written elsewhere would leave the statement
 * still incomplete where it actually sits.
 *
 * <p><b>Nothing is invented.</b> An answer that does not resolve to a value the
 * property can hold, a layer the ontology never declared or a date that is not a
 * date, is left alone and logged. The answer stays on record; it simply has not
 * become knowledge yet.
 */
@Service
public class NetworkAnswerService {

    private static final Logger log = LoggerFactory.getLogger(NetworkAnswerService.class);

    private static final String HCIN = HcinVocabulary.NS;
    private static final String FINANCIAL = HcinVocabulary.FIN_NS;

    /** Where a value goes and how it is read, by the property being answered. */
    private static final Map<String, Reading> READINGS = readings();

    private final HcinRepository repository;
    private final ClarificationService clarification;

    public NetworkAnswerService(HcinRepository repository, ClarificationService clarification) {
        this.repository = repository;
        this.clarification = clarification;
    }

    private static Map<String, Reading> readings() {
        return Map.of(
                HCIN + "layer", Reading.NAMED_INDIVIDUAL,
                HCIN + "context", Reading.SETTING,
                HCIN + "occurredAt", Reading.DATE,
                HCIN + "validFrom", Reading.DATE,
                FINANCIAL + "amount", Reading.NUMBER,
                FINANCIAL + "currency", Reading.TEXT,
                FINANCIAL + "scope", Reading.TEXT);
    }

    /**
     * Applies every answered gap question of one thought.
     *
     * <p>Safe to repeat: writing the same statement twice leaves one statement.
     *
     * @param thoughtId the thought whose answers are being applied
     * @return how many statements were written
     */
    public int apply(String thoughtId) {
        Map<String, AnswerRevision> answers = clarification.currentAnswers(thoughtId);

        int written = 0;
        for (ClarificationQuestion question : clarification.allQuestions(thoughtId)) {
            AnswerRevision answer = answers.get(question.id());
            if (!isApplicable(question, answer)) {
                continue;
            }
            written += write(question, answer.answer());
        }

        if (written > 0) {
            log.info("thought {}: {} statement(s) written from answers", thoughtId, written);
        }
        return written;
    }

    /** Whether this question is one the network asked and the user has answered. */
    private boolean isApplicable(ClarificationQuestion question, AnswerRevision answer) {
        return question.subjectRef() != null
                && question.predicateRef() != null
                && READINGS.containsKey(question.predicateRef())
                && answer != null
                && answer.answer() != null
                && !answer.answer().isBlank();
    }

    /** One answer, as statements about the node it is about. */
    private int write(ClarificationQuestion question, String answer) {
        String graphUri = repository.graphOf(question.subjectRef());
        if (graphUri == null) {
            log.info("answer to {} names a node the network no longer holds: {}",
                    question.id(), question.subjectRef());
            return 0;
        }

        Model model = ModelFactory.createDefaultModel();
        Resource subject = model.createResource(question.subjectRef());
        Property property = ResourceFactory.createProperty(question.predicateRef());
        Reading reading = READINGS.get(question.predicateRef());

        int written = 0;
        for (String value : reading.split(answer)) {
            RDFNode node = reading.read(value, model, repository);
            if (node == null) {
                log.info("answer \"{}\" is not something {} can hold; left as an answer only",
                        value, question.predicateRef());
                continue;
            }
            model.add(subject, property, node);
            written++;
        }

        if (written > 0) {
            repository.add(graphUri, model);
        }
        return written;
    }

    /**
     * How an answer becomes a value.
     *
     * <p>The kinds mirror what {@code GapPhrasing} asks for, because a question
     * that offered a list of layers and an answer that names one of them are the
     * same decision seen from two ends.
     */
    private enum Reading {

        /** One of the individuals the ontology declares, matched by its label. */
        NAMED_INDIVIDUAL {
            @Override
            RDFNode read(String value, Model model, HcinRepository repository) {
                String uri = repository.uriLabelled(value);
                return uri == null ? null : model.createResource(uri);
            }
        },

        /**
         * A setting, which is whatever the network already calls by that name, or
         * a new context node when the name is new. Minting is right here and not
         * elsewhere: the user was told they could name a setting nobody had named
         * yet, and refusing it afterwards would make that offer a lie.
         */
        SETTING {
            @Override
            RDFNode read(String value, Model model, HcinRepository repository) {
                String uri = repository.uriLabelled(value);
                if (uri != null) {
                    return model.createResource(uri);
                }
                Resource context = model.createResource(
                        HcinVocabulary.ENTITY_NS + "context:" + slug(value));
                model.add(context, RDF.type, HcinVocabulary.CONTEXT);
                model.add(context, HcinVocabulary.LABEL, value);
                return context;
            }
        },

        /** A calendar date, read at the start of that day in UTC. */
        DATE {
            @Override
            RDFNode read(String value, Model model, HcinRepository repository) {
                try {
                    return model.createTypedLiteral(
                            LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toString(),
                            org.apache.jena.datatypes.xsd.XSDDatatype.XSDdateTime);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }

            @Override
            List<String> split(String answer) {
                return List.of(answer.trim());
            }
        },

        /** An amount. */
        NUMBER {
            @Override
            RDFNode read(String value, Model model, HcinRepository repository) {
                try {
                    return model.createTypedLiteral(new BigDecimal(value.replace(",", "")));
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            @Override
            List<String> split(String answer) {
                return List.of(answer.trim());
            }
        },

        /** Whatever the user wrote, as it was written. */
        TEXT {
            @Override
            RDFNode read(String value, Model model, HcinRepository repository) {
                return model.createLiteral(value);
            }

            @Override
            List<String> split(String answer) {
                return List.of(answer.trim());
            }
        };

        /** The value, or null when the answer is not one this property can hold. */
        abstract RDFNode read(String value, Model model, HcinRepository repository);

        /**
         * The values in one answer. A question that could be answered with more
         * than one value was answered as a list, which is how the page writes what
         * was clicked.
         */
        List<String> split(String answer) {
            List<String> values = new ArrayList<>();
            for (String part : answer.split(",")) {
                if (!part.isBlank()) {
                    values.add(part.trim());
                }
            }
            return values;
        }

        /** "Expo Teleinfo 2026" becomes "expo-teleinfo-2026". */
        static String slug(String label) {
            return label.toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("(^-|-$)", "");
        }
    }
}
