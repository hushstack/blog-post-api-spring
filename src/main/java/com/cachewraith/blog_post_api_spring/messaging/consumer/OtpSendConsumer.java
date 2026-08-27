package com.cachewraith.blog_post_api_spring.messaging.consumer;

import com.cachewraith.blog_post_api_spring.config.RabbitMQConfig;
import com.cachewraith.blog_post_api_spring.integration.email.EmailService;
import com.cachewraith.blog_post_api_spring.messaging.event.OtpSendEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Delivers the OTP through {@link EmailService}.
 *
 * <p>The code itself is never written to the log here — only the user id — so log access does not
 * become a way to take over an account (OWASP A09). A delivery failure propagates so the message
 * dead-letters and stays inspectable rather than disappearing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtpSendConsumer {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_OTP_SEND)
    public void handle(OtpSendEvent event) {
        log.info("Dispatching {} OTP for user {}", event.purpose(), event.userId());
        emailService.sendOtp(event.email(), event.code(), event.purpose());
    }
}
