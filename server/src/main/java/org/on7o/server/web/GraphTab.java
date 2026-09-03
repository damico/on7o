package org.on7o.server.web;

/**
 * One knowledge graph, as a choice on the validation page.
 *
 * <p>The counts sit on the tab rather than behind it because where the findings
 * are is itself the answer to a question. A network whose asserted graph is
 * empty and whose hypotheses graph is full says something true about how the
 * pipeline treats what it is told, and a reader should see that without
 * clicking through every graph to find out.
 *
 * @param name     short graph name, as the API takes it
 * @param defects  findings that make the data untrustworthy
 * @param thin     findings that say the data is usable but incomplete
 * @param gaps     findings that are questions waiting to be asked
 * @param selected whether this is the graph being shown
 */
public record GraphTab(String name, int defects, int thin, int gaps, boolean selected) {

    /** Everything found in this graph. */
    public int total() {
        return defects + thin + gaps;
    }
}
