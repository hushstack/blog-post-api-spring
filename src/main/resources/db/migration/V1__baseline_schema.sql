-- Baseline schema for the blog/social API.
--
-- This file is the authority on the schema; Hibernate only validates the entities against it
-- (ddl-auto=validate in every profile). Column names, types, lengths and nullability below were
-- taken from Hibernate's own generated DDL for the current entities, so `validate` passes on a
-- database built solely by this migration.
--
-- Identifiers are application-generated UUIDs (@GeneratedValue(strategy = UUID)), so no column
-- carries a database-side default and no uuid extension is required.
-- Timestamps are `timestamp with time zone`, which is what Hibernate 6+ maps java.time.Instant to.

create table users (
    id            uuid         primary key,
    email         varchar(254) not null,
    username      varchar(32)  not null,
    password_hash varchar(72)  not null,
    full_name     varchar(100),
    bio           varchar(500),
    avatar_url    varchar(500),
    status        varchar(24)  not null,
    created_at    timestamp(6) with time zone not null,
    updated_at    timestamp(6) with time zone not null,
    constraint ck_users_status check (status in ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED'))
);

-- Unique indexes rather than table constraints: one database object per rule, named as the
-- @Index entries on the entity name them.
create unique index ix_users_email on users (email);
create unique index ix_users_username on users (username);

-- Audit trail for OTP issuance. The verifiable code lives in Redis; only a hash is stored here,
-- so a database read cannot replay a code (OWASP A04).
create table otp_verifications (
    id         uuid         primary key,
    user_id    uuid         not null,
    code_hash  varchar(100) not null,
    purpose    varchar(24)  not null,
    expires_at timestamp(6) with time zone not null,
    consumed   boolean      not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint fk_otp_user foreign key (user_id) references users (id) on delete cascade,
    constraint ck_otp_purpose check (purpose in ('REGISTER', 'RESET_PASSWORD'))
);

create index ix_otp_user on otp_verifications (user_id);

create table posts (
    id               uuid        primary key,
    author_id        uuid        not null,
    content          text,
    visibility       varchar(16) not null,
    original_post_id uuid,
    reaction_count   bigint      not null,
    comment_count    bigint      not null,
    repost_count     bigint      not null,
    created_at       timestamp(6) with time zone not null,
    updated_at       timestamp(6) with time zone not null,
    constraint fk_posts_author foreign key (author_id) references users (id) on delete cascade,
    -- A repost outlives the post it quoted: null the link rather than cascading the delete.
    constraint fk_posts_original foreign key (original_post_id) references posts (id) on delete set null,
    constraint ck_posts_visibility check (visibility in ('PUBLIC', 'FRIENDS', 'PRIVATE'))
);

-- Serves both the profile timeline and the feed cursor, which page on created_at desc.
create index ix_posts_author_created on posts (author_id, created_at);
create index ix_posts_original on posts (original_post_id);

create table post_images (
    id         uuid         primary key,
    post_id    uuid         not null,
    url        varchar(500) not null,
    position   integer      not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint fk_post_images_post foreign key (post_id) references posts (id) on delete cascade
);

create index ix_post_images_post on post_images (post_id);

create table comments (
    id                uuid   primary key,
    post_id           uuid   not null,
    author_id         uuid   not null,
    parent_comment_id uuid,
    content           text   not null,
    reaction_count    bigint not null,
    created_at        timestamp(6) with time zone not null,
    updated_at        timestamp(6) with time zone not null,
    constraint fk_comments_post foreign key (post_id) references posts (id) on delete cascade,
    constraint fk_comments_author foreign key (author_id) references users (id) on delete cascade,
    constraint fk_comments_parent foreign key (parent_comment_id) references comments (id) on delete cascade
);

create index ix_comments_post_created on comments (post_id, created_at);
create index ix_comments_parent on comments (parent_comment_id);

create table reactions (
    id          uuid        primary key,
    target_type varchar(16) not null,
    target_id   uuid        not null,
    user_id     uuid        not null,
    type        varchar(16) not null,
    created_at  timestamp(6) with time zone not null,
    updated_at  timestamp(6) with time zone not null,
    -- One reaction per user per target. This constraint is what makes the reaction upsert safe
    -- under concurrent requests: the database, not the service, is the arbiter.
    constraint uq_reaction_target_user unique (target_type, target_id, user_id),
    constraint fk_reactions_user foreign key (user_id) references users (id) on delete cascade,
    constraint ck_reactions_target_type check (target_type in ('POST', 'COMMENT')),
    constraint ck_reactions_type check (type in ('LIKE', 'LOVE', 'HAHA', 'WOW', 'SAD', 'ANGRY'))
);

-- No foreign key on target_id: it points at either a post or a comment depending on target_type,
-- and a single column cannot reference two tables. Orphan reactions are swept by target_type.
create index ix_reactions_target on reactions (target_type, target_id);

create table friendships (
    id           uuid        primary key,
    requester_id uuid        not null,
    addressee_id uuid        not null,
    status       varchar(16) not null,
    responded_at timestamp(6) with time zone,
    created_at   timestamp(6) with time zone not null,
    updated_at   timestamp(6) with time zone not null,
    constraint uq_friendship_pair unique (requester_id, addressee_id),
    constraint fk_friendship_requester foreign key (requester_id) references users (id) on delete cascade,
    constraint fk_friendship_addressee foreign key (addressee_id) references users (id) on delete cascade,
    constraint ck_friendship_status check (status in ('PENDING', 'ACCEPTED', 'DECLINED', 'BLOCKED'))
);

-- The feed resolves a viewer's accepted friends from both directions on every read.
create index ix_friendship_requester on friendships (requester_id, status);
create index ix_friendship_addressee on friendships (addressee_id, status);
