package org.enterprisedlt.fabric.contract

import java.time.Instant

import org.enterprisedlt.fabric.contract.msp.ClientIdentity
import org.enterprisedlt.fabric.contract.store.{ChannelPrivateStateAccess, ChannelStateAccess, Store}
import org.hyperledger.fabric.shim.Chaincode.Response.Status
import org.hyperledger.fabric.shim.ChaincodeStub

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

    def callChainCode[T: ClassTag](name: String, function: String, args: Any*): Either[ErrorResponse, T] = {
        val argsForInvoke = List(function) ++ args.map(codecs.parametersDecoder.encode)
        val response = lowLevelApi.invokeChaincodeWithStringArgs(name, argsForInvoke.asJava)
        response.getStatus match {
            case Status.SUCCESS =>
                Right(
                    codecs.resultEncoder.decode(response.getPayload, classTag[T].runtimeClass.asInstanceOf[Class[T]])
                )

            case _ =>
                Left(
                    ErrorResponse(
                        response.getStatusCode,
                        codecs.parametersDecoder.decode(response.getMessage, classOf[String])
                    )
                )
        }
    }

    def transientByKey[T: ClassTag](context: ContractContext, key: String): Option[T] =
        Option(context.lowLevelApi.getTransient)
          .flatMap(m => Option(m.get(key)))
          .flatMap(p => Option(codecs.transientDecoder.decode(p, classTag[T].runtimeClass.asInstanceOf[Class[T]])))

}

case class ErrorResponse(
    status: Int,
    message: String
)

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


