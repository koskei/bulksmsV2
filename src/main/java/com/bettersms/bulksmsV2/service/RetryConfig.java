package com.bettersms.bulksmsV2.service;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@EnableRetry
@Configuration
@Data
@AllArgsConstructor
public class RetryConfig {

    private Environment environment;

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate template = new RetryTemplate();

        RetryPolicy retryPolicy = new SimpleRetryPolicy(environment.getProperty("retry.max-attempts", Integer.class, 5));


        ExponentialBackOffPolicy exponentialBackOffPolicy = new ExponentialBackOffPolicy();
        exponentialBackOffPolicy.setInitialInterval(environment.getProperty("retry.backoff.exponential.initial-interval", Integer.class, 3000));
        exponentialBackOffPolicy.setMultiplier(environment.getProperty("retry.backoff.exponential.multiplier", Double.class, 1.3));
        exponentialBackOffPolicy.setMaxInterval(environment.getProperty("retry.backoff.exponential.max-interval", Integer.class, 3000));

        template.setBackOffPolicy(exponentialBackOffPolicy);
        template.setRetryPolicy(retryPolicy);

        return template;
    }
}