CREATE TABLE IF NOT EXISTS access_event (
  event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  request_id VARCHAR(64) NOT NULL,
  gateway_id VARCHAR(128) NOT NULL,
  route_id VARCHAR(128) NOT NULL,
  service_id VARCHAR(128) NOT NULL,
  instance_id VARCHAR(128) NOT NULL,
  method VARCHAR(16) NOT NULL,
  status INT NOT NULL,
  outcome VARCHAR(16) NOT NULL,
  reason VARCHAR(32) NOT NULL,
  duration_ms BIGINT NOT NULL,
  completed_at_ms BIGINT NOT NULL,
  INDEX idx_service_time (service_id, completed_at_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
