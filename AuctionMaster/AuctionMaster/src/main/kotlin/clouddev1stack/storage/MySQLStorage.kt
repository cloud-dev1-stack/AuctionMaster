package clouddev1stack.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

class MySQLStorage(
    host: String,
    port: Int,
    database: String,
    username: String,
    password: String,
    useSSL: Boolean,
    poolSize: Int
) : JdbcStorage() {

    override val dataSource: HikariDataSource = run {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=$useSSL&autoReconnect=true&characterEncoding=utf8" +
                "&cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048" +
                "&useServerPrepStmts=true&useLocalSessionState=true&rewriteBatchedStatements=true"
            driverClassName = "com.mysql.cj.jdbc.Driver"
            this.username = username
            this.password = password
            maximumPoolSize = poolSize
            minimumIdle = poolSize.coerceAtMost(2)
            connectionTestQuery = "SELECT 1"
        }
        HikariDataSource(config)
    }
}
