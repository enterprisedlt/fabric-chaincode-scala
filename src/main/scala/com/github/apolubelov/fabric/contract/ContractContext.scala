package com.github.apolubelov.fabric.contract

import com.github.apolubelov.fabric.contract.Util.ClientIdentity
import com.github.apolubelov.fabric.contract.codec.BinaryCodec
import com.github.apolubelov.fabric.contract.store.{ChannelPrivateStateAccess, ChannelStateAccess, Store}
import org.hyperledger.fabric.shim.ChaincodeStub

/**
 * @author Alexey Polubelov
 */
class ContractContext(
    api: ChaincodeStub,
    ledgerCodec: BinaryCodec
) {
    private[this] lazy val _channelStore = new Store(new ChannelStateAccess(api), ledgerCodec)
    private[this] lazy val _clientIdentity = ClientIdentity(api.getCreator)

    def lowLevelApi: ChaincodeStub = api

    def store: Store = _channelStore

    def privateStore(collection: String) = new Store(new ChannelPrivateStateAccess(api, collection), ledgerCodec)

    def clientIdentity: ClientIdentity = _clientIdentity
}


