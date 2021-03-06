// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.api.rest

import mu.KLogging
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.Query
import net.postchain.api.rest.controller.QueryResult
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.ApiTx
import net.postchain.api.rest.model.TxRID
import net.postchain.base.ConfirmationProof
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.ProgrammerMistake
import net.postchain.core.TransactionStatus
import net.postchain.core.UserMistake
import net.postchain.gtx.GTXValue
import org.junit.After
import org.junit.Test

class RestApiMockForClientManual {
    val listenPort = 49545
    val basePath = "/basepath"
    private val blockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"
    lateinit var restApi: RestApi

    companion object : KLogging()

    @After
    fun tearDown() {
        restApi.stop()
        logger.debug { "Stopped" }
    }

    @Test
    fun startMockRestApi() {
        val model = MockModel()
        restApi = RestApi(listenPort, basePath)
        restApi.attachModel(blockchainRID, model)
        logger.info("Ready to serve on port ${restApi.actualPort()}")
        Thread.sleep(600000) // Wait 10 minutes
    }


    class MockModel : Model {
        val statusUnknown = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val statusRejected = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val statusConfirmed = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        val statusNotFound = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        val statusWaiting = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        override fun postTransaction(tx: ApiTx) {
            when (tx.tx) {
                "helloOK".toByteArray().toHex() -> return
                "hello400".toByteArray().toHex() -> throw UserMistake("expected error")
                "hello500".toByteArray().toHex() -> throw ProgrammerMistake("expected error")
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getTransaction(txRID: TxRID): ApiTx? {
            return when (txRID) {
                TxRID(statusUnknown.hexStringToByteArray()) -> null
                TxRID(statusConfirmed.hexStringToByteArray()) -> ApiTx("1234")
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getConfirmationProof(txRID: TxRID): ConfirmationProof? {
            return when (txRID) {
                TxRID(statusUnknown.hexStringToByteArray()) -> null
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getStatus(txRID: TxRID): ApiStatus {
            return when (txRID) {
                TxRID(statusUnknown.hexStringToByteArray()) -> ApiStatus(TransactionStatus.UNKNOWN)
                TxRID(statusWaiting.hexStringToByteArray()) -> ApiStatus(TransactionStatus.WAITING)
                TxRID(statusConfirmed.hexStringToByteArray()) -> ApiStatus(TransactionStatus.CONFIRMED)
                TxRID(statusRejected.hexStringToByteArray()) -> ApiStatus(TransactionStatus.REJECTED)
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun query(query: Query): QueryResult {
            return QueryResult(when (query.json) {
                """{"a":"oknullresponse","c":3}""" -> ""
                """{"a":"okemptyresponse","c":3}""" -> """{}"""
                """{"a":"oksimpleresponse","c":3}""" -> """{"test":"hi"}"""
                """{"a":"usermistake","c":3}""" -> throw UserMistake("expected error")
                """{"a":"programmermistake","c":3}""" -> throw ProgrammerMistake("expected error")
                else -> throw ProgrammerMistake("unexpected error")
            })
        }

        override fun query(query: GTXValue): GTXValue {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
}