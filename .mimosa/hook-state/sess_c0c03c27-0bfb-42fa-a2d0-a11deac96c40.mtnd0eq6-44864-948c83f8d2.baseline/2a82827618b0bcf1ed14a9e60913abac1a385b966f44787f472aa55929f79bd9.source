package com.nexus.campus.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 (Swagger) documentation configuration.
 *
 * <p>Access the Swagger UI at {@code /swagger-ui.html} and the JSON spec
 * at {@code /v3/api-docs} once the application is running.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI nexusCampusOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nexus-Campus API")
                        .description("AI-Driven Cyberpunk Campus Forum System — REST API documentation")
                        .version("1.0.0-CYBERPUNK")
                        .contact(new Contact()
                                .name("Nexus-Campus Team")
                                .url("https://github.com/your-org/nexus-campus")
                                .email("dev@nexus-campus.io"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
