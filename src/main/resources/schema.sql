drop table if exists ntf_tx;

create table ntf_tx (
    id uuid primary key,
    notificationType varchar(32) not null,
    targetDestination varchar(255) not null,
    payload clob,
    notificationState varchar(1),
    failureCounter integer not null default 0,
    dispatchCounter integer not null default 0,
    isBlacklisted varchar(1) not null,
    lastMessage varchar(255),
    created timestamp not null,
    updated timestamp not null
);

comment on table ntf_tx is 'Notification transaction log';
comment on column ntf_tx.id is 'Primary key for the notification transaction';
comment on column ntf_tx.notificationType is 'Notification type';
comment on column ntf_tx.targetDestination is 'Target destination for the notification';
comment on column ntf_tx.payload is 'Notification payload content';
comment on column ntf_tx.notificationState is 'Current notification state (N=NEW, S=SENT, E=ERR, U=UNKNOWN, R=REJECTED)';
comment on column ntf_tx.failureCounter is 'Failure counter';
comment on column ntf_tx.dispatchCounter is 'Dispatch counter';
comment on column ntf_tx.isBlacklisted is 'Whether the notification is blacklisted or blocked (Y/N)';
comment on column ntf_tx.lastMessage is 'Last message or error detail';
comment on column ntf_tx.created is 'Created timestamp';
comment on column ntf_tx.updated is 'Last updated timestamp';

create index ix_ntf_tx_target_destination on ntf_tx (targetDestination);
create index ix_ntf_tx_notification_state on ntf_tx (notificationState);
