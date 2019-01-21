package com.github.apolubelov.fabric.contract

import java.time.Instant

import com.github.apolubelov.fabric.contract.codec.BinaryCodec
import com.github.apolubelov.fabric.contract.msp.ClientIdentity
import com.github.apolubelov.fabric.contract.store.{ChannelPrivateStateAccess, ChannelStateAccess, Store}
import org.hyperledger.fabric.shim.ChaincodeStub

/**
  * @author Alexey Polubelov
  */
class ContractContext(
    api: ChaincodeStub,
    ledgerCodec: BinaryCodec,
    simpleTypesPartitionName: String
) {
    private[this] lazy val _channelStore = new Store(new ChannelStateAccess(api), ledgerCodec, simpleTypesPartitionName)
    private[this] lazy val _clientIdentity = ClientIdentity(api.getCreator)
    private[this] lazy val _transactionInformation = new TransactionInfo(api)

    def lowLevelApi: ChaincodeStub = api

    def store: Store = _channelStore

    def privateStore(collection: String) = new Store(new ChannelPrivateStateAccess(api, collection), ledgerCodec, simpleTypesPartitionName)

    def clientIdentity: ClientIdentity = _clientIdentity

    def transaction: TransactionInfo = _transactionInformation
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
}


