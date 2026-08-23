package com.cachewraith.blog_post_api_spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BlogPostApiSpringApplication {

	public static void main(String[] args) {
		SpringApplication.run(BlogPostApiSpringApplication.class, args);
	}

}
