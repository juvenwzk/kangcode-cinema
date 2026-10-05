import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** 验证本地 MySQL 是否就绪（库、账号、表结构）。用法: java MysqlCheck <url> <user> <pass> <db> */
public class MysqlCheck {
    public static void main(String[] args) {
        String url = args[0], user = args[1], pass = args[2], db = args[3];
        System.out.println("目标: " + url + "  用户=" + user);
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Throwable t) {
            System.out.println("[WARN] 加载 com.mysql.cj.jdbc.Driver 失败: " + t.getMessage());
        }
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            System.out.println("[OK] 连接成功: " + c.getMetaData().getDatabaseProductName()
                    + " " + c.getMetaData().getDatabaseProductVersion());
            try (Statement st = c.createStatement()) {
                ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='" + db + "'");
                rs.next();
                System.out.println("     `" + db + "` 库中表数量: " + rs.getInt(1));

                rs = st.executeQuery("SHOW TABLES FROM `" + db + "`");
                StringBuilder sb = new StringBuilder();
                int n = 0;
                while (rs.next()) {
                    if (n++ > 0) sb.append(", ");
                    sb.append(rs.getString(1));
                }
                System.out.println("     表: " + (sb.length() == 0 ? "(空库)" : sb.toString()));
            }
        } catch (Exception e) {
            System.out.println("[FAIL] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
