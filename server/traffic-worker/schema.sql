CREATE TABLE IF NOT EXISTS probes (
  seg TEXT NOT NULL,   -- "<osmNodeA>-<osmNodeB>", direction of travel
  sess TEXT NOT NULL,  -- random id for one drive; not linked to any person
  kmh REAL NOT NULL,
  t INTEGER NOT NULL,  -- server time, unix seconds
  PRIMARY KEY (seg, sess)
);
CREATE INDEX IF NOT EXISTS probes_t ON probes (t);
