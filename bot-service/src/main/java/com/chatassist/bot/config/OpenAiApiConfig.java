package com.chatassist.bot.config;

import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Explicitly creates the {@link OpenAiApi} bean so that Spring AI's
 * {@code OpenAiAutoConfiguration} backs off ({@code @ConditionalOnMissingBean}).
 *
 * <p>Root cause: Spring Cloud LoadBalancer adds a {@code DeferringLoadBalancerInterceptor}
 * to {@code RestClient} beans. When Spring AI's auto-configured {@code OpenAiApi} uses that
 * {@code RestClient}, the interceptor treats {@code api.openai.com} as an Eureka service ID,
 * finds no registered instance, and throws:
 * <pre>IllegalArgumentException: Service Instance cannot be null, serviceId: api.openai.com</pre>
 *
 * <p>Fix: construct {@code OpenAiApi} with a <em>fresh</em> {@code RestClient.Builder()} that
 * is created programmatically — not declared as a Spring bean — so it is invisible to Spring
 * Cloud's load-balancer customisation machinery.
 */
@Configuration
public class OpenAiApiConfig {

    /**
     * A plain, load-balancer-free {@link OpenAiApi} bean.
     *
     * <p>{@code RestClient.builder()} is called directly here (not injected as a Spring bean),
     * so Spring Cloud's {@code LoadBalancerAutoConfiguration} cannot add its interceptors to it.
     *
     * @param apiKey resolved from {@code spring.ai.openai.api-key}
     */
    @Bean
    public OpenAiApi openAiApi(
            @Value("${spring.ai.openai.api-key:}") String apiKey) {

        return OpenAiApi.builder()
                .apiKey(apiKey)
                // Programmatic builder — NOT a Spring bean, so no load-balancer interceptors.
                .restClientBuilder(RestClient.builder())
                .build();
    }
}

