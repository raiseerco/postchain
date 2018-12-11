package net.postchain.base.merkle

import net.postchain.gtx.GTXValue
import net.postchain.gtx.IntegerGTXValue


/**
 * We create a dummy serializer so that we can use for tests
 * It truncates an Int to one byte
 */
fun dummySerializatorFun(iGtx: GTXValue): ByteArray {
    when (iGtx) {
        is IntegerGTXValue -> {
            val i: Long = iGtx.integer
            if (i > 127 && i > -1) {
                throw IllegalArgumentException("Test integers should be positive and should not be bigger than 127: $i")
            } else {
                val b: Byte = i.toByte()
                return byteArrayOf(b)
            }
        }
        else -> {
            throw IllegalArgumentException("We don't use any other than Integers for these tests")
        }
    }
}

/**
 * A simple dummy hashing function that's easy to test
 * It only adds 1 to all bytes in the byte array.
 */
fun dummyAddOneHashFun(bArr: ByteArray): Hash {
    val retArr = ByteArray(bArr.size)
    var pos = 0
    for (b: Byte in bArr.asIterable()) {
        retArr[pos] = b.inc()
        pos++
    }
    return retArr
}

/**
 * The "dummy" version is a real calculator, but it uses simplified versions of
 * serializations and hashing
 */
class MerkleHashCalculatorDummy: MerkleHashCalculator() {


    override fun calculateLeafHash(gtxValue: GTXValue): Hash {
        val hash = calculateHashOfGtxInternal(gtxValue, ::dummySerializatorFun, ::dummyAddOneHashFun)
        println("Hex: " + TreeHelper.convertToHex(hash))
        return hash
    }


    override fun calculateNodeHash(hashLeft: Hash, hashRight: Hash): Hash {
        return calculateNodeHashInternal(hashLeft, hashRight, ::dummyAddOneHashFun)
    }


}