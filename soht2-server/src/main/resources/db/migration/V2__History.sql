CREATE TABLE soht2_history (
  history_id    BIGINT AUTO_INCREMENT,
  user_name     VARCHAR(30)  NOT NULL,
  connection_id UUID         NOT NULL,
  client_host   VARCHAR(255) NOT NULL,
  target_host   VARCHAR(255) NOT NULL,
  target_port   INT          NOT NULL,
  opened_at     TIMESTAMP    NOT NULL,
  closed_at     TIMESTAMP    NOT NULL,
  CONSTRAINT soht2_history_pk PRIMARY KEY (history_id)
);

CREATE INDEX soht2_history_un_idx ON soht2_history (user_name);
CREATE INDEX soht2_history_ci_idx ON soht2_history (connection_id);
CREATE INDEX soht2_history_ch_idx ON soht2_history (client_host);
CREATE INDEX soht2_history_th_idx ON soht2_history (target_host);
CREATE INDEX soht2_history_tp_idx ON soht2_history (target_port);
CREATE INDEX soht2_history_oa_idx ON soht2_history (opened_at);
CREATE INDEX soht2_history_ca_idx ON soht2_history (closed_at);
