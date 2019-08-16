package org.enterprisedlt.fabric.contract

import java.time.Instant

import org.enterprisedlt.fabric.contract.msp.ClientIdentity
import org.enterprisedlt.fabric.contract.store.{ChannelPrivateStateAccess, ChannelStateAccess, Store}
import org.hyperledger.fabric.shim.ChaincodeStub
import org.hyperledger.fabric.shim.Chaincode.Response.Status

import scala.collection.JavaConverters._
import scala.reflect.{ClassTag, classTag}

/**
  * @author Alexey Polubelov
  */
class ContractContext(
    api: ChaincodeStub,
    codecs: ContractCodecs,
    simpleTypesPartitionName: String
) {
    private[this] lazy val _channelStore = new Store(new ChannelStateAccess(api), codecs.ledgerCodec, simpleTypesPartitionName)
    private[this] lazy val _clientIdentity = ClientIdentity(api.getCreator)
    private[this] lazy val _transactionInformation = new TransactionInfo(api)

    def lowLevelApi: ChaincodeStub = api

    def store: Store = _channelStore

    def privateStore(collection: String) = new Store(new ChannelPrivateStateAccess(api, collection), codecs.ledgerCodec, simpleTypesPartitionName)

    def clientIdentity: ClientIdentity = _clientIdentity

    def transaction: TransactionInfo = _transactionInformation

    def getTransientByKey[T: ClassTag](context: ContractContext, key: String): Either[String, T] = {
        val clz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
        Option(context.lowLevelApi.getTransient).toRight(s"There isn't transient map")
          .flatMap(m => Option(m.get(key)).toRight(s"Transient map doesn't have any value of class ${clz.getSimpleName}"))
          .flatMap(p => Option(codecs.transientDecoder.decode(p, clz)).toRight(s"There are some problems with decoding key $key in the transient map"))
    }
}

class TransactionInfo(
    api: ChaincodeStub
) {
    private lazy val _counter = new TxBoundCounter()

    def timestamp: Instant = api.getTxTimestamp

    def id: String = api.getTxId

    def counter: TxBoundCounter = _counter
}

class TxBoundCounter {
    private var counter = 0

    def current: Int = counter

    def next: Int = {
        counter = counter + 1
        counter
    }

    def reset(to: Int = 0): Unit = counter = to
}


