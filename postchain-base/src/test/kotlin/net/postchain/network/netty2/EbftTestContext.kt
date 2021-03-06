package net.postchain.network.netty2

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import net.postchain.base.PeerCommConfiguration
import net.postchain.ebft.EbftPacketDecoder
import net.postchain.ebft.EbftPacketEncoder
import net.postchain.ebft.message.EbftMessage
import net.postchain.network.x.XConnectorEvents
import net.postchain.network.x.XPacketHandler

class EbftTestContext(val config: PeerCommConfiguration, val blockchainRid: ByteArray) {

    val packets: XPacketHandler = mock()

    val events: XConnectorEvents = mock {
        on { onPeerConnected(any(), any()) } doReturn packets
    }

    val peer = NettyConnector<EbftMessage>(events)

    fun init() = peer.init(config.myPeerInfo(), EbftPacketDecoder(config))

    fun buildPacketEncoder(): EbftPacketEncoder = EbftPacketEncoder(config, blockchainRid)

    fun buildPacketDecoder(): EbftPacketDecoder = EbftPacketDecoder(config)

    fun encodePacket(message: EbftMessage): ByteArray = buildPacketEncoder().encodePacket(message)

    fun decodePacket(bytes: ByteArray): EbftMessage = buildPacketDecoder().decodePacket(bytes)!!

    fun shutdown() = peer.shutdown()
}