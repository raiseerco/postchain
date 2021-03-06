// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.configurations

import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.core.UserMistake
import net.postchain.gtx.*
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

private val r = QueryRunner()
private val nullableStringReader = ScalarHandler<String?>()

class GTXTestOp(u: Unit, opdata: ExtOpData): GTXOperation(opdata) {
    override fun isCorrect(): Boolean {
        if (data.args.size != 2) return false
        data.args[1].asString()
        return data.args[0].asInteger() == 1L
    }

    override fun apply(ctx: TxEContext): Boolean {
        if (data.args[1].asString() == "rejectMe")
            throw UserMistake("You were asking for it")
        r.update(ctx.conn,
                """INSERT INTO gtx_test_value(tx_iid, value) VALUES (?, ?)""",
                ctx.txIID, data.args[1].asString())
        return true
    }
}

class GTXTestModule: SimpleGTXModule<Unit>(Unit,
        mapOf("gtx_test" to ::GTXTestOp),
        mapOf("gtx_test_get_value" to { u, ctxt, args ->
            val txRID = args.get("txRID")
            if (txRID == null) {
                throw UserMistake("No txRID property supplied")
            }

            val value = r.query(ctxt.conn,
                    """SELECT value FROM gtx_test_value
                    INNER JOIN transactions ON gtx_test_value.tx_iid = transactions.tx_iid
                    WHERE transactions.tx_rid = ?""",
                    nullableStringReader, txRID.asByteArray(true))
            if (value == null)
                GTXNull
            else
                gtx(value)
        })
) {
    override fun initializeDB(ctx: EContext) {
        val moduleName = this::class.qualifiedName!!
        val version = GTXSchemaManager.getModuleVersion(ctx, moduleName)
        if (version == null) {
            r.update(ctx.conn, """
CREATE TABLE gtx_test_value(tx_iid BIGINT PRIMARY KEY, value TEXT NOT NULL)
            """)
            GTXSchemaManager.setModuleVersion(ctx, moduleName, 0)
        }
    }
}