package org.enterprisedlt.fabric.contract

object ContextHolder {
    private val contextStore = new ThreadLocal[ContractContext]()

    def set(contractContext: ContractContext): Unit = contextStore.set(contractContext)

    def get: ContractContext = contextStore.get

    def clear(): Unit = contextStore.remove()
}