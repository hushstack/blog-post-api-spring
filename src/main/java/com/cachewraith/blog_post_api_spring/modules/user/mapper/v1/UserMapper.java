package com.cachewraith.blog_post_api_spring.modules.user.mapper.v1;

import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.PublicUserResponse;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.UserResponse;
import com.cachewraith.blog_post_api_spring.modules.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "status", expression = "java(user.getStatus().name())")
    UserResponse toResponse(User user);

    PublicUserResponse toPublicResponse(User user);

    AuthorSummary toAuthorSummary(User user);
}
