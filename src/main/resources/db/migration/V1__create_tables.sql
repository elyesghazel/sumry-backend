CREATE TABLE categories
(
    id            UUID NOT NULL,
    label         VARCHAR(255),
    color         VARCHAR(255),
    icon          VARCHAR(255),
    category_type VARCHAR(255),
    CONSTRAINT pk_categories PRIMARY KEY (id)
);

CREATE TABLE planned_transactions
(
    id              UUID NOT NULL,
    name            VARCHAR(255),
    amount          DOUBLE PRECISION,
    description     VARCHAR(255),
    start_date      date,
    end_date        date,
    frequency       VARCHAR(255),
    user_account_id UUID,
    user_id         UUID,
    CONSTRAINT pk_planned_transactions PRIMARY KEY (id)
);

CREATE TABLE transactions
(
    id              UUID NOT NULL,
    amount          DOUBLE PRECISION,
    description     VARCHAR(255),
    user_account_id UUID,
    user_id         UUID,
    CONSTRAINT pk_transactions PRIMARY KEY (id)
);

CREATE TABLE user_accounts
(
    id              UUID NOT NULL,
    account_name    VARCHAR(255),
    current_balance DOUBLE PRECISION,
    user_id         UUID,
    CONSTRAINT pk_user_accounts PRIMARY KEY (id)
);

CREATE TABLE user_budgets
(
    id           UUID NOT NULL,
    limit_amount DOUBLE PRECISION,
    label        VARCHAR(255),
    budget_type  VARCHAR(255),
    start_date   date,
    user_id      UUID,
    category_id  UUID,
    CONSTRAINT pk_user_budgets PRIMARY KEY (id)
);

CREATE TABLE users
(
    id       UUID         NOT NULL,
    username VARCHAR(255),
    email    VARCHAR(255),
    password VARCHAR(255) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

ALTER TABLE planned_transactions
    ADD CONSTRAINT FK_PLANNED_TRANSACTIONS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE planned_transactions
    ADD CONSTRAINT FK_PLANNED_TRANSACTIONS_ON_USER_ACCOUNT FOREIGN KEY (user_account_id) REFERENCES user_accounts (id);

ALTER TABLE transactions
    ADD CONSTRAINT FK_TRANSACTIONS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE transactions
    ADD CONSTRAINT FK_TRANSACTIONS_ON_USERACCOUNT FOREIGN KEY (user_account_id) REFERENCES user_accounts (id);

ALTER TABLE user_accounts
    ADD CONSTRAINT FK_USER_ACCOUNTS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE user_budgets
    ADD CONSTRAINT FK_USER_BUDGETS_ON_CATEGORY FOREIGN KEY (category_id) REFERENCES categories (id);

ALTER TABLE user_budgets
    ADD CONSTRAINT FK_USER_BUDGETS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);