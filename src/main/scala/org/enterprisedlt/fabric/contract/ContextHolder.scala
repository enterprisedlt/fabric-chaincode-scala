package org.enterprisedlt.fabric.contract

/**
 * @author
 */
object ContextHolder {
    private val contextParams = new ThreadLocal[ContractContext]()

    def set(contractContext: ContractContext): Unit = {
        contextParams.set(contractContext)
    }

    def get(): ContractContext = {
        contextParams.get()
    }
}