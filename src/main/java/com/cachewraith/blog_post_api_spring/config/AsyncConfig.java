package com.cachewraith.blog_post_api_spring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background execution.
 *
 * <p>Only scheduling is enabled. Asynchronous work goes through RabbitMQ rather than
 * {@code @Async}, so there is no executor to define here — the queues already give retries and a
 * dead-letter path that a thread pool would not.
 */
@Configuration
@EnableScheduling
public class AsyncConfig {}
