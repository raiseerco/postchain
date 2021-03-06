package net.postchain.core

interface Shutdownable {
    fun shutdown()
}

/**
 * Blockchain engine used for building and adding new blocks
 */
interface BlockchainEngine : Shutdownable {
    var isRestartNeeded: Boolean

    fun initializeDB()
    fun addBlock(block: BlockDataWithWitness)
    fun loadUnfinishedBlock(block: BlockData): ManagedBlockBuilder
    fun buildBlock(): ManagedBlockBuilder
    fun getTransactionQueue(): TransactionQueue
    fun getBlockBuildingStrategy(): BlockBuildingStrategy
    fun getBlockQueries(): BlockQueries
    fun getConfiguration(): BlockchainConfiguration
}

interface BlockchainProcess : Shutdownable {
    fun getEngine(): BlockchainEngine
}

interface BlockchainProcessManager : Shutdownable {
    fun startBlockchain(chainId: Long)
    fun retrieveBlockchain(chainId: Long): BlockchainProcess?
    fun stopBlockchain(chainId: Long)
}

typealias RestartHandler = () -> Unit
