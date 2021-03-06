package net.postchain.gtx

import net.postchain.StorageBuilder
import net.postchain.base.withWriteConnection
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.MapConfiguration
import org.junit.Assert
import org.junit.Test
import java.nio.file.Paths

class SQLModuleTest {

    @Test
    fun testModule() {
        val moduleFileName = Paths.get(javaClass.getResource("sqlmodule1.sql").toURI()).toString()
        val config = gtx(
                "gtx" to gtx("sqlmodules" to gtx(gtx(moduleFileName)))
        )

        val mf = SQLGTXModuleFactory()
        val module = mf.makeModule(config, testBlockchainRID)

        val nodeConfig = NodeConfigurationProviderFactory.createProvider(
                AppConfig(getDatabaseConfig())
        ).getConfiguration()

        val storage = StorageBuilder.buildStorage(nodeConfig, 0)
        withWriteConnection(storage, 1) {
            GTXSchemaManager.initializeDB(it)
            module.initializeDB(it)
            Assert.assertTrue(module.getOperations().size == 1)
            false
        }
    }

    private fun getDatabaseConfig(): Configuration {
        return MapConfiguration(mapOf(
                "configuration.provider.node" to "legacy",
                "database.driverclass" to "org.postgresql.Driver",
                "database.url" to "jdbc:postgresql://localhost/postchain",
                "database.username" to "postchain",
                "database.password" to "postchain",
                "database.schema" to "testschema"
        ))
    }
}