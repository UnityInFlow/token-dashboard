package dev.unityinflow.tokendashboard.db

import dev.unityinflow.tokendashboard.db.tables.AgentCallsTable
import dev.unityinflow.tokendashboard.db.tables.AlertHistoryTable
import dev.unityinflow.tokendashboard.db.tables.BudgetAlertsTable
import dev.unityinflow.tokendashboard.db.tables.ModelCostsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

object DatabaseFactory {
    fun init(dbPath: String): Database {
        logger.info { "Initializing SQLite database at $dbPath" }

        val db =
            Database.connect(
                url = "jdbc:sqlite:$dbPath",
                driver = "org.sqlite.JDBC",
            )

        transaction(db) {
            SchemaUtils.create(
                SessionsTable,
                AgentCallsTable,
                ModelCostsTable,
                BudgetAlertsTable,
                AlertHistoryTable,
            )
        }

        logger.info { "Database initialized successfully" }
        return db
    }
}
