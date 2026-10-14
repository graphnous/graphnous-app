-- The names of the enhancers chosen to run on a scan's results when it was
-- created, as a JSON array such as ["react", "spring"]. Null when none
-- were chosen, which runs every installed enhancer: so for every scan
-- until now.

ALTER TABLE scans ADD COLUMN enhancers TEXT;
