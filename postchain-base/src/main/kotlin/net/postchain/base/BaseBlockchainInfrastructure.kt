package net.postchain.base

import net.postchain.StorageBuilder
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.gtx.decodeGTXValue

class BaseBlockchainInfrastructure(
        private val nodeConfigProvider: NodeConfigurationProvider,
        val synchronizationInfrastructure: SynchronizationInfrastructure,
        val apiInfrastructure: ApiInfrastructure
) : BlockchainInfrastructure {

    val cryptoSystem = SECP256K1CryptoSystem()
    val blockSigner: Signer
    val subjectID: ByteArray

    init {
        val privKey = nodeConfigProvider.getConfiguration().privKeyByteArray
        val pubKey = secp256k1_derivePubKey(privKey)
        blockSigner = cryptoSystem.makeSigner(pubKey, privKey)
        subjectID = pubKey
    }

    override fun shutdown() {
        synchronizationInfrastructure.shutdown()
        apiInfrastructure.shutdown()
    }

    override fun parseConfigurationString(rawData: String, format: String): ByteArray {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun makeBlockchainConfiguration(rawConfigurationData: ByteArray, context: BlockchainContext): BlockchainConfiguration {
        val actualContext = if (context.nodeRID == null) {
            BaseBlockchainContext(context.blockchainRID, context.nodeID, context.chainID, subjectID)
        } else {
            context
        }

        val gtxData = decodeGTXValue(rawConfigurationData)
        val confData = BaseBlockchainConfigurationData(gtxData, actualContext, blockSigner)

        val bcfClass = Class.forName(confData.data["configurationfactory"]!!.asString())
        val factory = (bcfClass.newInstance() as BlockchainConfigurationFactory)

        return factory.makeBlockchainConfiguration(confData)
    }

    override fun makeBlockchainEngine(configuration: BlockchainConfiguration): BaseBlockchainEngine {
        val storage = StorageBuilder.buildStorage(nodeConfigProvider.getConfiguration(), -1) // TODO: nodeID
        // TODO: [et]: Maybe extract 'queuecapacity' param from ''
        val tq = BaseTransactionQueue(
                (configuration as BaseBlockchainConfiguration)
                        .configData.getBlockBuildingStrategy()?.get("queuecapacity")?.asInteger()?.toInt() ?: 2500)
        return BaseBlockchainEngine(configuration, storage, configuration.chainID, tq)
                .apply { initializeDB() }
    }

    override fun makeBlockchainProcess(engine: BlockchainEngine, restartHandler: RestartHandler): BlockchainProcess {
        return synchronizationInfrastructure.makeBlockchainProcess(engine, restartHandler)
                .also(apiInfrastructure::connectProcess)
    }
}
