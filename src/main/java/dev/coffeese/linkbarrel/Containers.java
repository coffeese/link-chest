package dev.coffeese.linkbarrel;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.block.Container;

public class Containers {

    public static final class ContainerData {
        String name;
        String uuid;
        int x;
        int y;
        int z;
    }

    private static final String DBNAME = "containers.db";
    private static final String TABLENAME = "CONTAINER_V1";

    private Connection connection;

    private File dir;
    private Logger logger;

    public Containers(File dir, Logger logger) {
        this.dir = dir;
        this.logger = logger;
    }

    public boolean init() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            logger.log(Level.WARNING, "Could not load sqlite driver.", e);
            return false;
        }

        if (!prepare()) {
            return false;
        }

        if (!migration()) {
            shutdown();
            return false;
        }

        return true;
    }

    public void shutdown() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    connection = null;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Connection#isClose() failed...", e);
            }
        }
    }

    public List<ContainerData> loadContainers() {
        final String sql = "SELECT * FROM " + TABLENAME + ";";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ContainerData> list = new ArrayList<>();
                while (resultSet.next()) {
                    ContainerData data = new ContainerData();
                    data.name = resultSet.getString("name");
                    data.uuid = resultSet.getString("uuid");
                    data.x = resultSet.getInt("x");
                    data.y = resultSet.getInt("y");
                    data.z = resultSet.getInt("z");
                    list.add(data);
                }
                return list;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Load containers failed...", e);
        }

        return null;
    }

    public boolean addContainer(String name, Container container) {
        if (!prepare())
            return false;

        if (!saveContainer(name, container))
            return false;

        return true;
    }

    public boolean removeContainer(String name) {
        if (!prepare())
            return false;

        if (!deleteContainer(name))
            return false;

        return true;
    }

    private boolean prepare() {
        if (connection != null) {
            try {
                if (!connection.isClosed())
                    return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Connection#isClose() failed...", e);
            }
        }

        File dbDir = this.dir;
        if (!dbDir.exists()) {
            if(!dbDir.mkdirs()) {
                logger.log(Level.WARNING, "Create " + this.dir + " failed...");
                return false;
            }
        }

        File db = new File(dbDir, DBNAME);
        if (!db.exists()) {
            try {
                db.createNewFile();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Create " + DBNAME + " failed...", e);
                return false;
            }
        }

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "DriverManager#getConnection() failed...", e);
            return false;
        }

        return true;
    }

    private boolean migration() {
        try (Statement statement = connection.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS " + TABLENAME + "("
                + "name TEXT PRIMARY KEY,"
                + "uuid TEXT,"
                + "x INTEGER,"
                + "y INTEGER,"
                + "z INTEGER);";
            statement.executeUpdate(sql);
            return true;
        } catch (SQLException e) {
            logger.warning("Create database failed...");
            return false;
        }
    }

    private boolean saveContainer(String name, Container container) {
        final String sql = "INSERT INTO " + TABLENAME + "(name, uuid, x, y, z) VALUES(?, ?, ?, ?, ?);";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, container.getWorld().getUID().toString());
            statement.setInt(3, container.getX());
            statement.setInt(4, container.getY());
            statement.setInt(5, container.getZ());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Save container failed...", e);
            return false;
        }

        return true;
    }

    private boolean deleteContainer(String name) {
        final String sql = "DELETE FROM " + TABLENAME + " WHERE name = ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Save container failed...", e);
            return false;
        }

        return true;
    }
}
