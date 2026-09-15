-- Baseline: the schema Hibernate had grown by ddl-auto=update up to September 2026.
-- Existing databases are baselined at this version and never run it; fresh ones do.

CREATE TABLE users (
    id       uuid PRIMARY KEY,
    email    varchar(255),
    password varchar(255) NOT NULL,
    role     varchar(255) CHECK (role IN ('USER', 'ADMIN', 'DEBUG')),
    username varchar(255)
);

CREATE TABLE user_preferences (
    id                 uuid PRIMARY KEY,
    user_id            uuid NOT NULL UNIQUE REFERENCES users (id),
    language           varchar(255),
    preferred_currency varchar(255),
    theme              varchar(255),
    privacy            varchar(255),
    timeframe          varchar(255),
    app_lock           boolean NOT NULL,
    lock_delay_seconds integer NOT NULL
);

CREATE TABLE user_accounts (
    id              uuid PRIMARY KEY,
    account_name    varchar(255),
    current_balance double precision,
    version         bigint,
    user_id         uuid REFERENCES users (id)
);

CREATE TABLE categories (
    id            uuid PRIMARY KEY,
    category_type varchar(255) CHECK (category_type IN ('INCOME', 'EXPENSE')),
    color         varchar(255),
    icon          varchar(255),
    is_active     boolean,
    label         varchar(255),
    parent_group  varchar(255),
    user_id       uuid REFERENCES users (id)
);

CREATE TABLE transactions (
    id                uuid PRIMARY KEY,
    amount            double precision,
    balance_after     double precision,
    balance_before    double precision,
    date              timestamp(6),
    description       varchar(255),
    kind              varchar(255) CHECK (kind IN ('INCOME', 'EXPENSE', 'TRANSFER_IN', 'TRANSFER_OUT')),
    transfer_group_id uuid,
    category_id       uuid REFERENCES categories (id),
    user_account_id   uuid REFERENCES user_accounts (id)
);

CREATE TABLE user_budgets (
    id          uuid PRIMARY KEY,
    max_amount  double precision NOT NULL,
    category_id uuid REFERENCES categories (id),
    user_id     uuid REFERENCES users (id)
);

CREATE TABLE planned_transactions (
    id              uuid PRIMARY KEY,
    amount          double precision,
    description     varchar(255),
    end_date        date,
    frequency       varchar(255) CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY', 'ONCE')),
    name            varchar(255),
    start_date      date,
    category_id     uuid REFERENCES categories (id),
    user_id         uuid REFERENCES users (id),
    user_account_id uuid REFERENCES user_accounts (id)
);
