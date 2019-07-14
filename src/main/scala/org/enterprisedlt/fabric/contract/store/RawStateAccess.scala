package org.enterprisedlt.fabric.contract.store

import org.hyperledger.fabric.shim.ledger
import org.hyperledger.fabric.shim.ledger.{CompositeKey, QueryResultsIterator}

/**
 * @author Alexey Polubelov
 */
trait RawStateAccess {

    def getStateByPartialCompositeKey(key: CompositeKey): QueryResultsIterator[ledger.KeyValue]

    def delState(key: String): Unit

    def getState(key: String): Array[Byte]

    def putState(key: String, data: Array[Byte]): Unit

    def queryState(query: String): QueryResultsIterator[ledger.KeyValue]
}
