package xyz.gnarbot.gnar.db;

import com.rethinkdb.gen.exc.ReqlDriverError;
import com.rethinkdb.net.Connection;
import com.rethinkdb.net.Cursor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import xyz.gnarbot.gnar.db.guilds.GuildData;
import xyz.gnarbot.gnar.db.guilds.UserData;
import xyz.gnarbot.gnar.db.premium.PremiumGuild;
import xyz.gnarbot.gnar.db.premium.PremiumUser;

import javax.annotation.Nullable;
import java.util.List;

import static com.rethinkdb.RethinkDB.r;

public class Database {
    private static final Logger LOG = LoggerFactory.getLogger("Database");
    private final Connection conn;
    private static JedisPool defaultJedisPool = new JedisPool("localhost", 6379);
    private Config config = new Config();
    private RedissonClient redisson;

    public Database(String name) {
        config.useSingleServer().setAddress("redis://192.168.0.140:6379");
        redisson = Redisson.create(config);

        Connection conn = null;
        try {
            Connection.Builder builder = r.connection().hostname("localhost").port(28015);
            // potential spot for authentication
            conn = builder.connect();
            if (r.dbList().<List<String>>run(conn).contains(name)) {
                LOG.info("Connected to database.");
                conn.use(name);
            } else {
                LOG.info("Rethink Database `" + name + "` is not present. Closing connection.");
                close();
                System.exit(0);
            }
        } catch (ReqlDriverError e) {
            LOG.error("Rethink Database connection failed.", e);
            System.exit(0);
        }
        this.conn = conn;
    }

    public Connection getConn() {
        return conn;
    }

    public boolean isOpen() {
        return conn != null && conn.isOpen();
    }

    public void close() {
        conn.close();
    }

    @Nullable
    public GuildData getGuildData(String id) {
        return get("guilds_v2", id, GuildData.class);
    }

    @Nullable
    public PremiumKey getPremiumKey(String id) {
        return get("keys", id, PremiumKey.class);
    }

    public UserData getUserData(String id) {
        return get("users", id, UserData.class);
    }

    @Nullable
    public PatreonEntry getPatreonEntry(String id) {
        return get("patreon", id, PatreonEntry.class);
    }

    public boolean hasPremiumUser(String id) {
        return isOpen() ? r.table("premiumusers").get(id).coerceTo("bool").run(conn) : false;
    }

    public PremiumUser getPremiumUser(String id) {
        return !isOpen() ? null : r.table("premiumusers")
                .get(id)
                .default_(r.hashMap("id", id).with("pledgeAmount", "0.0"))
                .run(conn, PremiumUser.class);
    }

    public List<PremiumUser> getPremiumUsers() {
        if (!isOpen()) {
            return List.of();
        }

        Cursor<PremiumUser> cursor = r.table("premiumusers").run(conn, PremiumUser.class);
        return cursor.toList();
    }

    @Nullable
    public PremiumGuild getPremiumGuild(String id) {
        return get("premiumguilds", id, PremiumGuild.class);
    }

    @Nullable
    public Cursor<PremiumGuild> getPremiumGuilds(String id) {
        return isOpen()
                ? r.table("premiumguilds").filter(guild -> guild.g("redeemer").eq(id)).run(conn, PremiumGuild.class)
                : null;
    }

    @Nullable
    public <T> T get(String table, String id, Class<T> cls) {
        return isOpen() ? r.table(table).get(id).run(conn, cls) : null;
    }

    public static JedisPool getDefaultJedisPool() {
        return defaultJedisPool;
    }

    public RedissonClient getRedisson() {
        return redisson;
    }
}
