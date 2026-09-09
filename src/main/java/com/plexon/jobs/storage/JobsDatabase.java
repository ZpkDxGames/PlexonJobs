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
import java.util.UUID;

public final class JobsDatabase {
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
                    CREATE TABLE IF NOT EXISTS migration_meta(
                      key TEXT PRIMARY KEY,
                      value TEXT NOT NULL
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
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize PlexonJobs database", ex);
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
                    profile.put(rs.getString(1),
                            new JobProgress(rs.getLong(3), rs.getInt(4), rs.getInt(2) != 0));
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

    public void addShadow(UUID playerId, String jobId, long moneyMinor, long xp, long eventCount) {
        try (Connection connection = open();
             PreparedStatement ps = connection.prepareStatement("""
                 INSERT INTO shadow_totals(player_uuid,job_id,money_units,xp_units,event_count,updated_at)
                 VALUES(?,?,?,?,?,?)
                 ON CONFLICT(player_uuid,job_id) DO UPDATE SET
                   money_units=shadow_totals.money_units+excluded.money_units,
                   xp_units=shadow_totals.xp_units+excluded.xp_units,
                   event_count=shadow_totals.event_count+1,
                   updated_at=excluded.updated_at
                 """)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, jobId);
            ps.setLong(3, moneyMinor);
            ps.setLong(4, xp);
            ps.setLong(5, eventCount);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write shadow aggregate", ex);
        }
    }

    private Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }
}
