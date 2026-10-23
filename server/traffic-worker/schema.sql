CREATE TABLE IF NOT EXISTS probes (
  seg TEXT NOT NULL,     -- "<osmNodeA>-<osmNodeB>", direction of travel
  sess TEXT NOT NULL,    -- random id for one drive; not linked to any person
  sender TEXT,           -- one-way day-keyed code of the IP address (never the address)
  kmh REAL NOT NULL,
  t INTEGER NOT NULL,    -- server time, unix seconds
  PRIMARY KEY (seg, sess)
);
CREATE INDEX IF NOT EXISTS probes_t ON probes (t);
CREATE INDEX IF NOT EXISTS probes_sess ON probes (sess);

-- Per-sender limits for each 10-minute window.
CREATE TABLE IF NOT EXISTS quota (
  sender TEXT NOT NULL,
  w INTEGER NOT NULL,    -- unix time / 600
  pieces INTEGER NOT NULL,
  drives INTEGER NOT NULL,
  PRIMARY KEY (sender, w)
);

-- Upgrading a database made before senders existed:
--   ALTER TABLE probes ADD COLUMN sender TEXT;
