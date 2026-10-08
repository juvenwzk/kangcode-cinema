import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 执行写操作 SQL（INSERT / UPDATE / DELETE）。
 * 只读查询请用 SqlQuery。
 * 用法: java SqlExec <url> <user> <pass> "<SQL>"
 */
public class SqlExec {
    public static void main(String[] args) throws Exception {
        String url = args[0], user = args[1], pass = args[2], sql = args[3];
        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement st = c.createStatement()) {
            int n = st.executeUpdate(sql);
            System.out.println("  影响行数: " + n);
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
