package com.github.apolubelov.fabric.contract.store

import org.hyperledger.fabric.shim.ChaincodeStub
import org.hyperledger.fabric.shim.ledger.{CompositeKey, KeyValue, QueryResultsIterator}

/*
 * @author Alexey Polubelov
 */
class ChannelStateAccess(
    api: ChaincodeStub
) extends RawStateAccess {

    override def putState(key: String, data: Array[Byte]): Unit = api.putState(key, data)

    override def getState(key: String): Array[Byte] = api.getState(key)

    override def delState(key: String): Unit = api.delState(key)

    override def getStateByPartialCompositeKey(key: CompositeKey): QueryResultsIterator[KeyValue] =
        api.getStateByPartialCompositeKey(key)

}