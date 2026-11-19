create table if not exists ntf_blacklist (
    targetDestination varchar(255) primary key,
    created timestamp not null
);