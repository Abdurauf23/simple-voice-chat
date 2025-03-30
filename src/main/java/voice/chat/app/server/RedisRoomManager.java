package voice.chat.app.server;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisRoomManager {
    private final JedisPool pool = new JedisPool("localhost", 6380, "my_user", "my_user_password");

    public boolean roomExists(String roomName) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists("room:" + roomName);
        }
    }

    public boolean validateRoom(String roomName, String password) {
        try (Jedis jedis = pool.getResource()) {
            String pass = jedis.get("room:" + roomName);
            return pass != null && pass.equals(password);
        }
    }

    public void createRoom(String roomName, String password) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set("room:" + roomName, password);
        }
    }
}

