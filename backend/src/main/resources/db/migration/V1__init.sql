create table concerts (
    id bigint primary key,
    title varchar(100) not null,
    created_at timestamptz not null default now()
);

create table seats (
    id bigint primary key,
    concert_id bigint not null references concerts(id),
    seat_label varchar(20) not null,
    status varchar(40) not null default 'AVAILABLE',
    updated_at timestamptz not null default now(),
    unique (concert_id, seat_label)
);

create table simulation_sessions (
    id uuid primary key,
    concert_id bigint not null references concerts(id),
    requested_users integer not null,
    status varchar(40) not null,
    created_at timestamptz not null default now()
);

create table virtual_users (
    id uuid primary key,
    simulation_id uuid not null references simulation_sessions(id),
    display_name varchar(40) not null,
    status varchar(40) not null,
    selected_seat_id bigint references seats(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table reservations (
    id bigint primary key,
    seat_id bigint not null references seats(id),
    virtual_user_id uuid references virtual_users(id),
    status varchar(40) not null,
    idempotency_key varchar(100) not null unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index active_reservation_per_seat
    on reservations(seat_id)
    where status in ('HELD', 'PAYMENT_IN_PROGRESS', 'RESERVED');

create table payments (
    id bigint primary key,
    reservation_id bigint not null references reservations(id),
    status varchar(40) not null,
    idempotency_key varchar(100) not null unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table outbox_events (
    id uuid primary key,
    aggregate_type varchar(80) not null,
    aggregate_id varchar(120) not null,
    event_type varchar(120) not null,
    payload jsonb not null,
    published_at timestamptz,
    created_at timestamptz not null default now()
);

create index outbox_unpublished_idx on outbox_events(created_at) where published_at is null;
