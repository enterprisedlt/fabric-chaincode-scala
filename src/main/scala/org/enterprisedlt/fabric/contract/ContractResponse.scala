package org.enterprisedlt.fabric.contract

/**
  * @author Alexey Polubelov
  */
sealed trait ContractResponse

case class Success[T](value: T) extends ContractResponse

object Success {
    def apply(): ContractResponse = Success(null)
}

case class Error(msg: String) extends ContractResponse

object ContractResponseConversions {
    // TODO: or use type extension? which better? think about...
    implicit def Either2ContractResponse[T]: Either[String, T] => ContractResponse = {
        case Right(()) => Success()
        case Right(x) => Success(x)
        case Left(msg) => Error(msg)
    }
}