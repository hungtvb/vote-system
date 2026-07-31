create table ranking_revision (
    singleton_id smallint primary key,
    revision bigint not null,
    updated_at timestamptz not null,
    constraint chk_ranking_revision_singleton check (singleton_id = 1),
    constraint chk_ranking_revision_non_negative check (revision >= 0)
);

insert into ranking_revision (singleton_id, revision, updated_at)
values (1, 0, current_timestamp);
