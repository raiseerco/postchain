package net.postchain.gtv.merkle

import net.postchain.base.merkle.BinaryTree
import net.postchain.base.merkle.BinaryTreeElement
import net.postchain.gtv.merkle.GtvMerkleBasics.HASH_PREFIX_NODE_GTV_ARRAY
import net.postchain.gtv.merkle.GtvMerkleBasics.HASH_PREFIX_NODE_GTV_DICT
import net.postchain.base.merkle.SubTreeRootNode
import net.postchain.gtv.*

/**
 * In this file we handle the most common case, where the binary tree holds only [Gtv] s.
 *
 * When we calculate the merkle root hash, we need get different hashes if 2 trees have same leaf elements,
 * but different internal structure. Therefore we use special [GtvArrayHeadNode] and [GtvDictHeadNode] to
 * signal this difference.
 */

/**
 * Represents the top of a sub tree generated by a [GtvArray]
 *
 * @param size is how many element we have in the original array.
 */
class GtvArrayHeadNode(left: BinaryTreeElement, right: BinaryTreeElement, isProofLeaf: Boolean, content: Gtv, val size: Int, val sizeInBytes: Int):
        SubTreeRootNode<Gtv>(left, right, isProofLeaf, content) {

    init {
        if (content !is GtvArray) {
            throw IllegalStateException("How come we use this array type when the type is not an GtvArray?")
        }
    }

    override fun getPrefixByte(): Byte = HASH_PREFIX_NODE_GTV_ARRAY

    override fun getNrOfBytes(): Int = sizeInBytes
}

/**
 * Represents the top a sub tree generated by a [GtvDictionary]
 *
 * @param size is how many key-pairs we have in the original dict.
 */
class GtvDictHeadNode(left: BinaryTreeElement, right: BinaryTreeElement, isProofLeaf: Boolean, content: Gtv, val size: Int, val sizeInBytes: Int):
        SubTreeRootNode<Gtv>(left, right, isProofLeaf, content){

    init {
        if (content !is GtvDictionary) {
            throw IllegalStateException("How come we use this dict type when the type is not an GtvDictionary?")
        }
    }

    override fun getPrefixByte(): Byte = HASH_PREFIX_NODE_GTV_DICT

    override fun getNrOfBytes(): Int = sizeInBytes
}

/**
 * Represents a [BinaryTree] that only holds Gtv values.
 */
class GtvBinaryTree(root: BinaryTreeElement) : BinaryTree<Gtv>(root)
