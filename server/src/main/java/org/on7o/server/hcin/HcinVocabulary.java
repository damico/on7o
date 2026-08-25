package org.on7o.server.hcin;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * The HCIN terms, as Jena resources.
 *
 * <p>Two vocabularies: the core says what a person, a relationship, an
 * interaction and a piece of evidence are, and the financial one adds what money
 * and decision power are. They are kept apart so the core stays usable by
 * projections that have nothing to do with money.
 *
 * <p>Every term here also exists in {@code hcin-core.ttl} or
 * {@code hcin-financial.ttl}. Those files are the definition; this class is how
 * Java refers to them.
 */
public final class HcinVocabulary {

    /** Namespace of the core vocabulary. */
    public static final String NS = "http://on7o.io/hcin#";

    /** Namespace of the financial vocabulary. */
    public static final String FIN_NS = "http://on7o.io/hcin/financial#";

    /** Namespace under which HCIN entities themselves are minted. */
    public static final String ENTITY_NS = "urn:hcin:";

    // -------------------------------------------------------------------------
    // Core classes
    // -------------------------------------------------------------------------

    public static final Resource PERSON = resource("Person");
    public static final Resource ORGANIZATION = resource("Organization");
    public static final Resource MEMBERSHIP = resource("Membership");
    public static final Resource ROLE = resource("Role");
    public static final Resource RELATIONSHIP = resource("Relationship");
    public static final Resource INTERACTION = resource("Interaction");
    public static final Resource CONTEXT = resource("Context");
    public static final Resource GOAL = resource("Goal");
    public static final Resource EVIDENCE = resource("Evidence");
    public static final Resource OBSERVATION = resource("Observation");
    public static final Resource PERSPECTIVE = resource("Perspective");
    public static final Resource TEMPORAL_EXTENT = resource("TemporalExtent");
    public static final Resource ENTITY = resource("Entity");
    public static final Resource LAYER_CLASS = resource("Layer");
    public static final Resource FINANCIAL = resource("Financial");
    public static final Resource PROFESSIONAL = resource("Professional");
    public static final Resource ORGANIZATIONAL = resource("Organizational");

    // -------------------------------------------------------------------------
    // Epistemic status
    // -------------------------------------------------------------------------

    public static final Resource EPISTEMIC_STATUS = resource("EpistemicStatus");
    public static final Resource ASSERTED = resource("Asserted");
    public static final Resource INFERRED = resource("Inferred");
    public static final Resource HYPOTHESIZED = resource("Hypothesized");

    // -------------------------------------------------------------------------
    // Core properties
    // -------------------------------------------------------------------------

    public static final Property LABEL = property("label");
    public static final Property SOURCE = property("source");
    public static final Property TARGET = property("target");
    public static final Property LAYER = property("layer");
    public static final Property RELATION_TYPE = property("relationType");
    public static final Property CONTEXT_OF = property("context");
    public static final Property MEMBER = property("member");
    public static final Property MEMBER_OF = property("memberOf");
    public static final Property ROLE_OF = property("role");
    public static final Property PARTICIPANT = property("participant");
    public static final Property INTERACTION_TYPE = property("interactionType");
    public static final Property OCCURRED_AT = property("occurredAt");

    public static final Property VALID_FROM = property("validFrom");
    public static final Property VALID_TO = property("validTo");
    public static final Property OBSERVED_AT = property("observedAt");
    public static final Property RECORDED_AT = property("recordedAt");
    public static final Property LAST_CONFIRMED_AT = property("lastConfirmedAt");

    public static final Property KNOWLEDGE_STATUS = property("knowledgeStatus");
    public static final Property CONFIDENCE = property("confidence");
    public static final Property DERIVED_FROM = property("wasDerivedFrom");
    public static final Property EVIDENCE_OF = property("evidence");
    public static final Property PERSPECTIVE_OF = property("perspective");
    public static final Property THOUGHT_ID = property("thoughtId");
    public static final Property OBSERVED_STATEMENT = property("about");

    // -------------------------------------------------------------------------
    // Financial classes
    // -------------------------------------------------------------------------

    public static final Resource FINANCIAL_FLOW = financial("FinancialFlow");
    public static final Resource FINANCIAL_AUTHORITY = financial("FinancialAuthority");
    public static final Resource FINANCIAL_DEPENDENCY = financial("FinancialDependency");
    public static final Resource FINANCIAL_MAGNITUDE = financial("FinancialMagnitude");
    public static final Resource FINANCIAL_DECISION_SCOPE = financial("FinancialDecisionScope");

    /** Money entering the ego's financial sphere. */
    public static final Resource INFLOW = financial("Inflow");

    /** Money leaving the ego's financial sphere. */
    public static final Resource OUTFLOW = financial("Outflow");

    public static final Resource EXPENDITURE_AUTHORITY = financial("ExpenditureAuthority");
    public static final Resource REVENUE_AUTHORITY = financial("RevenueAuthority");

    // -------------------------------------------------------------------------
    // Financial properties
    // -------------------------------------------------------------------------

    public static final Property FLOW_SOURCE = financialProperty("flowSource");
    public static final Property FLOW_TARGET = financialProperty("flowTarget");
    public static final Property DIRECTION = financialProperty("direction");
    public static final Property AMOUNT = financialProperty("amount");
    public static final Property CURRENCY = financialProperty("currency");
    public static final Property HOLDER = financialProperty("holder");
    public static final Property ORGANIZATION_OF = financialProperty("organization");
    public static final Property AUTHORITY_TYPE = financialProperty("authorityType");
    public static final Property SPENDING_LIMIT = financialProperty("spendingLimit");
    public static final Property SCOPE = financialProperty("scope");

    private HcinVocabulary() {
    }

    /** A core term as a resource. */
    public static Resource resource(String localName) {
        return ResourceFactory.createResource(NS + localName);
    }

    /** A core term as a property. */
    public static Property property(String localName) {
        return ResourceFactory.createProperty(NS + localName);
    }

    /** A financial term as a resource. */
    public static Resource financial(String localName) {
        return ResourceFactory.createResource(FIN_NS + localName);
    }

    /** A financial term as a property. */
    public static Property financialProperty(String localName) {
        return ResourceFactory.createProperty(FIN_NS + localName);
    }

    /** The standard prefixes, for every query and every export. */
    public static String prefixes() {
        return """
                PREFIX hcin:  <%s>
                PREFIX hcinf: <%s>
                PREFIX rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX xsd:   <http://www.w3.org/2001/XMLSchema#>
                """.formatted(NS, FIN_NS);
    }
}
