ALTER TABLE soht2_history
  ADD COLUMN bytes_read BIGINT NOT NULL DEFAULT 0;
ALTER TABLE soht2_history
  ADD COLUMN bytes_written BIGINT NOT NULL DEFAULT 0;

CREATE INDEX soht2_history_br_idx ON soht2_history (bytes_read);
CREATE INDEX soht2_history_bw_idx ON soht2_history (bytes_written);
