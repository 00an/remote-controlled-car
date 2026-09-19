DROP TABLE IF EXISTS "user" CASCADE;

CREATE TABLE IF NOT EXISTS "users" (
    id BIGSERIAL NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL,
    username TEXT NOT NULL,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    email TEXT NOT NULL,
    password TEXT NOT NULL,

    CONSTRAINT user_pkey PRIMARY KEY (id)
    );