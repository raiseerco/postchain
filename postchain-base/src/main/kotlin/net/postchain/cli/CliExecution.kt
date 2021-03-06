package net.postchain.cli

import net.postchain.PostchainNode
import net.postchain.base.BaseConfigurationDataStore
import net.postchain.base.data.BaseBlockStore
import net.postchain.base.data.SQLDatabaseAccess
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.gtx.encodeGTXValue
import net.postchain.gtx.gtxml.GTXMLValueParser
import org.apache.commons.configuration2.ex.ConfigurationException
import org.apache.commons.dbcp2.BasicDataSource
import java.io.File
import java.sql.Connection
import java.sql.SQLException

class CliExecution {

    private val dbAccess = SQLDatabaseAccess()

    fun addBlockchain(
            nodeConfigFile: String,
            chainId: Long,
            blockchainRID: String,
            blockchainConfigFile: String,
            mode: AlreadyExistMode = AlreadyExistMode.IGNORE
    ) {

        val encodedGtxValue = getEncodedGtxValueFromFile(blockchainConfigFile)
        runDBCommandBody(nodeConfigFile, chainId) { eContext ->

            fun init() {
                BaseBlockStore().initialize(eContext, blockchainRID.hexStringToByteArray())
                BaseConfigurationDataStore.addConfigurationData(eContext, 0, encodedGtxValue)
            }


            when (mode) {
                AlreadyExistMode.ERROR -> {
                    if (SQLDatabaseAccess().getBlockchainRID(eContext) == null) {
                        init()
                    } else {
                        throw CliError.Companion.CliException(
                                "Blockchain with chainId $chainId already exists. Use -f flag to force addition.")
                    }
                }

                AlreadyExistMode.FORCE -> {
                    init()
                }

                else -> {
                    if (SQLDatabaseAccess().getBlockchainRID(eContext) == null) {
                        init()
                    }
                }
            }
        }
    }

    fun addConfiguration(
            nodeConfigFile: String,
            blockchainConfigFile: String,
            chainId: Long,
            height: Long,
            mode: AlreadyExistMode = AlreadyExistMode.IGNORE
    ): Boolean {

        val encodedGtxValue = getEncodedGtxValueFromFile(blockchainConfigFile)
        var result = false
        runDBCommandBody(nodeConfigFile, chainId) { eContext ->

            fun init() {
                result = BaseConfigurationDataStore.addConfigurationData(eContext, height, encodedGtxValue) > 0
            }

            when (mode) {
                AlreadyExistMode.ERROR -> {
                    if (BaseConfigurationDataStore.getConfigurationData(eContext, height) == null) {
                        init()
                    } else {
                        throw CliError.Companion.CliException("Blockchain configuration of chainId $chainId at " +
                                "height $height already exists. Use -f flag to force addition.")
                    }
                }

                AlreadyExistMode.FORCE -> {
                    init()
                }

                else -> {
                    if (BaseConfigurationDataStore.getConfigurationData(eContext, height) == null) {
                        init()
                    } else {
                        println("Blockchain configuration of chainId $chainId at height $height already exists")
                    }
                }
            }
        }
        return result
    }

    fun runNode(nodeConfigFile: String, chainIDs: List<Long>) {
        val nodeConfigProvider = NodeConfigurationProviderFactory.createProvider(
                AppConfig.fromPropertiesFile(nodeConfigFile))

        with(PostchainNode(nodeConfigProvider)) {
            chainIDs.forEach(::startBlockchain)
        }
    }

    fun checkBlockchain(nodeConfigFile: String, chainId: Long, blockchainRID: String) {
        runDBCommandBody(nodeConfigFile, chainId) { eContext ->
            val chainIdBlockchainRid = dbAccess.getBlockchainRID(eContext)
            when {
                chainIdBlockchainRid == null -> {
                    throw CliError.Companion.CliException("Unknown chain-id: $chainId")
                }
                !blockchainRID.equals(chainIdBlockchainRid.toHex(), true) -> {
                    throw CliError.Companion.CliException("""
                        BlockchainRids are not equal:
                            expected: $blockchainRID
                            actual: ${chainIdBlockchainRid.toHex()}
                    """.trimIndent())
                }
                BaseConfigurationDataStore.findConfiguration(eContext, 0) == null -> {
                    throw CliError.Companion.CliException("No configuration found")
                }
                else -> {
                }
            }
        }
    }

    fun waitDb(retryTimes: Int, retryInterval: Long, nodeConfigFile: String): CliResult {
        return tryCreateBasicDataSource(nodeConfigFile)?.let { Ok() } ?: if (retryTimes > 0) {
            Thread.sleep(retryInterval)
            waitDb(retryTimes - 1, retryInterval, nodeConfigFile)
        } else CliError.DatabaseOffline()
    }

    private fun tryCreateBasicDataSource(nodeConfigFile: String): Connection? {
        return try {
            val nodeConfig = NodeConfigurationProviderFactory.createProvider(
                    AppConfig.fromPropertiesFile(nodeConfigFile)
            ).getConfiguration()

            BasicDataSource().apply {
                addConnectionProperty("currentSchema", nodeConfig.databaseSchema)
                driverClassName = nodeConfig.databaseDriverclass
                url = "${nodeConfig.databaseUrl}?loggerLevel=OFF"
                username = nodeConfig.databaseUsername
                password = nodeConfig.databasePassword
                defaultAutoCommit = false
            }.connection
        } catch (e: SQLException) {
            null
        } catch (e: ConfigurationException) {
            throw CliError.Companion.CliException("Failed to read configuration")
        }
    }

    private fun getEncodedGtxValueFromFile(blockchainConfigFile: String): ByteArray {
        val gtxValue = GTXMLValueParser.parseGTXMLValue(File(blockchainConfigFile).readText())
        return encodeGTXValue(gtxValue)
    }
}