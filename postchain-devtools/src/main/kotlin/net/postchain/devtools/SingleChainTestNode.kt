package net.postchain.devtools

import net.postchain.PostchainNode
import net.postchain.StorageBuilder
import net.postchain.api.rest.controller.Model
import net.postchain.base.*
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.base.data.SQLDatabaseAccess
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.BlockchainProcess
import net.postchain.core.NODE_ID_TODO
import net.postchain.ebft.BlockchainInstanceModel
import net.postchain.gtx.encodeGTXValue
import org.apache.commons.configuration2.Configuration

class SingleChainTestNode(nodeConfig: Configuration) : PostchainNode(nodeConfig) {

    private val chainId: Long
    private val blockchainRID: ByteArray

    init {
        val storage = StorageBuilder.buildStorage(nodeConfig, NODE_ID_TODO, true)
        chainId = nodeConfig.getLong("activechainids")
        blockchainRID = nodeConfig
                .getString("test.blockchain.$chainId.blockchainrid")
                .hexStringToByteArray()

        // TODO: [et]: Is it necessary here after StorageBuilder.buildStorage() redesign?
        withWriteConnection(storage, chainId) { eContext ->
            with(SQLDatabaseAccess()) {
                initialize(eContext.conn, expectedDbVersion = 1)
                checkBlockchainRID(eContext, blockchainRID)
            }
            true
        }

        val configData = BaseBlockchainConfigurationData.readFromCommonsConfiguration(
                nodeConfig, chainId, blockchainRID, NODE_ID_TODO)
        val factoryClass = Class.forName(configData.data["configurationfactory"]!!.asString())
        val factory = (factoryClass.newInstance() as BlockchainConfigurationFactory)

        val blockchainConfiguration = factory.makeBlockchainConfiguration(configData)
        val configAsByteArray = encodeGTXValue(
                (blockchainConfiguration as BaseBlockchainConfiguration).configData.data)

        withWriteConnection(storage, chainId) { eContext ->
            BaseConfigurationDataStore.addConfigurationData(eContext, 0, configAsByteArray)
            true
        }
    }

    fun startBlockchain() {
        startBlockchain(chainId)
    }

    fun getRestApiModel(): Model {
        val blockchainProcess = processManager.retrieveBlockchain(chainId)!!
        return ((blockchainInfrastructure as BaseBlockchainInfrastructure).apiInfrastructure as BaseApiInfrastructure)
                .restApi?.retrieveModel(blockchainRID(blockchainProcess))!!
    }

    fun getRestApiHttpPort(): Int {
        return ((blockchainInfrastructure as BaseBlockchainInfrastructure).apiInfrastructure as BaseApiInfrastructure)
                .restApi?.actualPort() ?: 0
    }

    fun getBlockchainInstance(): BlockchainInstanceModel {
        return processManager.retrieveBlockchain(chainId) as BlockchainInstanceModel
    }

    private fun blockchainRID(process: BlockchainProcess): String {
        return (process.getEngine().getConfiguration() as BaseBlockchainConfiguration) // TODO: [et]: Resolve type cast
                .blockchainRID.toHex()
    }
}