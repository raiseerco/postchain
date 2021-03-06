package net.postchain.config.node

enum class NodeConfigProviders {

    /**
     * PeerInfo collection and other PostchainNode parameters are obtained
     * from app.properties file
     */
    Legacy,

    /**
     * PeerInfo collection and other PostchainNode parameters are obtained
     * from database
     */
    Manual,

    /**
     * PeerInfo collection and other PostchainNode parameters are obtained
     * from system blockchain
     */
    Managed
}