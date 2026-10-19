CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, role TEXT NOT NULL CHECK(role IN ('admin','teacher','student')), salt TEXT NOT NULL, pw_hash TEXT NOT NULL, created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS sessions (hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id), expires_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS rooms (id TEXT PRIMARY KEY, title TEXT NOT NULL, teacher_id TEXT NOT NULL REFERENCES users(id), created_at INTEGER NOT NULL, ended_at INTEGER);
CREATE TABLE IF NOT EXISTS room_members (room_id TEXT NOT NULL REFERENCES rooms(id), user_id TEXT NOT NULL REFERENCES users(id), can_speak INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(room_id,user_id));
CREATE INDEX IF NOT EXISTS sessions_user_idx ON sessions(user_id);
CREATE INDEX IF NOT EXISTS rooms_teacher_idx ON rooms(teacher_id);
CREATE INDEX IF NOT EXISTS memberships_user_idx ON room_members(user_id);
CREATE TABLE IF NOT EXISTS login_failures (email_hash TEXT PRIMARY KEY, attempts INTEGER NOT NULL, window_start INTEGER NOT NULL);
