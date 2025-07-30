CREATE TABLE soht2_users (
  user_id       BIGINT AUTO_INCREMENT,
  user_name     VARCHAR(30)        NOT NULL,
  user_password VARCHAR(255)       NOT NULL,
  user_role     VARCHAR(10)        NOT NULL,
  targets       VARCHAR(255) ARRAY NOT NULL DEFAULT ARRAY ['*:*'],
  created_at    TIMESTAMP          NOT NULL,
  updated_at    TIMESTAMP,
  CONSTRAINT soht2_users_pk PRIMARY KEY (user_id),
  CONSTRAINT soht2_users_uk UNIQUE (user_name)
);
