package com.github.apolubelov.fabric.contract.store

import org.apache.commons.logging.LogFactory
import org.hyperledger.fabric.shim.ChaincodeStub
import org.hyperledger.fabric.shim.ledger.{CompositeKey, KeyValue, QueryResultsIterator}

/*
 * @author Alexey Polubelov
 */
class ChannelStateAccess(
    api: ChaincodeStub
) extends RawStateAccess {
    private[this] val logger = LogFactory.getLog(classOf[ChannelStateAccess])

    override def putState(key: String, data: Array[Byte]): Unit = {
        logger.debug(s"putState( key= $key, data=$data )")
        api.putState(key, data)
        logger.debug(s"putState done.")
    }

    override def getState(key: String): Array[Byte] = {
        logger.debug(s"getState( key= $key )")
        val r = api.getState(key)
        logger.debug(s"getState -> $r")
        r
    }

    override def delState(key: String): Unit = api.delState(key)

    override def getStateByPartialCompositeKey(key: CompositeKey): QueryResultsIterator[KeyValue] = {
        logger.debug(s"getStateByPartialCompositeKey( key= $key )")
        val r = api.getStateByPartialCompositeKey(key)
        logger.debug(s"getStateByPartialCompositeKey -> $r")
        r
    }
}