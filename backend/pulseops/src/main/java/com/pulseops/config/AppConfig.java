package com.pulseops.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AppConfig {

    /** Injected instead of calling Instant.now() directly, so time-dependent logic can be tested with a fixed clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Bounded pool for Gemini calls. Bounded on purpose: the external API has rate limits, and an unbounded queue
     * would hide a backlog until memory runs out. When the queue is full, submission is rejected and the analysis
     * is marked FAILED with a retry option (see AnalysisListener) rather than blocking the caller.
     */
    @Bean
    ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("PulseOps API").version("1.0")
                        .description("Service health monitoring and incident management. See docs/API.md."))
                .components(new Components()
                        .addSecuritySchemes("bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("apiKey", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name("X-API-Key")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
