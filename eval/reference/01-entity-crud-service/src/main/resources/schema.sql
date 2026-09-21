CREATE TABLE IF NOT EXISTS tasks (
  tenant     VARCHAR(64)  NOT NULL,
  id         VARCHAR(64)  NOT NULL,
  created_by VARCHAR(128) NOT NULL,
  created_at TIMESTAMP    NOT NULL,
  updated_by VARCHAR(128) NOT NULL,
  updated_at TIMESTAMP    NOT NULL,
  title      VARCHAR(200) NOT NULL,
  PRIMARY KEY (tenant, id)
);
