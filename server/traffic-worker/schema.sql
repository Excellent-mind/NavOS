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

-- Usual speed of each piece per time-of-week bucket (weekend*24 + hour, Malaysia time).
CREATE TABLE IF NOT EXISTS typical (
  seg TEXT NOT NULL,
  bucket INTEGER NOT NULL,
  kmh REAL NOT NULL,
  n INTEGER NOT NULL,
  PRIMARY KEY (seg, bucket)
);

CREATE TABLE IF NOT EXISTS meta (
  k TEXT PRIMARY KEY,
  v TEXT NOT NULL
);

-- Road reports from drivers (accident, police, flood, hazard).
CREATE TABLE IF NOT EXISTS reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  kind TEXT NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  h INTEGER NOT NULL,    -- heading of the reporter, -1 = unknown
  sender TEXT NOT NULL,  -- day-keyed code, as for probes
  t INTEGER NOT NULL,
  expires INTEGER NOT NULL,
  up INTEGER NOT NULL,
  down INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS reports_live ON reports (expires, lat, lon);
CREATE INDEX IF NOT EXISTS reports_sender ON reports (sender, t);

-- One "still there" / "not there" per sender per report.
CREATE TABLE IF NOT EXISTS votes (
  id INTEGER NOT NULL,
  sender TEXT NOT NULL,
  v INTEGER NOT NULL,
  t INTEGER NOT NULL,
  PRIMARY KEY (id, sender)
);
CREATE INDEX IF NOT EXISTS votes_sender ON votes (sender, t);

-- Upgrading a database made before senders existed:
--   ALTER TABLE probes ADD COLUMN sender TEXT;
