package com.cachewraith.blog_post_api_spring.common.constant;

public final class AppConstants {

    private AppConstants() {}

    public static final String API_V1 = "/api/v1";

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Redis key prefixes. See CLAUDE.md / spec section 2. */
    public static final String KEY_OTP = "otp:";
    public static final String KEY_OTP_ATTEMPTS = "otp:attempts:";
    public static final String KEY_RESET = "reset:";
    public static final String KEY_BLACKLIST = "blacklist:";
    public static final String KEY_REFRESH = "refresh:";
    public static final String KEY_FEED = "feed:";
    public static final String KEY_POST_REACTIONS = "post:reactions:";
    public static final String KEY_RATE_LIMIT = "ratelimit:";

    public static final int OTP_MAX_ATTEMPTS = 5;
    public static final int OTP_LENGTH = 6;
}
