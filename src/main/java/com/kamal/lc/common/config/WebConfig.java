package com.kamal.lc.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures Spring Boot to serve Angular static files from static/browser/.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve Angular built files from static/browser/
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/browser/")
                .setCachePeriod(0);
    }
}
