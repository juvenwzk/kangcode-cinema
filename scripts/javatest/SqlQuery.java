import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

/** 通用只读查询工具。用法: java SqlQuery <url> <user> <pass> "<SQL>" */
public class SqlQuery {
    public static void main(String[] args) throws Exception {
        String url = args[0], user = args[1], pass = args[2], sql = args[3];
        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            StringBuilder head = new StringBuilder();
            for (int i = 1; i <= n; i++) {
                if (i > 1) head.append(" | ");
                head.append(md.getColumnLabel(i));
            }
            System.out.println("  " + head);
            System.out.println("  " + new String(new char[Math.min(head.length(), 90)]).replace('\0', '-'));
            int rows = 0;
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= n; i++) {
                    if (i > 1) sb.append(" | ");
                    String v = rs.getString(i);
                    sb.append(v == null ? "NULL" : v);
                }
                System.out.println("  " + sb);
                if (++rows >= 30) { System.out.println("  ...(截断)"); break; }
            }
            System.out.println("  共 " + rows + " 行");
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
