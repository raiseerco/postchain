package net.postchain.api.rest.json

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.ApiTx
import net.postchain.api.rest.model.GTXQuery
import net.postchain.base.ConfirmationProof

object JsonFactory {

    fun makeJson(): Gson = GsonBuilder()
            .registerTypeAdapter(ConfirmationProof::class.java, ConfirmationProofSerializer())
            .registerTypeAdapter(ApiTx::class.java, TransactionDeserializer())
            .registerTypeAdapter(ApiStatus::class.java, ApiStatusSerializer())
            .registerTypeAdapter(GTXQuery::class.java, GTXQueryDeserializer())
            .create()!!
}