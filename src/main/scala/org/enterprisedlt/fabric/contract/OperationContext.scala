package org.enterprisedlt.fabric.contract

import java.nio.charset.StandardCharsets
import java.time.Instant

import org.enterprisedlt.fabric.contract.msp.ClientIdentity
import org.enterprisedlt.fabric.contract.store.{ChannelPrivateStateAccess, ChannelStateAccess, Store}
import org.enterprisedlt.spec.{BinaryCodec, ContractResult}
import org.hyperledger.fabric.shim.Chaincode.Response.Status
import org.hyperledger.fabric.shim.ChaincodeStub

import scala.collection.JavaConverters._
import scala.reflect.{ClassTag, classTag}

/**
 * @author Alexey Polubelov
 */
object OperationContext {
    private[this] val threadContext = new ThreadLocal[ThreadContext]

    private[contract] def set(api: ChaincodeStub, codecs: ContractCodecs, simpleTypesPartitionName: String): Unit =
        threadContext.set(ThreadContext(api, codecs, simpleTypesPartitionName))

    private[contract] def clear(): Unit = threadContext.remove()

    //
    //
    //

    def lowLevelApi: ChaincodeStub = threadContext.get.api

    def store: Store = threadContext.get.channelStore

    def privateStore(collection: String) =
        new Store(
            new ChannelPrivateStateAccess(threadContext.get.api, collection),
            threadContext.get.codecs.ledgerCodec,
            threadContext.get.simpleTypesPartitionName
        )

    def clientIdentity: ClientIdentity = threadContext.get.clientIdentity

    def transaction: TransactionInfo = threadContext.get.transactionInformation

    def callChainCode[T: ClassTag](channel: String, name: String, function: String, args: Any*)(codec: Option[BinaryCodec] = None): ContractResult[T] = {
        val argsForInvoke = List(function.getBytes(StandardCharsets.UTF_8)) ++ args.map(codec.getOrElse(threadContext.get.codecs.parametersDecoder).encode)
        val response = lowLevelApi.invokeChaincode(name, argsForInvoke.asJava, channel)
        response.getStatus match {
            case Status.SUCCESS =>
                Right(
                    threadContext.get.codecs.resultEncoder.decode[T](
                        response.getPayload, classTag[T].runtimeClass
                    )
                )

            case _ => Left(response.getMessage)
        }
    }

    def transientByKey[T: ClassTag](key: String): Option[T] =
        Option(lowLevelApi.getTransient)
          .flatMap(m => Option(m.get(key)))
          .flatMap(p => Option(threadContext.get.codecs.transientDecoder.decode[T](p, classTag[T].runtimeClass)))

}

private[contract] case class ThreadContext(
    api: ChaincodeStub,
    codecs: ContractCodecs,
    simpleTypesPartitionName: String
) {
    private[contract] lazy val channelStore = new Store(new ChannelStateAccess(api), codecs.ledgerCodec, simpleTypesPartitionName)
    private[contract] lazy val clientIdentity = ClientIdentity(api.getCreator)
    private[contract] lazy val transactionInformation = new TransactionInfo(api)
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


