package com.plexon.jobs.storage;

import com.plexon.jobs.model.JobProgress;
import com.plexon.jobs.model.PlayerJobsProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class JobsDatabase {
    public static final int SCHEMA_VERSION = 2;
    private final Path dbPath;

    public JobsDatabase(Path dbPath) {
        this.dbPath = dbPath;
    }

    public Path path() { return dbPath; }

    public void initialize() {
        try {
            Files.createDirectories(dbPath.getParent());
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = open(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("CREATE TABLE IF NOT EXISTS migration_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
                Integer current = readSchemaVersion(connection);
                if (current != null && current > SCHEMA_VERSION) {
                    throw new IllegalStateException("Database schema " + current + " is newer than supported schema " + SCHEMA_VERSION);
                }
                if (current != null && current < 1) throw new IllegalStateException("Invalid PlexonJobs schema version " + current);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS players(
                      player_uuid TEXT PRIMARY KEY,
                      last_name TEXT,
                      updated_at INTEGER NOT NULL
                    )
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_jobs(
                      player_uuid TEXT NOT NULL,
                      job_id TEXT NOT NULL,
                      joined INTEGER NOT NULL,
                      total_xp INTEGER NOT NULL,
                      level INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL,
                      PRIMARY KEY(player_uuid, job_id)
                    )
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS daily_earnings(
                      player_uuid TEXT NOT NULL,
                      job_id TEXT NOT NULL,
                      day_id INTEGER NOT NULL,
                      money_units INTEGER NOT NULL,
                      xp_units INTEGER NOT NULL,
                      PRIMARY KEY(player_uuid, job_id, day_id)
                    )
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS shadow_totals(
                      player_uuid TEXT NOT NULL,
                      job_id TEXT NOT NULL,
                      money_units INTEGER NOT NULL,
                      xp_units INTEGER NOT NULL,
                      event_count INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL,
                      PRIMARY KEY(player_uuid, job_id)
                    )
                    """);
                writeSchemaVersion(connection, SCHEMA_VERSION);
            }
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Failed to initialize PlexonJobs database", ex);
        }
    }

    public int schemaVersion() {
        try (Connection connection = open()) {
            Integer version = readSchemaVersion(connection);
            return version == null ? 0 : version;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read PlexonJobs schema version", ex);
        }
    }

    public PlayerJobsProfile load(UUID playerId) {
        PlayerJobsProfile profile = new PlayerJobsProfile(playerId, PlayerJobsProfile.State.READY);
        try (Connection connection = open();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT job_id, joined, total_xp, level FROM player_jobs WHERE player_uuid=?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    profile.put(rs.getString(1), new JobProgress(rs.getLong(3), rs.getInt(4), rs.getInt(2) != 0));
                }
            }
            return profile;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load jobs profile " + playerId, ex);
        }
    }

    public void save(PlayerJobsProfile.Snapshot profile, String lastName) {
        long now = System.currentTimeMillis();
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement player = connection.prepareStatement("""
                    INSERT INTO players(player_uuid,last_name,updated_at) VALUES(?,?,?)
                    ON CONFLICT(player_uuid) DO UPDATE SET last_name=excluded.last_name, updated_at=excluded.updated_at
                    """);
                 PreparedStatement job = connection.prepareStatement("""
                    INSERT INTO player_jobs(player_uuid,job_id,joined,total_xp,level,updated_at) VALUES(?,?,?,?,?,?)
                    ON CONFLICT(player_uuid,job_id) DO UPDATE SET
                      joined=excluded.joined,total_xp=excluded.total_xp,level=excluded.level,updated_at=excluded.updated_at
                    """)) {
                player.setString(1, profile.playerId().toString());
                player.setString(2, lastName == null ? "" : lastName);
                player.setLong(3, now);
                player.executeUpdate();
                for (var entry : profile.jobs().entrySet()) {
                    PlayerJobsProfile.ProgressSnapshot progress = entry.getValue();
                    job.setString(1, profile.playerId().toString());
                    job.setString(2, entry.getKey());
                    job.setInt(3, progress.joined() ? 1 : 0);
                    job.setLong(4, progress.totalXp());
                    job.setInt(5, progress.level());
                    job.setLong(6, now);
                    job.addBatch();
                }
                job.executeBatch();
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save jobs profile " + profile.playerId(), ex);
        }
    }

    public Map<String, DailyRow> loadDaily(UUID playerId, long dayId) {
        Map<String, DailyRow> rows = new LinkedHashMap<>();
        try (Connection connection = open(); PreparedStatement ps = connection.prepareStatement(
                "SELECT job_id,money_units,xp_units FROM daily_earnings WHERE player_uuid=? AND day_id=? ORDER BY job_id")) {
            ps.setString(1, playerId.toString());
            ps.setLong(2, dayId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.put(rs.getString(1), new DailyRow(rs.getLong(2), rs.getLong(3)));
            }
            return Map.copyOf(rows);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load daily earnings for " + playerId + " / " + dayId, ex);
        }
    }

    /** Writes absolute same-day counters; retries cannot double an already persisted snapshot. */
    public void saveDaily(UUID playerId, long dayId, Map<String, DailyRow> rows) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) return;
        String sql = """
                INSERT INTO daily_earnings(player_uuid,job_id,day_id,money_units,xp_units)
                VALUES(?,?,?,?,?)
                ON CONFLICT(player_uuid,job_id,day_id) DO UPDATE SET
                  money_units=excluded.money_units,
                  xp_units=excluded.xp_units
                """;
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Map.Entry<String, DailyRow> entry : rows.entrySet()) {
                    DailyRow row = Objects.requireNonNull(entry.getValue(), "daily row");
                    ps.setString(1, playerId.toString());
                    ps.setString(2, Objects.requireNonNull(entry.getKey(), "jobId"));
                    ps.setLong(3, dayId);
                    ps.setLong(4, Math.max(0, row.moneyMinor()));
                    ps.setLong(5, Math.max(0, row.xp()));
                    ps.addBatch();
                }
                ps.executeBatch();
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save daily earnings for " + playerId + " / " + dayId, ex);
        }
    }

    public void addShadow(UUID playerId, String jobId, long moneyMinor, long xp, long eventCount) {
        addShadowBatch(List.of(new ShadowDelta(playerId, jobId, moneyMinor, xp, eventCount)));
    }

    /**
     * Persists one drained shadow-ledger batch as a single SQLite transaction. Callers may safely
     * requeue the entire batch after failure because no prefix can be committed independently.
     */
    public void addShadowBatch(Iterable<ShadowDelta> deltas) {
        Objects.requireNonNull(deltas, "deltas");
        String sql = """
                INSERT INTO shadow_totals(player_uuid,job_id,money_units,xp_units,event_count,updated_at)
                VALUES(?,?,?,?,?,?)
                ON CONFLICT(player_uuid,job_id) DO UPDATE SET
                  money_units=shadow_totals.money_units+excluded.money_units,
                  xp_units=shadow_totals.xp_units+excluded.xp_units,
                  event_count=shadow_totals.event_count+excluded.event_count,
                  updated_at=excluded.updated_at
                """;
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                for (ShadowDelta delta : deltas) {
                    Objects.requireNonNull(delta, "shadow delta");
                    ps.setString(1, Objects.requireNonNull(delta.playerId(), "playerId").toString());
                    ps.setString(2, Objects.requireNonNull(delta.jobId(), "jobId"));
                    ps.setLong(3, delta.moneyMinor());
                    ps.setLong(4, delta.xp());
                    ps.setLong(5, delta.eventCount());
                    ps.setLong(6, now);
                    ps.addBatch();
                }
                ps.executeBatch();
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write shadow aggregate batch", ex);
        }
    }

    public ShadowRow shadow(UUID playerId, String jobId) {
        try (Connection connection = open(); PreparedStatement ps = connection.prepareStatement(
                "SELECT money_units,xp_units,event_count FROM shadow_totals WHERE player_uuid=? AND job_id=?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new ShadowRow(rs.getLong(1), rs.getLong(2), rs.getLong(3)) : new ShadowRow(0, 0, 0);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read shadow aggregate", ex);
        }
    }

    private Integer readSchemaVersion(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT value FROM migration_meta WHERE key='schema_version'");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            try { return Integer.parseInt(rs.getString(1)); }
            catch (NumberFormatException ex) { throw new IllegalStateException("Invalid schema_version value", ex); }
        }
    }

    private void writeSchemaVersion(Connection connection, int version) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO migration_meta(key,value) VALUES('schema_version',?)
                ON CONFLICT(key) DO UPDATE SET value=excluded.value
                """)) {
            ps.setString(1, Integer.toString(version));
            ps.executeUpdate();
        }
    }

    private Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    public record DailyRow(long moneyMinor, long xp) {}
    public record ShadowDelta(UUID playerId, String jobId, long moneyMinor, long xp, long eventCount) {}
    public record ShadowRow(long moneyMinor, long xp, long events) {}
}
