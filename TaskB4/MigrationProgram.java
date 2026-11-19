import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MigrationProgram {
    private static final String DB_URL = "jdbc:sqlite:taskb4_migration.db";

    public static void initializeDatabase(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS bus_pass");
            st.executeUpdate("CREATE TABLE bus_pass (pass_id TEXT, reg_no TEXT, name TEXT, route_no INTEGER, amount REAL)");
        }
    }

    public static void createSampleFile(Path path) throws IOException {
        List<String> lines = List.of(
                "P1001,192311001,Arun,12,4500.0",
                "P1002,192311002,Bina,7,3500.0",
                "BAD,INVALID,Record,abc,100",
                "P1003,192311003,Divya,14,3800.0",
                "P1004,192311004,Farah,,4500.0",
                "P1005,192311005,John,5,valid"
        );
        Files.write(path, lines);
    }

    public static List<String[]> readValidPasses(Path file) throws IOException {
        List<String[]> valid = new ArrayList<>();
        List<String[]> invalid = new ArrayList<>();
        List<String> raw = Files.readAllLines(file);
        for (String line : raw) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split(",", -1);
            if (parts.length != 5) {
                invalid.add(parts);
                continue;
            }
            try {
                Integer.parseInt(parts[3]);
                Double.parseDouble(parts[4]);
                valid.add(parts);
            } catch (NumberFormatException e) {
                invalid.add(parts);
            }
        }
        System.out.println("Valid rows: " + valid.size());
        System.out.println("Rejected rows: " + invalid.size());
        for (String[] row : invalid) {
            System.out.println("Rejected: " + String.join(",", row));
        }
        return valid;
    }

    public static int batchInsert(Connection con, List<String[]> passes, int batchSize) throws SQLException {
        String sql = "INSERT INTO bus_pass(pass_id, reg_no, name, route_no, amount) VALUES (?, ?, ?, ?, ?)";
        boolean oldAutoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        int loaded = 0;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (String[] p : passes) {
                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.setString(3, p[2]);
                ps.setInt(4, Integer.parseInt(p[3]));
                ps.setDouble(5, Double.parseDouble(p[4]));
                ps.addBatch();
                loaded++;
                if (loaded % batchSize == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
            con.commit();
            return loaded;
        } catch (SQLException | RuntimeException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(oldAutoCommit);
        }
    }

    public static void reportMetaData(Connection con) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        System.out.println("Database product: " + meta.getDatabaseProductName());
        System.out.println("Driver version: " + meta.getDriverVersion());
        try (ResultSet rs = meta.getTables(null, null, null, new String[] {"TABLE"})) {
            System.out.print("Tables: ");
            boolean first = true;
            while (rs.next()) {
                if (!first) {
                    System.out.print(", ");
                }
                System.out.print(rs.getString("TABLE_NAME"));
                first = false;
            }
            System.out.println();
        }
    }

    public static void main(String[] args) throws Exception {
        Path sampleFile = Path.of("TaskB4", "sample_passes.csv");
        createSampleFile(sampleFile);

        try (Connection con = DriverManager.getConnection(DB_URL)) {
            initializeDatabase(con);
            List<String[]> validRows = readValidPasses(sampleFile);
            int rowsLoaded = batchInsert(con, validRows, 1000);
            System.out.println("Rows persisted: " + rowsLoaded);
            reportMetaData(con);

            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM bus_pass")) {
                rs.next();
                System.out.println("Final row count: " + rs.getInt(1));
            }
        }
    }
}
