-- 累土 JDBC adapter 的表约定：单表 + tenant/id + 审计四件套（adapter 统一读写）
CREATE TABLE IF NOT EXISTS orders (
  tenant       VARCHAR(64)  NOT NULL,
  id           VARCHAR(64)  NOT NULL,
  created_by   VARCHAR(128) NOT NULL,
  created_at   TIMESTAMP    NOT NULL,
  updated_by   VARCHAR(128) NOT NULL,
  updated_at   TIMESTAMP    NOT NULL,
  amount_cents BIGINT       NOT NULL,
  PRIMARY KEY (tenant, id)
);
