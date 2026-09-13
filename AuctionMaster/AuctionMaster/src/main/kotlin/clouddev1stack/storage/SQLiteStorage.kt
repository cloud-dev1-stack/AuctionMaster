package clouddev1stack.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File

class SQLiteStorage(dataFolder: File) : JdbcStorage() {

    override val dataSource: HikariDataSource = run {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        val dbFile = File(dataFolder, "auctions.db")
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 1
            connectionTestQuery = "SELECT 1"
        }
        val ds = HikariDataSource(config)
        ds.connection.use { conn ->
            conn.createStatement().use { st ->
                st.execute("PRAGMA journal_mode=WAL")
                st.execute("PRAGMA synchronous=NORMAL")
                st.execute("PRAGMA temp_store=MEMORY")
                st.execute("PRAGMA cache_size=-8000")
            }
        }
        ds
    }
}
