create table simulation_seats (
    simulation_id uuid not null references simulation_sessions(id),
    seat_id bigint not null,
    seat_label varchar(20) not null,
    status varchar(40) not null default 'AVAILABLE',
    held_by_user_id uuid references virtual_users(id),
    updated_at timestamptz not null default now(),
    primary key (simulation_id, seat_id),
    unique (simulation_id, seat_label)
);

drop index if exists active_reservation_per_seat;

alter table reservations
    add column simulation_id uuid references simulation_sessions(id);

alter table payments
    add column simulation_id uuid references simulation_sessions(id);

create unique index active_reservation_per_simulation_seat
    on reservations(simulation_id, seat_id)
    where simulation_id is not null
      and status in ('HELD', 'PAYMENT_IN_PROGRESS', 'RESERVED');

create unique index one_final_reservation_per_simulation_user
    on reservations(simulation_id, virtual_user_id)
    where simulation_id is not null
      and status in ('PAYMENT_IN_PROGRESS', 'RESERVED');
