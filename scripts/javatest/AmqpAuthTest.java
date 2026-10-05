import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * 用 RabbitMQ 官方 Java 客户端（Spring AMQP 底层同一个库）测试凭据是否可用。
 * 用法: java AmqpAuthTest <host> <port> <user> <pass> <vhost>
 */
public class AmqpAuthTest {
    public static void main(String[] args) {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String user = args[2];
        String pass = args[3];
        String vhost = args[4];

        System.out.println("目标: " + host + ":" + port + "  用户=" + user + "  密码=" + pass + "  vhost=" + vhost);

        ConnectionFactory f = new ConnectionFactory();
        f.setHost(host);
        f.setPort(port);
        f.setUsername(user);
        f.setPassword(pass);
        f.setVirtualHost(vhost);
        f.setConnectionTimeout(8000);
        f.setHandshakeTimeout(8000);

        try (Connection c = f.newConnection()) {
            Object ver = c.getServerProperties().get("version");
            System.out.println("[OK] 连接成功！Broker 版本=" + ver
                    + "  连接数上限=" + c.getChannelMax());
        } catch (Exception e) {
            String msg = e.getMessage();
            System.out.println("[FAIL] " + e.getClass().getSimpleName() + ": " + msg);
            Throwable cause = e.getCause();
            if (cause != null) {
                System.out.println("       原因: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            }
        }
    }
}
