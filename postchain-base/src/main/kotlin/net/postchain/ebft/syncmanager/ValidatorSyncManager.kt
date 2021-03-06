// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.ebft.syncmanager

import mu.KLogging
import net.postchain.common.toHex
import net.postchain.core.*
import net.postchain.ebft.*
import net.postchain.ebft.message.*
import net.postchain.ebft.message.Transaction
import net.postchain.network.CommunicationManager
import net.postchain.network.x.XPeerID
import java.util.*

fun decodeBlockDataWithWitness(block: CompleteBlock, bc: BlockchainConfiguration)
        : BlockDataWithWitness {
    val header = bc.decodeBlockHeader(block.header)
    val witness = bc.decodeWitness(block.witness)
    return BlockDataWithWitness(header, block.transactions, witness)
}

fun decodeBlockData(block: UnfinishedBlock, bc: BlockchainConfiguration)
        : BlockData {
    val header = bc.decodeBlockHeader(block.header)
    return BlockData(header, block.transactions)
}

private class StatusSender(
        private val maxStatusInterval: Int,
        private val statusManager: StatusManager,
        private val communicationManager: CommunicationManager<EbftMessage>
) {
    var lastSerial: Long = -1
    var lastSentTime: Long = Date(0L).time

    // Sends a status message to all peers when my status has changed or
    // after a timeout period.
    fun update() {
        val myStatus = statusManager.myStatus
        val isNewState = myStatus.serial > this.lastSerial
        val timeoutExpired = System.currentTimeMillis() - this.lastSentTime > this.maxStatusInterval
        if (isNewState || timeoutExpired) {
            this.lastSentTime = Date().time
            this.lastSerial = myStatus.serial
            val statusMessage = Status(myStatus.blockRID, myStatus.height, myStatus.revolting,
                    myStatus.round, myStatus.serial, myStatus.state.ordinal)
            communicationManager.broadcastPacket(statusMessage)
        }
    }
}

const val StatusLogInterval = 10000L

/**
 * The ValidatorSyncManager handles communications with our peers.
 */
class ValidatorSyncManager(
        private val signers: List<ByteArray>,
        private val statusManager: StatusManager,
        private val blockManager: BlockManager,
        private val blockDatabase: BlockDatabase,
        private val communicationManager: CommunicationManager<EbftMessage>,
        private val txQueue: TransactionQueue,
        val blockchainConfiguration: BlockchainConfiguration
) : SyncManagerBase {
    private val revoltTracker = RevoltTracker(10000, statusManager)
    private val statusSender = StatusSender(1000, statusManager, communicationManager)
    private val defaultTimeout = 1000
    private var currentTimeout = defaultTimeout
    private var processingIntent: BlockIntent = DoNothingIntent
    private var processingIntentDeadline = 0L
    private var lastStatusLogged = Date().time

    companion object : KLogging()

    private val nodes = communicationManager.peers().map { XPeerID(it.pubKey) }

    private fun getPeerIndex(peerID: XPeerID): Int {
        return nodes.indexOf(peerID)
    }

    // get n-th validator node XPeerID
    private fun validatorAtIndex(n: Int): XPeerID {
        return XPeerID(signers[n])
    }

    /**
     * Handle incoming messages
     */
    fun dispatchMessages() {
        for (packet in communicationManager.getPackets()) {
            val xPeerId = packet.first
            val nodeIndex = getPeerIndex(xPeerId)
            val message = packet.second
            logger.debug { "Received message type ${message.javaClass.simpleName}/${message.getBackingInstance().choiceID} from node $nodeIndex" }
            try {
                when (message) {
                    // same case for replica and validator node
                    is GetBlockAtHeight -> sendBlockAtHeight(xPeerId, message.height)
                    else -> {
                        if (nodeIndex != NODE_ID_READ_ONLY) {
                            // validator consensus logic
                            when (message) {
                                is Status -> {
                                    val nodeStatus = NodeStatus(message.height, message.serial)
                                    nodeStatus.blockRID = message.blockRId
                                    nodeStatus.revolting = message.revolting
                                    nodeStatus.round = message.round
                                    nodeStatus.state = NodeState.values()[message.state]
                                    statusManager.onStatusUpdate(nodeIndex, nodeStatus)
                                }
                                is BlockSignature -> {
                                    val signature = Signature(message.signature.subjectID, message.signature.data)
                                    val smBlockRID = this.statusManager.myStatus.blockRID
                                    if (smBlockRID == null) {
                                        logger.info("Received signature not needed")
                                    } else if (!smBlockRID.contentEquals(message.blockRID)) {
                                        logger.info("Receive signature for a different block")
                                    } else if (this.blockDatabase.verifyBlockSignature(signature)) {
                                        this.statusManager.onCommitSignature(nodeIndex, message.blockRID, signature)
                                    }
                                }
                                is CompleteBlock -> {
                                    blockManager.onReceivedBlockAtHeight(
                                            decodeBlockDataWithWitness(message, blockchainConfiguration),
                                            message.height
                                    )
                                }
                                is UnfinishedBlock -> {
                                    blockManager.onReceivedUnfinishedBlock(decodeBlockData(message, blockchainConfiguration))
                                }
                                is GetUnfinishedBlock -> sendUnfinishedBlock(nodeIndex)
                                is GetBlockSignature -> sendBlockSignature(nodeIndex, message.blockRID)
                                is Transaction -> handleTransaction(nodeIndex, message)
                                else -> throw ProgrammerMistake("Unhandled type ${message::class}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Couldn't handle message ${message}. Ignoring and continuing", e)
            }
        }
    }

    /**
     * Handle transaction received from peer
     *
     * @param index
     * @param message message including the transaction
     */
    private fun handleTransaction(index: Int, message: Transaction) {
        val tx = blockchainConfiguration.getTransactionFactory().decodeTransaction(message.data)
        if (!tx.isCorrect()) {
            throw UserMistake("Transaction ${tx.getRID()} is not correct")
        }
        txQueue.enqueue(tx)
    }

    /**
     * Send message to peer with our commit signature
     *
     * @param nodeIndex node index of receiving peer
     * @param blockRID block identifier
     */
    private fun sendBlockSignature(nodeIndex: Int, blockRID: ByteArray) {
        val currentBlock = this.blockManager.currentBlock
        if (currentBlock != null && currentBlock.header.blockRID.contentEquals(blockRID)) {
            if(!statusManager.myStatus.blockRID!!.contentEquals(currentBlock.header.blockRID)) {
                throw ProgrammerMistake("status manager block RID (${statusManager.myStatus.blockRID!!.toHex()}) out of sync with current block RID (${currentBlock.header.blockRID.toHex()})")
            }
            val signature = statusManager.getCommitSignature()
            if (signature != null) {
                communicationManager.sendPacket(BlockSignature(blockRID, signature), validatorAtIndex(nodeIndex))
            }
            return
        }
        val blockSignature = blockDatabase.getBlockSignature(blockRID)
        blockSignature success {
            val packet = BlockSignature(blockRID, it)
            communicationManager.sendPacket(packet, validatorAtIndex(nodeIndex))
        } fail {
            logger.debug("Error sending BlockSignature", it)
        }
    }

    /**
     * Send message to node including the block at [height]. This is a response to the [fetchBlockAtHeight] request.
     *
     * @param xPeerId XPeerID of receiving node
     * @param height requested block height
     */
    private fun sendBlockAtHeight(xPeerId: XPeerID, height: Long) {
        val blockAtHeight = blockDatabase.getBlockAtHeight(height)
        blockAtHeight success {
            val packet = CompleteBlock(it.header.rawData, it.transactions.toList(),
                    height, it.witness!!.getRawData())
            communicationManager.sendPacket(packet, xPeerId)
        } fail { logger.debug("Error sending CompleteBlock", it) }
    }

    /**
     * Send message to node with the current unfinished block.
     *
     * @param nodeIndex index of node to send block to
     */
    private fun sendUnfinishedBlock(nodeIndex: Int) {
        val currentBlock = blockManager.currentBlock
        if (currentBlock != null) {
            communicationManager.sendPacket(UnfinishedBlock(currentBlock.header.rawData, currentBlock.transactions.toList()),
                    validatorAtIndex(nodeIndex))
        }
    }

    /**
     * Pick a random node from all nodes matching certain conditions
     *
     * @param match function that checks whether a node matches our selection conditions
     * @return index of selected node
     */
    private fun selectRandomNode(match: (NodeStatus) -> Boolean): Int? {
        val matchingIndexes = mutableListOf<Int>()
        statusManager.nodeStatuses.forEachIndexed({ index, status ->
            if (match(status)) matchingIndexes.add(index)
        })
        if (matchingIndexes.isEmpty()) return null
        if (matchingIndexes.size == 1) return matchingIndexes[0]
        return matchingIndexes[Math.floor(Math.random() * matchingIndexes.size).toInt()]
    }

    /**
     * Send message to random peer to retrieve the block at [height]
     *
     * @param height the height at which we want the block
     */
    private fun fetchBlockAtHeight(height: Long) {
        val nodeIndex = selectRandomNode { it.height > height } ?: return
        logger.debug("Fetching block at height $height from node $nodeIndex")
        communicationManager.sendPacket(GetBlockAtHeight(height), validatorAtIndex(nodeIndex))
    }

    /**
     * Send message to fetch commit signatures from [nodes]
     *
     * @param blockRID identifier of the block to fetch signatures for
     * @param nodes list of nodes we want commit signatures from
     */
    private fun fetchCommitSignatures(blockRID: ByteArray, nodes: Array<Int>) {
        val message = GetBlockSignature(blockRID)
        logger.debug("Fetching commit signature for block with RID ${blockRID.toHex()} from nodes ${Arrays.toString(nodes)}")
        nodes.forEach {
            communicationManager.sendPacket(message, validatorAtIndex(it))
        }
    }

    /**
     * Send message to random peer for fetching latest unfinished block at the same height as us
     *
     * @param blockRID identifier of the unfinished block
     */
    private fun fetchUnfinishedBlock(blockRID: ByteArray) {
        val height = statusManager.myStatus.height
        val nodeIndex = selectRandomNode {
            it.height == height && (it.blockRID?.contentEquals(blockRID) ?: false)
        } ?: return
        logger.debug("Fetching unfinished block with RID ${blockRID.toHex()} from node $nodeIndex ")
        communicationManager.sendPacket(GetUnfinishedBlock(blockRID), validatorAtIndex(nodeIndex))
    }

    /**
     * Process our intent latest intent
     */
    fun processIntent() {
        val intent = blockManager.getBlockIntent()
        if (intent == processingIntent) {
            if (intent is DoNothingIntent) return
            if (Date().time > processingIntentDeadline) {
                this.currentTimeout = (this.currentTimeout.toDouble() * 1.1).toInt() // exponential back-off
            } else {
                return
            }
        } else {
            currentTimeout = defaultTimeout
        }
        when (intent) {
            DoNothingIntent -> Unit
            is FetchBlockAtHeightIntent -> fetchBlockAtHeight(intent.height)
            is FetchCommitSignatureIntent -> fetchCommitSignatures(intent.blockRID, intent.nodes)
            is FetchUnfinishedBlockIntent -> fetchUnfinishedBlock(intent.blockRID)
            else -> throw ProgrammerMistake("Unrecognized intent: ${intent::class}")
        }
        processingIntent = intent
        processingIntentDeadline = Date().time + currentTimeout
    }

    /**
     * Log status of all nodes including their latest block RID and if they have the signature or not
     */
    fun logStatus() {
        for ((idx, ns) in statusManager.nodeStatuses.withIndex()) {
            val blockRID = ns.blockRID
            val haveSignature = statusManager.commitSignatures[idx] != null
            logger.info {
                "Node ${idx} he:${ns.height} ro:${ns.round} st:${ns.state}" +
                        " ${if (ns.revolting) "R" else ""} blockRID=${if (blockRID == null) "null" else blockRID.toHex()}" +
                        " havesig:${haveSignature}"
            }
        }
    }

    /**
     * Process peer messages, how we should proceed with the current block, updating the revolt tracker and
     * notify peers of our current status.
     */
    override fun update() {
        // Process all messages from peers, one at a time. Some
        // messages may trigger asynchronous code which will
        // send replies at a later time, others will send replies
        // immediately
        dispatchMessages()

        // An intent is something that we want to do with our current block.
        // The current intent is fetched from the BlockManager and will result in
        // some messages being sent to peers requesting data like signatures or
        // complete blocks
        processIntent()

        // RevoltTracker will check trigger a revolt if conditions for revolting are met
        // A revolt will be triggerd by calling statusManager.onStartRevolting()
        // Typical revolt conditions
        //    * A timeout happens and round has not increased. Round is increased then 2f+1 nodes
        //      are revolting.
        revoltTracker.update()

        // Sends a status message to all peers when my status has changed or after a timeout
        statusSender.update()

        if (Date().time - lastStatusLogged >= StatusLogInterval) {
            logStatus()
            lastStatusLogged = Date().time
        }
    }
}