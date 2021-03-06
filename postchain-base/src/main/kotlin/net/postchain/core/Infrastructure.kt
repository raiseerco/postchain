package net.postchain.core

import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider

interface SynchronizationInfrastructure : Shutdownable {
    fun makeBlockchainProcess(engine: BlockchainEngine, restartHandler: RestartHandler): BlockchainProcess
}

interface BlockchainInfrastructure : SynchronizationInfrastructure {
    fun parseConfigurationString(rawData: String, format: String): ByteArray
    fun makeBlockchainConfiguration(rawConfigurationData: ByteArray, context: BlockchainContext): BlockchainConfiguration
    fun makeBlockchainEngine(configuration: BlockchainConfiguration): BlockchainEngine
}

interface ApiInfrastructure : Shutdownable {
    fun connectProcess(process: BlockchainProcess)
    fun disconnectProcess(process: BlockchainProcess)
}

interface InfrastructureFactory {
    fun makeBlockchainInfrastructure(nodeConfigProvider: NodeConfigurationProvider): BlockchainInfrastructure
    fun makeProcessManager(nodeConfigProvider: NodeConfigurationProvider,
                           blockchainConfig: BlockchainConfigurationProvider,
                           blockchainInfrastructure: BlockchainInfrastructure
    ): BlockchainProcessManager
}

enum class Infrastructures(val secondName: String) {
    BaseEbft("base/ebft"),
    BaseTest("base/test")
}