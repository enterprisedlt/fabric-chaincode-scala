package com.github.apolubelov.fabric.contract

/**
 * @author Alexey Polubelov
 */
sealed trait ContractResponse

case class Success[T](value: T) extends ContractResponse

object Success {
    def apply(): ContractResponse = Success(null)
}

case class Error(msg: String) extends ContractResponse


