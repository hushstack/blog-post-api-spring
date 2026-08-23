package com.cachewraith.blog_post_api_spring.messaging.consumer;

import com.cachewraith.blog_post_api_spring.config.RabbitMQConfig;
import com.cachewraith.blog_post_api_spring.messaging.event.OtpSendEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Delivers the OTP. Wire a real mail or SMS provider here.
 *
 * <p>The code itself is never written to the log — only the user id — so log access does not become
 * a way to take over an account (OWASP A09).
 */
@Slf4j
@Component
public class OtpSendConsumer {

    @RabbitListener(queues = RabbitMQConfig.QUEUE_OTP_SEND)
    public void handle(OtpSendEvent event) {
        log.info("Dispatching {} OTP for user {}", event.purpose(), event.userId());
        // TODO: integrate an email/SMS provider; the code is in event.code().
    }
}
