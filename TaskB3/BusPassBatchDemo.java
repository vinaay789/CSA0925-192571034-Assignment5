import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class BusPassBatchDemo {
    private static final String DB_URL = "jdbc:sqlite:taskb3_bus_pass.db";

    public static void createTable(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS bus_pass");
            st.executeUpdate("CREATE TABLE bus_pass (pass_id TEXT, reg_no TEXT, name TEXT, route_no INTEGER, amount REAL)");
        }
    }

    public static void clearTable(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("DELETE FROM bus_pass");
        }
    }

    public static List<String[]> generatePasses(int count) {
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new String[] {
                "P" + String.format("%05d", i + 1),
                "2024" + String.format("%05d", i + 1),
                "Student" + i,
                String.valueOf((i % 20) + 1),
                String.valueOf(100 + (i % 500))
            });
        }
        return rows;
    }

    public static int insertWithStatement(Connection con, List<String[]> passes) throws SQLException {
        String sql = "INSERT INTO bus_pass(pass_id, reg_no, name, route_no, amount) VALUES ('%s', '%s', '%s', %s, %s)";
        int count = 0;
        try (Statement st = con.createStatement()) {
            for (String[] p : passes) {
                st.executeUpdate(String.format(sql, p[0], p[1], p[2], p[3], p[4]));
                count++;
            }
        }
        return count;
    }

    public static int insertWithPreparedStatement(Connection con, List<String[]> passes) throws SQLException {
        String sql = "INSERT INTO bus_pass(pass_id, reg_no, name, route_no, amount) VALUES (?, ?, ?, ?, ?)";
        int count = 0;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (String[] p : passes) {
                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.setString(3, p[2]);
                ps.setInt(4, Integer.parseInt(p[3]));
                ps.setDouble(5, Double.parseDouble(p[4]));
                ps.executeUpdate();
                count++;
            }
        }
        return count;
    }

    public static int insertWithBatch(Connection con, List<String[]> passes) throws SQLException {
        String sql = "INSERT INTO bus_pass(pass_id, reg_no, name, route_no, amount) VALUES (?, ?, ?, ?, ?)";
        int count = 0;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (String[] p : passes) {
                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.setString(3, p[2]);
                ps.setInt(4, Integer.parseInt(p[3]));
                ps.setDouble(5, Double.parseDouble(p[4]));
                ps.addBatch();
                count++;
                if (count % 1000 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
        return count;
    }

    public static void showMetaData(Connection con) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        System.out.println("Database product: " + meta.getDatabaseProductName());
        System.out.println("Driver: " + meta.getDriverVersion());
        try (ResultSet rs = meta.getTables(null, null, "bus_pass", null)) {
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

        try (PreparedStatement ps = con.prepareStatement("SELECT * FROM bus_pass LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData rmd = rs.getMetaData();
                System.out.println("Columns: ");
                for (int i = 1; i <= rmd.getColumnCount(); i++) {
                    System.out.printf("  %d: %s (%s)%n", i, rmd.getColumnName(i), rmd.getColumnTypeName(i));
                }
            }
        }
    }

    public static int countRows(Connection con) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM bus_pass")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public static void verifySmallData(Connection con) throws SQLException {
        List<String[]> rows = generatePasses(10);
        clearTable(con);
        insertWithPreparedStatement(con, rows);
        showMetaData(con);
        System.out.println("Rows in bus_pass after 10 inserts: " + countRows(con));
    }

    public static void benchmark(Connection con) throws SQLException {
        int[] sizes = {1000, 10000, 100000};
        for (int size : sizes) {
            List<String[]> rows = generatePasses(size);
            clearTable(con);
            long start = System.nanoTime();
            insertWithStatement(con, rows);
            long statementTime = (System.nanoTime() - start) / 1_000_000;

            clearTable(con);
            start = System.nanoTime();
            insertWithPreparedStatement(con, rows);
            long preparedTime = (System.nanoTime() - start) / 1_000_000;

            clearTable(con);
            start = System.nanoTime();
            insertWithBatch(con, rows);
            long batchTime = (System.nanoTime() - start) / 1_000_000;

            System.out.printf("Size=%d | Statement=%d ms | PreparedStatement=%d ms | Batch=%d ms%n", size, statementTime, preparedTime, batchTime);
        }
    }

    public static void transactionFailureDemo(Connection con) throws SQLException {
        List<String[]> rows = generatePasses(2000);
        rows.set(1000, new String[] {"P1001", "20249999", "BadAmount", "9", "NOT_A_NUMBER"});
        clearTable(con);

        boolean oldAutoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO bus_pass(pass_id, reg_no, name, route_no, amount) VALUES (?, ?, ?, ?, ?)")) {
            for (String[] p : rows) {
                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.setString(3, p[2]);
                ps.setInt(4, Integer.parseInt(p[3]));
                ps.setDouble(5, Double.parseDouble(p[4]));
                ps.addBatch();
            }
            ps.executeBatch();
            con.commit();
        } catch (Exception e) {
            con.rollback();
            System.out.println("Transaction failed as expected: " + e.getMessage());
        } finally {
            con.setAutoCommit(oldAutoCommit);
        }

        System.out.println("Rows after failed transaction: " + countRows(con));
    }

    public static void main(String[] args) throws Exception {
        try (Connection con = DriverManager.getConnection(DB_URL)) {
            createTable(con);
            verifySmallData(con);
            benchmark(con);
            transactionFailureDemo(con);
        }
    }
}
