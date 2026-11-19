import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class UnsafeLoginDemo {
    private static final String DB_URL = "jdbc:sqlite:taskb2_users.db";

    public static void setupDatabase(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS users");
            st.executeUpdate("CREATE TABLE users (id TEXT PRIMARY KEY, pwd TEXT)");
            st.executeUpdate("INSERT INTO users (id, pwd) VALUES ('admin', 'secret')");
            st.executeUpdate("INSERT INTO users (id, pwd) VALUES ('student', 'pass123')");
        }
    }

    public static void main(String[] args) throws Exception {
        try (Connection con = DriverManager.getConnection(DB_URL)) {
            setupDatabase(con);

            String unsafeUser = "admin' -- ";
            String unsafePwd = "anything";

            String unsafeSql = "SELECT * FROM users WHERE id = '" + unsafeUser + "' AND pwd = '" + unsafePwd + "'";
            System.out.println("Unsafe SQL: " + unsafeSql);
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(unsafeSql)) {
                if (rs.next()) {
                    System.out.println("Unsafe check accepted an invalid login: " + rs.getString("id"));
                } else {
                    System.out.println("Unsafe check rejected the crafted login.");
                }
            }

            String safeSql = "SELECT * FROM users WHERE id = ? AND pwd = ?";
            try (PreparedStatement ps = con.prepareStatement(safeSql)) {
                ps.setString(1, unsafeUser);
                ps.setString(2, unsafePwd);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Safe check incorrectly accepted a login: " + rs.getString("id"));
                    } else {
                        System.out.println("Safe PreparedStatement rejected the crafted login, as expected.");
                    }
                }
            }
        }
    }
}
