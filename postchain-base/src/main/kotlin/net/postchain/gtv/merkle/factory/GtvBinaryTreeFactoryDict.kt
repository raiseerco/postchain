package net.postchain.gtv.merkle.factory

import net.postchain.base.merkle.BinaryTreeElement
import net.postchain.base.merkle.EmptyLeaf
import net.postchain.base.merkle.Node
import net.postchain.gtv.*
import net.postchain.gtv.merkle.GtvBinaryTreeFactory
import net.postchain.gtv.merkle.GtvDictHeadNode
import java.util.SortedSet

object GtvBinaryTreeFactoryDict {


    private val mainFactory = GtvBinaryTreeFactory()

    /**
     * The strategy for transforming [GtvDictionary] is pretty simple, we treat the key and value as leafs and
     * add them both as elements to the tree.
     *
     * There is an edge cases here:
     * - When the dict is empty. -> We return a top node with two empty leafs
     */
    fun buildFromGtvDictionary(GtvDictionary: GtvDictionary, GtvPaths: GtvPathSet): GtvDictHeadNode {
        val isThisAProofLeaf = GtvPaths.isThisAProofLeaf() // Will tell us if any of the paths points to this element
        //println("Dict,(is proof? $isThisAProofLeaf) Proof path (size: ${GtvPathList.size} ) list: " + GtvPath.debugRerpresentation(GtvPathList))
        val keys: SortedSet<String> = GtvDictionary.dict.keys.toSortedSet() // Needs to be sorted, or else the order is undefined

        if (keys.isEmpty()) {
            return GtvDictHeadNode(EmptyLeaf, EmptyLeaf, isThisAProofLeaf, GtvDictionary, keys.size, 0)
        }

        // 1. Build first (leaf) layer
        val leafArray = buildLeafElementFromDict(keys, GtvDictionary, GtvPaths)
        val sumNrOfBytes = leafArray.sumBy { it.getNrOfBytes() }

        // 2. Build all higher layers
        val result = mainFactory.buildHigherLayer(1, leafArray)

        // 3. Fix and return the root node
        val orgRoot = result.get(0)
        return when (orgRoot) {
            is Node -> GtvDictHeadNode(orgRoot.left, orgRoot.right, isThisAProofLeaf, GtvDictionary, keys.size, sumNrOfBytes)
            else -> throw IllegalStateException("Should not find element of this type here: $orgRoot")
        }
    }


    /**
     * Converts the key-value pairs of a [GtvDictionary] into [BinaryTreeElement] s.
     * The only tricky bit of this method is that we need to remove paths that are irrelevant for the pair in question.
     *
     * @param leafList the list of [Gtv] we will use for leafs in the tree
     * @param GtvPaths the paths we have to consider while creating the leafs
     * @return an array of all the leafs as [BinaryTreeElement] s. Note that some leafs might not be primitive values
     *   but some sort of collection with their own leafs (recursivly)
     */
    private fun buildLeafElementFromDict(keys: SortedSet<String>, GtvDictionary: GtvDictionary, GtvPaths: GtvPathSet): ArrayList<BinaryTreeElement> {
        val leafArray = arrayListOf<BinaryTreeElement>()

        val onlyDictPaths = GtvPaths.keepOnlyDictPaths() // For performance, since we will loop soon

        for (key in keys) {

            //println("key extracted: $key")

            // 1.a Fix the key
            val keyGtvString: Gtv = GtvString(key)
            val keyElement = mainFactory.handlePrimitiveLeaf(keyGtvString, GtvPath.NO_PATHS) // The key cannot not be proved, so NO_PATHS
            leafArray.add(keyElement)

            // 1.b Fix the value/content
            val pathsRelevantForThisLeaf = onlyDictPaths.getTailIfFirstElementIsDictOfThisKeyFromList(key)
            val content: Gtv = GtvDictionary.get(key)!!  // TODO: Is it ok to bang here if the dict is broken?
            val contentElement = mainFactory.handleLeaf(content, pathsRelevantForThisLeaf)
            leafArray.add(contentElement)
        }
        return leafArray
    }
}