package org.on7o.server.hcin;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ego as a node rather than a setting.
 */
class EgoIdentityTest {

    @Test
    void writesTheEgoIntoTheNetworkUnderTheNameItAnswersTo() throws IOException {
        HcinFixture hcin = new HcinFixture("Ana Prado");

        new EgoIdentity(hcin.repository()).assertEgo();

        // A projection is centred on the ego. Before this, the ego was a URI that
        // nothing in the graph mentioned, and the picture came back empty however
        // full the network was.
        assertThat(hcin.repository().exists(hcin.ego())).isTrue();
        assertThat(hcin.repository().people())
                .extracting(HcinEntity::label)
                .contains("Ana Prado");
    }
}
