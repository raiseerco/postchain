// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.integrationtest

import junitparams.JUnitParamsRunner
import junitparams.Parameters
import junitparams.naming.TestCaseName
import mu.KLogging
import net.postchain.devtools.IntegrationTest
import net.postchain.devtools.OnDemandBlockBuildingStrategy
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.testinfra.TestTransaction
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JUnitParamsRunner::class)
class FullEbftTestNightly : IntegrationTest() {

    companion object : KLogging()

    private fun strategy(node: PostchainTestNode): OnDemandBlockBuildingStrategy {
        return node
                .getBlockchainInstance()
                .getEngine()
                .getBlockBuildingStrategy() as OnDemandBlockBuildingStrategy
    }

    @Test
    @Parameters(
            "3, 1, 0", "3, 2, 0", "3, 10, 0", "3, 1, 10", "3, 2, 10", "3, 10, 10"
            , "4, 1, 0", "4, 2, 0", "4, 10, 0", "4, 1, 10", "4, 2, 10", "4, 10, 10"
            , "8, 1, 0", "8, 2, 0", "8, 10, 0", "8, 1, 10", "8, 2, 10", "8, 10, 10"
//            , "25, 100, 0"
    )
    @TestCaseName("[{index}] nodesCount: {0}, blocksCount: {1}, txPerBlock: {2}")
    fun runXNodesWithYTxPerBlock(nodesCount: Int, blocksCount: Int, txPerBlock: Int) {
        logger.info {
            "runXNodesWithYTxPerBlock(): " +
                    "nodesCount: $nodesCount, blocksCount: $blocksCount, txPerBlock: $txPerBlock"
        }

        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodesCount))
        createNodes(nodesCount, "/net/postchain/full_ebft/blockchain_config_$nodesCount.xml")

        var txId = 0
        for (i in 0 until blocksCount) {
            for (tx in 0 until txPerBlock) {
                val currentTxId = txId++
                nodes.forEach {
                    it.getBlockchainInstance()
                            .getEngine()
                            .getTransactionQueue()
                            .enqueue(TestTransaction(currentTxId))
                }
            }
            logger.info { "Trigger block" }
            nodes.forEach { strategy(it).buildBlocksUpTo(i.toLong()) }
            logger.info { "Await committed" }
            nodes.forEach { strategy(it).awaitCommitted(i) }
        }

        val queries = nodes[0].getBlockchainInstance().getEngine().getBlockQueries()
        val referenceHeight = queries.getBestHeight().get()
        logger.info { "$blocksCount, refHe: $referenceHeight" }
        nodes.forEach { node ->
            val queries = node.getBlockchainInstance().getEngine().getBlockQueries()
            assertEquals(referenceHeight, queries.getBestHeight().get())

            for (height in 0..referenceHeight) {
                logger.info { "Verifying height $height" }
                val rids = queries.getBlockRids(height).get()
                assertEquals(1, rids.size)

                val txs = queries.getBlockTransactionRids(rids[0]).get()
                assertEquals(txPerBlock, txs.size)

                for (tx in 0 until txPerBlock) {
                    val expectedTx = TestTransaction((height * txPerBlock + tx).toInt())
                    assertArrayEquals(expectedTx.getRID(), txs[tx])

                    val actualTx = queries.getTransaction(txs[tx]).get()
                    assertArrayEquals(expectedTx.getRID(), actualTx?.getRID())
                    assertArrayEquals(expectedTx.getRawData(), actualTx!!.getRawData())
                }
            }
        }
    }
}
