// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.api.rest.controller

import mu.KLogging
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_ALLOW_HEADERS
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_ALLOW_METHODS
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_ALLOW_ORIGIN
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_REQUEST_HEADERS
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_REQUEST_METHOD
import net.postchain.api.rest.controller.HttpHelper.Companion.PARAM_BLOCKCHAIN_RID
import net.postchain.api.rest.controller.HttpHelper.Companion.PARAM_HASH_HEX
import net.postchain.api.rest.json.JsonFactory
import net.postchain.api.rest.model.ApiTx
import net.postchain.api.rest.model.TxRID
import net.postchain.common.TimeLog
import net.postchain.common.hexStringToByteArray
import net.postchain.core.UserMistake
import spark.Request
import spark.Service

class RestApi(private val listenPort: Int, private val basePath: String) : Modellable {

    companion object : KLogging()

    private val http = Service.ignite()!!
    private val gson = JsonFactory.makeJson()
    private val models = mutableMapOf<String, Model>()

    init {
        buildRouter(http)
        logger.info { "Rest API listening on port ${actualPort()}" }
        logger.info { "Rest API attached on $basePath/" }
    }

    override fun attachModel(blockchainRID: String, model: Model) {
        models[blockchainRID.toUpperCase()] = model
    }

    override fun detachModel(blockchainRID: String) {
        models.remove(blockchainRID.toUpperCase())
    }

    fun actualPort(): Int {
        return http.port()
    }

    private fun buildRouter(http: Service) {

        http.port(listenPort)

        http.exception(NotFoundError::class.java) { error, _, response ->
            logger.error("NotFoundError:", error)
            response.status(404)
            response.body(error(error))
        }

        http.exception(UserMistake::class.java) { error, _, response ->
            logger.error("UserMistake:", error)
            response.status(400)
            response.body(error(error))
        }

        http.exception(OverloadedException::class.java) { error, _, response ->
            response.status(503) // Service unavailable
            response.body(error(error))
        }

        http.exception(Exception::class.java) { error, _, response ->
            logger.error("Exception:", error)
            response.status(500)
            response.body(error(error))
        }

        http.notFound { _, _ -> error(UserMistake("Not found")) }

        http.before { req, res ->
            res.header(ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            res.header(ACCESS_CONTROL_REQUEST_METHOD, "POST, GET, OPTIONS")
            //res.header("Access-Control-Allow-Headers", "")
            res.type("application/json")

            // This is to provide compatibility with old postchain-client code
            req.pathInfo()
                    .takeIf { it.endsWith("/") }
                    ?.also { res.redirect(it.dropLast(1)) }
        }

        http.path(basePath) {

            http.options("/*") { request, response ->
                request.headers(ACCESS_CONTROL_REQUEST_HEADERS)?.let {
                    response.header(ACCESS_CONTROL_ALLOW_HEADERS, it)
                }
                request.headers(ACCESS_CONTROL_REQUEST_METHOD)?.let {
                    response.header(ACCESS_CONTROL_ALLOW_METHODS, it)
                }

                "OK"
            }

            http.post("/tx/$PARAM_BLOCKCHAIN_RID") { request, _ ->
                val n = TimeLog.startSumConc("RestApi.buildRouter().postTx")
                logger.debug("Request body: ${request.body()}")
                val tx = toTransaction(request)
                if (!tx.tx.matches(Regex("[0-9a-fA-F]{2,}"))) {
                    throw UserMistake("Invalid tx format. Expected {\"tx\": <hexString>}")
                }
                model(request).postTransaction(tx)
                TimeLog.end("RestApi.buildRouter().postTx", n)
                "{}"
            }

            http.get("/tx/$PARAM_BLOCKCHAIN_RID/$PARAM_HASH_HEX", "application/json", { request, _ ->
                runTxActionOnModel(request) { model, txRID ->
                    model.getTransaction(txRID)
                }
            }, gson::toJson)

            http.get("/tx/$PARAM_BLOCKCHAIN_RID/$PARAM_HASH_HEX/confirmationProof", { request, _ ->
                runTxActionOnModel(request) { model, txRID ->
                    model.getConfirmationProof(txRID)
                }
            }, gson::toJson)

            http.get("/tx/$PARAM_BLOCKCHAIN_RID/$PARAM_HASH_HEX/status", { request, _ ->
                runTxActionOnModel(request) { model, txRID ->
                    model.getStatus(txRID)
                }
            }, gson::toJson)

            http.post("/query/$PARAM_BLOCKCHAIN_RID") { request, _ ->
                handleQuery(request)
            }
        }

        http.awaitInitialization()
    }

    private fun toTransaction(req: Request): ApiTx {
        try {
            return gson.fromJson<ApiTx>(req.body(), ApiTx::class.java)
        } catch (e: Exception) {
            throw UserMistake("Could not parse json", e)
        }
    }

    private fun toTxRID(hashHex: String): TxRID {
        val bytes: ByteArray
        try {
            bytes = hashHex.hexStringToByteArray()
        } catch (e: Exception) {
            throw UserMistake("Can't parse hashHex $hashHex", e)
        }

        val txRID: TxRID
        try {
            txRID = TxRID(bytes)
        } catch (e: Exception) {
            throw UserMistake("Bytes $hashHex is not a proper hash", e)
        }

        return txRID
    }

    private fun error(error: Exception): String {
        return gson.toJson(ErrorBody(error.message ?: "Unknown error"))
    }

    private fun handleQuery(request: Request): String {
        logger.debug("Request body: ${request.body()}")
        return model(request)
                .query(Query(request.body()))
                .json
    }

    private fun checkHashHex(request: Request): String {
        val hashHex = request.params(PARAM_HASH_HEX)
        if (hashHex.length != 64 && !hashHex.matches(Regex("[0-9a-f]{64}"))) {
            throw NotFoundError("Invalid hashHex. Expected 64 hex digits [0-9a-f]")
        }
        return hashHex
    }

    private fun checkBlockchainRID(request: Request): String {
        val blockchainRID = request.params(PARAM_BLOCKCHAIN_RID)
        if (blockchainRID.length != 64 && !blockchainRID.matches(Regex("[0-9a-f]{64}"))) {
            throw NotFoundError("Invalid blockchainRID. Expected 64 hex digits [0-9a-f]")
        }
        return blockchainRID
    }

    fun stop() {
        http.stop()
        // Ugly hack to workaround that there is no blocking stop.
        // Test cases won't work correctly without it
        Thread.sleep(100)
    }

    private fun runTxActionOnModel(request: Request, txAction: (Model, TxRID) -> Any?): Any? {
        val hashHex = checkHashHex(request)
        return txAction(model(request), toTxRID(hashHex))
                ?: throw NotFoundError("Can't find tx with hash $hashHex")
    }

    private fun model(request: Request): Model {
        val blockchainRID = checkBlockchainRID(request)
        return models[blockchainRID.toUpperCase()]
                ?: throw NotFoundError("Can't find blockchain with blockchainRID: $blockchainRID")
    }
}
