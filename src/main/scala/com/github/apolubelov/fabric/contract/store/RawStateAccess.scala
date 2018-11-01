package com.github.apolubelov.fabric.contract.store

import org.hyperledger.fabric.shim.ledger.{CompositeKey, KeyValue, QueryResultsIterator}

/*
 * @author Alexey Polubelov
 */
trait RawStateAccess {

    def getStateByPartialCompositeKey(key: CompositeKey): QueryResultsIterator[KeyValue]

    def delState(key: String): Unit

    def getState(key: String): Array[Byte]

    def putState(key: String, data: Array[Byte]): Unit

}
