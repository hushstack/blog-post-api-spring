-- Notifications raised by the notification.dispatch consumer and read back through
-- /api/v1/notifications. Closes the gap noted in CLAUDE.md, where the consumer logged an event
-- but persisted nothing because no entity existed.

create table notifications (
    id           uuid        primary key,
    recipient_id uuid        not null,
    -- Null for a notification raised by the system rather than by another user.
    actor_id     uuid,
    type         varchar(32) not null,
    -- No foreign key: what target_id points at depends on type (friendship, comment, post), and a
    -- single column cannot reference three tables. Same reasoning as reactions.target_id.
    target_id    uuid,
    -- Null while unread. The instant is kept rather than a boolean flag so "when" comes for free.
    read_at      timestamp(6) with time zone,
    created_at   timestamp(6) with time zone not null,
    updated_at   timestamp(6) with time zone not null,
    constraint fk_notifications_recipient foreign key (recipient_id) references users (id) on delete cascade,
    constraint fk_notifications_actor foreign key (actor_id) references users (id) on delete cascade,
    constraint ck_notifications_type check (type in ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'COMMENT', 'REPOST'))
);

-- Serves the default listing, which pages on created_at desc for one recipient.
create index ix_notifications_recipient_created on notifications (recipient_id, created_at);
-- Serves the unread badge count and the unreadOnly listing.
create index ix_notifications_recipient_unread on notifications (recipient_id, read_at);
