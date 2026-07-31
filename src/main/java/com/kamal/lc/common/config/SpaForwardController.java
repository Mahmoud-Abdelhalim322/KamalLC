package com.kamal.lc.common.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards all non-API, non-static routes to Angular's index.html.
 * This enables Angular client-side routing to work when refreshing the browser.
 *
 * Pattern: matches any path that does NOT contain a dot (file extension),
 * so static files (.js, .css, .ico) are served normally.
 */
@Controller
public class SpaForwardController {

    @RequestMapping(value = {"/", "/{path:^(?!api)[^\\.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
