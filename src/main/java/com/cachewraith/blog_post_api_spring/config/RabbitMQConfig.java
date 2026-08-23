package com.cachewraith.blog_post_api_spring.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queues from spec section 3. Each work queue is durable and dead-lettered, so a message that keeps
 * failing lands somewhere inspectable instead of looping forever or vanishing (OWASP A10).
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "blog.exchange";
    public static final String DLX = "blog.dlx";

    public static final String QUEUE_OTP_SEND = "otp.send";
    public static final String QUEUE_IMAGE_PROCESS = "image.process";
    public static final String QUEUE_FEED_FANOUT = "feed.fanout";
    public static final String QUEUE_REACTION_SYNC = "reaction.sync";
    public static final String QUEUE_NOTIFICATION_DISPATCH = "notification.dispatch";

    @Bean
    public TopicExchange blogExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX, true, false);
    }

    private Queue durableQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(name + ".dlq")
                .build();
    }

    @Bean
    public Queue otpSendQueue() {
        return durableQueue(QUEUE_OTP_SEND);
    }

    @Bean
    public Queue imageProcessQueue() {
        return durableQueue(QUEUE_IMAGE_PROCESS);
    }

    @Bean
    public Queue feedFanoutQueue() {
        return durableQueue(QUEUE_FEED_FANOUT);
    }

    @Bean
    public Queue reactionSyncQueue() {
        return durableQueue(QUEUE_REACTION_SYNC);
    }

    @Bean
    public Queue notificationDispatchQueue() {
        return durableQueue(QUEUE_NOTIFICATION_DISPATCH);
    }

    @Bean
    public Queue otpSendDlq() {
        return QueueBuilder.durable(QUEUE_OTP_SEND + ".dlq").build();
    }

    @Bean
    public Queue imageProcessDlq() {
        return QueueBuilder.durable(QUEUE_IMAGE_PROCESS + ".dlq").build();
    }

    @Bean
    public Queue feedFanoutDlq() {
        return QueueBuilder.durable(QUEUE_FEED_FANOUT + ".dlq").build();
    }

    @Bean
    public Queue reactionSyncDlq() {
        return QueueBuilder.durable(QUEUE_REACTION_SYNC + ".dlq").build();
    }

    @Bean
    public Queue notificationDispatchDlq() {
        return QueueBuilder.durable(QUEUE_NOTIFICATION_DISPATCH + ".dlq").build();
    }

    @Bean
    public Binding otpSendBinding() {
        return BindingBuilder.bind(otpSendQueue()).to(blogExchange()).with(QUEUE_OTP_SEND);
    }

    @Bean
    public Binding imageProcessBinding() {
        return BindingBuilder.bind(imageProcessQueue()).to(blogExchange()).with(QUEUE_IMAGE_PROCESS);
    }

    @Bean
    public Binding feedFanoutBinding() {
        return BindingBuilder.bind(feedFanoutQueue()).to(blogExchange()).with(QUEUE_FEED_FANOUT);
    }

    @Bean
    public Binding reactionSyncBinding() {
        return BindingBuilder.bind(reactionSyncQueue()).to(blogExchange()).with(QUEUE_REACTION_SYNC);
    }

    @Bean
    public Binding notificationDispatchBinding() {
        return BindingBuilder.bind(notificationDispatchQueue())
                .to(blogExchange())
                .with(QUEUE_NOTIFICATION_DISPATCH);
    }

    @Bean
    public Binding otpSendDlqBinding() {
        return BindingBuilder.bind(otpSendDlq()).to(deadLetterExchange()).with(QUEUE_OTP_SEND + ".dlq");
    }

    @Bean
    public Binding imageProcessDlqBinding() {
        return BindingBuilder.bind(imageProcessDlq())
                .to(deadLetterExchange())
                .with(QUEUE_IMAGE_PROCESS + ".dlq");
    }

    @Bean
    public Binding feedFanoutDlqBinding() {
        return BindingBuilder.bind(feedFanoutDlq())
                .to(deadLetterExchange())
                .with(QUEUE_FEED_FANOUT + ".dlq");
    }

    @Bean
    public Binding reactionSyncDlqBinding() {
        return BindingBuilder.bind(reactionSyncDlq())
                .to(deadLetterExchange())
                .with(QUEUE_REACTION_SYNC + ".dlq");
    }

    @Bean
    public Binding notificationDispatchDlqBinding() {
        return BindingBuilder.bind(notificationDispatchDlq())
                .to(deadLetterExchange())
                .with(QUEUE_NOTIFICATION_DISPATCH + ".dlq");
    }

    /** Jackson 3 converter; it builds its own JsonMapper, so no ObjectMapper is injected here. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
