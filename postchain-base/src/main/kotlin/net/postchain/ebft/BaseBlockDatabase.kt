// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.ebft

import mu.KLogging
import net.postchain.common.toHex
import net.postchain.core.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A wrapper class for the [engine] and [blockQueries], starting new threads when running
 */
class BaseBlockDatabase(
        private val engine: BlockchainEngine,
        private val blockQueries: BlockQueries,
        val nodeIndex: Int
) : BlockDatabase {

    private val executor = Executors.newSingleThreadExecutor {
        Thread(it, "$nodeIndex-BaseBlockDatabaseWorker")
                .apply {
                    isDaemon = true // So it can't block the JVM from exiting if still running
                }
    }
    private var blockBuilder: ManagedBlockBuilder? = null
    private var witnessBuilder: MultiSigBlockWitnessBuilder? = null

    companion object : KLogging()

    fun stop() {
        logger.debug("BaseBlockDatabase $nodeIndex stopping")
        executor.shutdownNow()
        executor.awaitTermination(1000, TimeUnit.MILLISECONDS) // TODO: [et]: 1000 ms
        maybeRollback()
    }

    private fun <RT> runOp(name: String, op: () -> RT): Promise<RT, Exception> {
        logger.trace("BaseBlockDatabase $nodeIndex putting a job")

        val deferred = deferred<RT, Exception>()
        executor.execute {
            try {
                logger.debug("Starting job $name")
                val res = op()
                logger.debug("Finish job $name")
                deferred.resolve(res)
            } catch (e: Exception) {
                logger.debug("Failed job $name", e)
                deferred.reject(e)
            }
        }

        return deferred.promise
    }

    private fun maybeRollback() {
        logger.trace("BaseBlockDatabase $nodeIndex maybeRollback.")
        if (blockBuilder != null) {
            logger.debug("BaseBlockDatabase $nodeIndex blockBuilder is not null.")
        }
        blockBuilder?.rollback()
        blockBuilder = null
        witnessBuilder = null
    }

    override fun addBlock(block: BlockDataWithWitness): Promise<Unit, Exception> {
        return runOp("addBlock ${block.header.blockRID.toHex()}") {
            maybeRollback()
            engine.addBlock(block)
        }
    }


    override fun loadUnfinishedBlock(block: BlockData): Promise<Signature, Exception> {
        return runOp("loadUnfinishedBlock ${block.header.blockRID.toHex()}") {
            maybeRollback()
            blockBuilder = engine.loadUnfinishedBlock(block)
            witnessBuilder = blockBuilder!!.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
            witnessBuilder!!.getMySignature()
        }
    }

    override fun commitBlock(signatures: Array<Signature?>): Promise<Unit, Exception> {
        return runOp("commitBlock") {
            // TODO: process signatures
            blockBuilder!!.commit(witnessBuilder!!.getWitness())
            blockBuilder = null
            witnessBuilder = null
        }
    }

    override fun buildBlock(): Promise<Pair<BlockData, Signature>, Exception> {
        return runOp("buildBlock") {
            maybeRollback()
            blockBuilder = engine.buildBlock()
            witnessBuilder = blockBuilder!!.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
            Pair(blockBuilder!!.getBlockData(), witnessBuilder!!.getMySignature())
        }
    }

    override fun verifyBlockSignature(s: Signature): Boolean {
        if (witnessBuilder == null) {
            return false
        }
        return try {
            witnessBuilder!!.applySignature(s)
            true
        } catch (e: Exception) {
            logger.debug("Signature invalid", e)
            false
        }
    }

    override fun getBlockSignature(blockRID: ByteArray): Promise<Signature, Exception> {
        return blockQueries.getBlockSignature(blockRID)
    }

    override fun getBlockAtHeight(height: Long): Promise<BlockDataWithWitness, Exception> {
        return blockQueries.getBlockAtHeight(height)
    }

}