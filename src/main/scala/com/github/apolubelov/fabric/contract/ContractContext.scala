package com.github.apolubelov.fabric.contract

import com.github.apolubelov.fabric.contract.Util.ClientIdentity
import com.github.apolubelov.fabric.contract.store.{ChannelPrivateStateAccess, ChannelStateAccess, Store}
import org.hyperledger.fabric.shim.ChaincodeStub

/*
 * @author Alexey Polubelov
 */
class ContractContext(
    _lowLevelApi: ChaincodeStub
) {
    private[this] lazy val _channelStore = new Store(new ChannelStateAccess(_lowLevelApi))
    private[this] lazy val _clientIdentity = ClientIdentity(_lowLevelApi.getCreator)

    def lowLevelApi: ChaincodeStub = _lowLevelApi

    def store: Store = _channelStore

    def privateStore(collection: String) = new Store(new ChannelPrivateStateAccess(_lowLevelApi, collection))

    def clientIdentity: ClientIdentity = _clientIdentity
}


