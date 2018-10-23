package net.postchain.network.x

import com.nhaarman.mockitokotlin2.*
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.core.ProgrammerMistake
import net.postchain.network.PacketConverter
import org.junit.Before
import org.junit.Test

class DefaultXConnectionManagerTest {

    private val blockchainRid = byteArrayOf(0x01)
    private val connectorFactory = DefaultXConnectorFactory()

    private lateinit var peerInfo1: PeerInfo
    private lateinit var packetConverter1: PacketConverter<Int>

    private lateinit var peerInfo2: PeerInfo
    private lateinit var packetConverter2: PacketConverter<Int>

    @Before
    fun setUp() {
        // TODO: [et]: Make dynamic ports
        peerInfo1 = PeerInfo("localhost", 3331, byteArrayOf(0x01))
        peerInfo2 = PeerInfo("localhost", 3332, byteArrayOf(0x02))

        packetConverter1 = mock()
        packetConverter2 = mock()
    }

    @Test
    fun connectChain_without_autoConnect() {
        // Given
        val communicationConfig: PeerCommConfiguration = mock {
            on { blockchainRID } doReturn blockchainRid
        }
        val chainPeerConfig: XChainPeerConfiguration = mock {
            on { chainID } doReturn 1L
            on { commConfiguration } doReturn communicationConfig
        }

        // When
        DefaultXConnectionManager(connectorFactory, peerInfo1, packetConverter1)
                .connectChain(chainPeerConfig, false)

        // Then
        verify(chainPeerConfig, times(3)).chainID
        verify(chainPeerConfig).commConfiguration
        verify(communicationConfig).blockchainRID
        verify(communicationConfig, never()).peerInfo
    }

    @Test
    fun connectChain_with_autoConnect_without_any_peers() {
        // Given
        val communicationConfig: PeerCommConfiguration = mock {
            on { blockchainRID } doReturn blockchainRid
            on { peerInfo } doReturn arrayOf()
        }
        val chainPeerConfig: XChainPeerConfiguration = mock {
            on { chainID } doReturn 1L
            on { commConfiguration } doReturn communicationConfig
        }

        // When
        DefaultXConnectionManager(connectorFactory, peerInfo1, packetConverter1)
                .connectChain(chainPeerConfig, true)

        // Then
        verify(chainPeerConfig, times(3)).chainID
        verify(chainPeerConfig, times(2)).commConfiguration
        verify(communicationConfig).blockchainRID
        verify(communicationConfig).peerInfo
    }

    @Test
    fun connectChain_with_autoConnect_with_two_peers() {
        // TODO: [et]: Maybe use arg captor here

        // Given
        val connector: XConnector = mock {
            on { connectPeer(any(), any()) }.doAnswer { } // FYI: Instead of `doNothing` or `doReturn Unit`
        }
        val connectorFactory: XConnectorFactory = mock {
            on { createConnector(any(), any(), any()) } doReturn connector
        }
        val communicationConfig: PeerCommConfiguration = mock {
            on { blockchainRID } doReturn blockchainRid
            on { peerInfo } doReturn arrayOf(peerInfo1, peerInfo2)
            on { resolvePeer(peerInfo1.pubKey) } doReturn peerInfo1
            on { resolvePeer(peerInfo2.pubKey) } doReturn peerInfo2
        }
        val chainPeerConfig: XChainPeerConfiguration = mock {
            on { chainID } doReturn 1L
            on { commConfiguration } doReturn communicationConfig
        }

        // When
        DefaultXConnectionManager(connectorFactory, peerInfo1, packetConverter1)
                .connectChain(chainPeerConfig, true)

        // Then
        verify(chainPeerConfig, times(3)).chainID
        verify(chainPeerConfig, times(1 + 1 + 2 * 2)).commConfiguration
        verify(communicationConfig, times(1 + 2 * 1)).blockchainRID
        verify(communicationConfig).peerInfo
    }

    @Test(expected = ProgrammerMistake::class)
    fun connectChainPeer_will_result_in_exception_if_chain_is_not_connected() {
        DefaultXConnectionManager(connectorFactory, peerInfo1, packetConverter1)
                .connectChainPeer(1, peerInfo1.peerId())
    }


    ////////////////////////////////////////////////////////////////////////////////

    @Test(expected = ProgrammerMistake::class)
    fun isPeerConnected_will_result_in_exception_if_chain_is_not_connected() {
        DefaultXConnectionManager(connectorFactory, peerInfo1, packetConverter1)
                .isPeerConnected(1, peerInfo1.peerId())
    }

    @Test(expected = ProgrammerMistake::class)
    fun getConnectedPeers_will_result_in_exception_if_chain_is_not_connected() {
        DefaultXConnectionManager(connectorFactory, peerInfo1, packetConverter1)
                .getConnectedPeers(1)
    }

    @Test(expected = ProgrammerMistake::class)
    fun sendPacket_will_result_in_exception_if_chain_is_not_connected() {
        DefaultXConnectionManager(connectorFactory, peerInfo1, packetConverter1)
                .sendPacket({ byteArrayOf() }, 1, peerInfo2.peerId())
    }

    @Test(expected = ProgrammerMistake::class)
    fun broadcastPacket_will_result_in_exception_if_chain_is_not_connected() {
        DefaultXConnectionManager(connectorFactory, peerInfo1, packetConverter1)
                .broadcastPacket({ byteArrayOf() }, 1)
    }


    @Test
    fun disconnectChain_wont_result_in_exception_if_chain_is_not_connected() {
        DefaultXConnectionManager(connectorFactory, peerInfo1, packetConverter1)
                .disconnectChain(1)
    }

/*@Test
fun two_peers_connection_establishing_then_sending_packets_successfully() {
    val connectorFactory = DefaultXConnectorFactory()

    val connectionManager1: XConnectionManager = DefaultXConnectionManager(connectorFactory, peerInfo1, packetConverter1)
    val connectionManager2: XConnectionManager = DefaultXConnectionManager(connectorFactory, peerInfo2, packetConverter2)

    connectionManager1.connectChain()

}*/

}