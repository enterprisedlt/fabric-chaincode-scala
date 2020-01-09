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
    private[this] val _context = new ThreadLocal[ThreadContext]

    private[this] def context: ThreadContext =
        Option(_context.get)
          .getOrElse(
              throw new IllegalStateException("Operation context should never be used out of ContractOperation scope")
          )

    private[contract] def set(api: ChaincodeStub, codecs: ContractCodecs, simpleTypesPartitionName: String): Unit =
        _context.set(ThreadContext(api, codecs, simpleTypesPartitionName))

    private[contract] def clear(): Unit = _context.remove()

    //
    //
    //

    def codecs: ContractCodecs = context.codecs

    def lowLevelApi: ChaincodeStub = context.api

    def store: Store = context.channelStore

    def privateStore(collection: String) =
        new Store(
            new ChannelPrivateStateAccess(context.api, collection),
            context.codecs.ledgerCodec,
            context.simpleTypesPartitionName
        )

    def clientIdentity: ClientIdentity = context.clientIdentity

    def transaction: TransactionInfo = context.transactionInformation

    def callChainCode[T: ClassTag](channel: String, name: String, function: String, args: Any*)(codec: Option[BinaryCodec] = None): ContractResult[T] = {
        val argsForInvoke = List(function.getBytes(StandardCharsets.UTF_8)) ++ args.map(codec.getOrElse(context.codecs.parametersDecoder).encode)
        val response = lowLevelApi.invokeChaincode(name, argsForInvoke.asJava, channel)
        response.getStatus match {
            case Status.SUCCESS =>
                Right(
                    codec.getOrElse(context.codecs.resultEncoder)
                      .decode[T](
                          response.getPayload, classTag[T].runtimeClass
                      )
                )
            case _ => Left(response.getMessage)
        }
    }

    def transientByKey[T: ClassTag](key: String): Option[T] =
        Option(lowLevelApi.getTransient)
          .flatMap(m => Option(m.get(key)))
          .flatMap(p => Option(context.codecs.transientDecoder.decode[T](p, classTag[T].runtimeClass)))

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


