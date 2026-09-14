CREATE TABLE accounts (
    account_id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE payments (
    payment_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    merchant_reference VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    correlation_id UUID NOT NULL,

    CONSTRAINT fk_payments_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(account_id)
);

CREATE TABLE payment_audit (
    audit_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_payment_audit_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments(payment_id)
);
