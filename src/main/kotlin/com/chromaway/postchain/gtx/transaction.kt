package com.chromaway.postchain.gtx

import com.chromaway.postchain.base.BaseBlockQueries
import com.chromaway.postchain.base.CryptoSystem
import com.chromaway.postchain.base.Storage
import com.chromaway.postchain.base.data.BaseBlockchainConfiguration
import com.chromaway.postchain.base.hexStringToByteArray
import com.chromaway.postchain.base.secp256k1_derivePubKey
import com.chromaway.postchain.core.*
import nl.komponents.kovenant.Promise
import org.apache.commons.configuration2.Configuration

class GTXTransaction (val _rawData: ByteArray, module: GTXModule, val cs: CryptoSystem): Transaction {

    val myRID: ByteArray = cs.digest(_rawData)
    val data: GTXData
    val signers: Array<ByteArray>
    val signatures: Array<ByteArray>
    val ops: Array<Transactor>
    var isChecked: Boolean = false
    val digestForSigning: ByteArray

    init {
        data = decodeGTXData(_rawData)

        digestForSigning = data.getDigestForSigning(cs)

        signers = data.signers
        signatures = data.signatures

        ops = data.getExtOpData().map({ module.makeTransactor(it) }).toTypedArray()
    }

    override fun isCorrect(): Boolean {
        if (isChecked) return true

        if (signatures.size != signers.size) return false

        for ((idx, signer) in signers.withIndex()) {
            val signature = signatures[idx]
            if (!cs.verifyDigest(digestForSigning, Signature(signer, signature))) {
                return false
            }
        }

        for (op in ops) {
            if (!op.isCorrect()) return false
        }

        isChecked = true
        return true
    }

    override fun getRawData(): ByteArray {
        return _rawData
    }

    override fun getRID(): ByteArray {
         return myRID
    }

    override fun apply(ctx: TxEContext): Boolean {
        if (!isCorrect()) throw Error("Transaction is not correct")
        for (op in ops) {
            if (!op.apply(ctx))
                throw Error("Operation failed")
        }
        return true
    }

}

class GTXTransactionFactory(val module: GTXModule, val cs: CryptoSystem): TransactionFactory {
    override fun decodeTransaction(data: ByteArray): Transaction {
        return GTXTransaction(data, module, cs)
    }
}

class GTXBlockchainConfiguration(chainID: Long, config: Configuration, val module: GTXModule)
    :BaseBlockchainConfiguration(chainID, config)
{
    val txFactory = GTXTransactionFactory(module, cryptoSystem)

    override fun getTransactionFactory(): TransactionFactory {
        return txFactory
    }

    override fun initializeDB(ctx: EContext) {
        GTXSchemaManager.initializeDB(ctx)
        module.initializeDB(ctx)
    }

    override fun makeBlockQueries(storage: Storage): BlockQueries {
        val blockSigningPrivateKey = config.getString("blocksigningprivkey").hexStringToByteArray()
        val blockSigningPublicKey = secp256k1_derivePubKey(blockSigningPrivateKey)

        return object: BaseBlockQueries(this@GTXBlockchainConfiguration, storage, blockStore,
                chainID.toInt(), blockSigningPublicKey) {
            private val gson = make_gtx_gson()

            override fun query(query: String): Promise<String, Exception> {
                val gtxQuery = gson.fromJson<GTXValue>(query, GTXValue::class.java)
                return runOp {
                    val type = gtxQuery.asDict().get("type")?: throw UserError("Missing query type")
                    val queryResult = module.query(it, type.asString(), gtxQuery)
                    gtxToJSON(queryResult, gson)
                }
            }
        }
    }
}

abstract class GTXBlockchainConfigurationFactory(): BlockchainConfigurationFactory {
    override fun makeBlockchainConfiguration(chainID: Long, config: Configuration): BlockchainConfiguration {
        return GTXBlockchainConfiguration(chainID, config, createGtxModule(config))
    }

    abstract internal fun createGtxModule(config: Configuration): GTXModule
}