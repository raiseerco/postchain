package net.postchain.base

import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.RestApi
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.common.toHex
import net.postchain.core.ApiInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.ebft.worker.WorkerBase
import org.apache.commons.configuration2.Configuration

class BaseApiInfrastructure(val config: Configuration) : ApiInfrastructure {

    val restApi: RestApi?

    init {
        val basePath = config.getString("api.basepath", "")
        val port = config.getInt("api.port", 7740)
        val enableSsl = config.getBoolean("api.enable_ssl", false)
        val sslCertificate = config.getString("api.ssl_certificate", "")
        val sslCertificatePassword = config.getString("api.ssl_certificate.password", "")
        restApi = if (port != -1)
            if(enableSsl) RestApi(port, basePath, sslCertificate, sslCertificatePassword)
            else RestApi(port, basePath)
        else null
    }

    override fun connectProcess(process: BlockchainProcess) {
        restApi?.run {
            val engine = process.getEngine()

            val apiModel = PostchainModel(
                    (process as WorkerBase).networkAwareTxQueue,
                    engine.getConfiguration().getTransactionFactory(),
                    engine.getBlockQueries() as BaseBlockQueries) // TODO: [et]: Resolve type cast

            attachModel(blockchainRID(process), apiModel)
        }
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        restApi?.detachModel(blockchainRID(process))
    }

    override fun shutdown() {
        restApi?.stop()
    }

    private fun blockchainRID(process: BlockchainProcess): String {
        return (process.getEngine().getConfiguration() as BaseBlockchainConfiguration) // TODO: [et]: Resolve type cast
                .blockchainRID.toHex()
    }
}