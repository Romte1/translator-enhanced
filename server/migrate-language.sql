-- Existing installations: the "translated" state is per target language now, so marking a
-- text in spanish no longer removes it from the french queue.
--
-- The old table only had a global hash. It is recreated; the marks are rebuilt by
-- scripts/mark-translated.mjs after the next merge.

DROP TABLE IF EXISTS translated;

CREATE TABLE translated (
	language TEXT NOT NULL,
	hash TEXT NOT NULL,
	at TEXT NOT NULL,
	PRIMARY KEY (language, hash)
);
