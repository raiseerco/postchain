package net.postchain.network.x

import net.postchain.base.PeerInfo
import net.postchain.network.IdentPacketConverter

class DefaultXConnectorFactory : XConnectorFactory {

    override fun createConnector(
            myPeerInfo: PeerInfo,
            identPacketConverter: IdentPacketConverter,
            eventReceiver: XConnectorEvents): XConnector {

        return DefaultXConnector(
                myPeerInfo,
                identPacketConverter,
                eventReceiver)
    }
}