package net.postchain.gtx.gtxml

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.common.hexStringToByteArray
import net.postchain.gtx.*
import org.junit.Test

class GTXMLTransactionEncodeTest {

    @Test
    fun encodeXMLGTXTransaction_successfully() {
        val gtxData = GTXData(
                "23213213".hexStringToByteArray(),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24)
                ),
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55)
                ),
                arrayOf(
                        OpData("ft_transfer",
                                arrayOf(
                                        StringGTXValue("hello"),
                                        StringGTXValue("hello2"),
                                        StringGTXValue("hello3"),
                                        IntegerGTXValue(42),
                                        IntegerGTXValue(43))),
                        OpData("ft_transfer",
                                arrayOf(
                                        StringGTXValue("HELLO"),
                                        StringGTXValue("HELLO2"),
                                        IntegerGTXValue(142),
                                        IntegerGTXValue(143)))
                )
        )

        val expected = javaClass.getResource("/net/postchain/gtx/gtxml/encode/tx_full.xml").readText()
                .replace("\r\n", "\n").trim()
        val actual = GTXMLTransactionEncoder.encodeXMLGTXTransaction(gtxData)

        assert(actual.trim()).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGTXTransaction_empty_successfully() {
        val gtxData = GTXData(
                "23213213".hexStringToByteArray(),
                arrayOf(),
                arrayOf(),
                arrayOf()
        )

        val expected = javaClass.getResource("/net/postchain/gtx/gtxml/encode/tx_empty.xml").readText()
                .replace("\r\n", "\n").trim()
        val actual = GTXMLTransactionEncoder.encodeXMLGTXTransaction(gtxData)

        assert(actual.trim()).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGTXTransaction_with_empty_signers_and_signatures_successfully() {
        val gtxData = GTXData(
                "23213213".hexStringToByteArray(),
                arrayOf(),
                arrayOf(),
                arrayOf(
                        OpData("ft_transfer",
                                arrayOf(
                                        StringGTXValue("hello"),
                                        StringGTXValue("hello2"),
                                        StringGTXValue("hello3"),
                                        IntegerGTXValue(42),
                                        IntegerGTXValue(43))),
                        OpData("ft_transfer",
                                arrayOf(
                                        StringGTXValue("HELLO"),
                                        StringGTXValue("HELLO2"),
                                        IntegerGTXValue(142),
                                        IntegerGTXValue(143)))
                )
        )

        val expected = javaClass.getResource("/net/postchain/gtx/gtxml/encode/tx_empty_signers_and_signatures.xml").readText()
                .replace("\r\n", "\n").trim()
        val actual = GTXMLTransactionEncoder.encodeXMLGTXTransaction(gtxData)

        assert(actual.trim()).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGTXTransaction_with_empty_operations_successfully() {
        val gtxData = GTXData(
                "23213213".hexStringToByteArray(),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24)
                ),
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55)
                ),
                arrayOf()
        )

        val expected = javaClass.getResource("/net/postchain/gtx/gtxml/encode/tx_empty_operations.xml").readText()
                .replace("\r\n", "\n").trim()
        val actual = GTXMLTransactionEncoder.encodeXMLGTXTransaction(gtxData)

        assert(actual.trim()).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGTXTransaction_with_empty_operation_parameters_successfully() {
        val gtxData = GTXData(
                "23213213".hexStringToByteArray(),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24)
                ),
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55)
                ),
                arrayOf(
                        OpData("ft_transfer", arrayOf()),
                        OpData("ft_transfer", arrayOf())
                )
        )

        val expected = javaClass.getResource("/net/postchain/gtx/gtxml/encode/tx_empty_operation_parameters.xml").readText()
                .replace("\r\n", "\n").trim()
        val actual = GTXMLTransactionEncoder.encodeXMLGTXTransaction(gtxData)

        assert(actual.trim()).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGTXTransaction_compound_parameter_of_operation_successfully() {
        val gtxData = GTXData(
                "23213213".hexStringToByteArray(),
                arrayOf(),
                arrayOf(),
                arrayOf(
                        OpData("ft_transfer",
                                arrayOf(
                                        StringGTXValue("foo"),
                                        ArrayGTXValue(arrayOf(
                                                StringGTXValue("foo"),
                                                ArrayGTXValue(arrayOf(
                                                        StringGTXValue("foo"),
                                                        StringGTXValue("bar")
                                                )),
                                                DictGTXValue(mapOf(
                                                        "key1" to IntegerGTXValue(42),
                                                        "key2" to StringGTXValue("42"),
                                                        "key3" to ArrayGTXValue(arrayOf(
                                                                StringGTXValue("hello"),
                                                                IntegerGTXValue(42)
                                                        ))
                                                ))
                                        )),
                                        DictGTXValue(mapOf(
                                                "key1" to GTXNull,
                                                "key2" to StringGTXValue("42")
                                        ))
                                ))
                ))

        val expected = javaClass.getResource("/net/postchain/gtx/gtxml/encode/tx_compound_parameter_of_operation.xml").readText()
                .replace("\r\n", "\n").trim()
        val actual = GTXMLTransactionEncoder.encodeXMLGTXTransaction(gtxData)

        assert(actual.trim()).isEqualTo(expected)
    }
}