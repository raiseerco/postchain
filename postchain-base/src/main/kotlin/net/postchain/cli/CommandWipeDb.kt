package net.postchain.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.StorageBuilder
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.core.NODE_ID_NA

@Parameters(commandDescription = "Wipe db")
class CommandWipeDb : Command {

    override fun key(): String = "wipe-db"

    @Parameter(
            names = ["-nc", "--node-config"],
            description = "Configuration file of blockchain (.properties file)")
    private var nodeConfigFile = ""

    override fun execute(): CliResult {
        return try {
            val nodeConfig = NodeConfigurationProviderFactory.createProvider(
                    AppConfig.fromPropertiesFile(nodeConfigFile)
            ).getConfiguration()
            StorageBuilder.buildStorage(nodeConfig, NODE_ID_NA, true)
            Ok("Wipe database successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }

    }

}