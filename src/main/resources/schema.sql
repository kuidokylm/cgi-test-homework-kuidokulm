drop table if exists ntf_tx;

create table ntf_tx (
    id bigint primary key,
    notificationType varchar(32) not null,
    targetDestination varchar(255) not null,
    payload clob,
    notificationState varchar(1),
    failureCounter integer not null default 0,
    dispatchCounter integer not null default 0,
    lastMessage varchar(255),
    created timestamp not null,
    updated timestamp null
);

comment on table ntf_tx is 'Notification transaction log';
comment on column ntf_tx.id is 'Primary key for the notification transaction';
comment on column ntf_tx.notificationType is 'Notification type';
comment on column ntf_tx.targetDestination is 'Target destination for the notification';
comment on column ntf_tx.payload is 'Notification payload content';
comment on column ntf_tx.notificationState is 'Current notification state (N=NEW, S=SENT, E=ERR, U=UNKNOWN, R=REJECTED)';
comment on column ntf_tx.failureCounter is 'Failure counter';
comment on column ntf_tx.dispatchCounter is 'Dispatch counter';
comment on column ntf_tx.lastMessage is 'Last message or error detail';
comment on column ntf_tx.created is 'Created timestamp';
comment on column ntf_tx.updated is 'Last updated timestamp';

create index ix_ntf_tx_target_destination on ntf_tx (targetDestination);
create index ix_ntf_tx_dispatch_counter on ntf_tx (dispatchCounter);

drop table if exists ntf_blacklist;

create table ntf_blacklist (
    targetDestination varchar(255) primary key,
    created timestamp not null
);