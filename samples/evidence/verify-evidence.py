import sqlite3, sys
db = sys.argv[1]
c = sqlite3.connect(db)
print("TABLES:", [r[0] for r in c.execute("select name from sqlite_master where type='table'")])
cols = [r[1] for r in c.execute("PRAGMA table_info(evidence)")]
print("COLUMNS:", cols)
print("COUNT:", c.execute("select count(*) from evidence").fetchone()[0])
print("--- chain ---")
for r in c.execute("select id, source_type, http_method, endpoint_path, evidence_code, related_evidence_codes "
                   "from evidence order by id"):
    print(r)
print("--- source security detail ---")
for r in c.execute("select evidence_code, file, line_start, line_end, path_pattern, rule_type, content "
                   "from evidence where source_type='SOURCE_SECURITY'"):
    print(r)
print("--- conformance detail ---")
for r in c.execute("select evidence_code, rule_type, content from evidence where source_type='CONFORMANCE'"):
    print(r)