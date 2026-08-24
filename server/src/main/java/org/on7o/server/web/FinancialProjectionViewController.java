package org.on7o.server.web;

import org.on7o.server.projection.ProjectionProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the ego-centric financial projection page.
 *
 * <p>The page holds no knowledge of its own. It fetches
 * {@code /api/hcin/financial-projection} and draws what comes back, which is why
 * changing the instant is a new request rather than a recalculation in the
 * browser: what the network looked like is the server's answer to give.
 */
@Controller
public class FinancialProjectionViewController {

    private final ProjectionProperties properties;

    public FinancialProjectionViewController(ProjectionProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/hcin/financial")
    public String projection(Model model) {
        model.addAttribute("minDistance", properties.getTemporalProximity().getMinDistance());
        model.addAttribute("maxDistance", properties.getTemporalProximity().getMaxDistance());
        return "financial-projection";
    }
}
