package org.enterprisedlt.fabric.contract

import org.enterprisedlt.spec.{InvokeResult, QueryResult}

import scala.util.Try

/**
 * @author Alexey Polubelov
 */

case class Success[+E, +V](value: V) extends QueryResult[E, V] with InvokeResult[E, V] {

    override def get: V = value

    override def toEither: Either[E, V] = Right(value)

    override def commit(): Unit = {}
}

private[contract] object SuccessH {
    def apply[E, V](): Success[E, V] = Success(null.asInstanceOf[V])
}

case class Error[+E, +V](msg: E) extends QueryResult[E, V] with InvokeResult[E, V] {

    override def get: V = null.asInstanceOf[V]

    override def toEither: Either[E, V] = Left(msg)

    override def commit(): Unit = {}
}

object ContractResponseConversions {

    implicit def Either2QueryResult[E, V]: Either[E, V] => QueryResult[E, V] = {
        case Right(()) => SuccessH()
        case Right(x) => Success(x)
        case Left(msg) => Error(msg)
    }

    implicit def Try2QueryResult[V]: Try[V] => QueryResult[Throwable, V] = {
        case scala.util.Success(x) => Success(x)
        case scala.util.Failure(msg) => Error(msg)
    }

    implicit def Either2InvokeResult[E, V]: Either[E, V] => InvokeResult[E, V] = {
        case Right(()) => SuccessH()
        case Right(x) => Success(x)
        case Left(msg) => Error(msg)
    }

    implicit def Try2InvokeResult[V]: Try[V] => InvokeResult[Throwable, V] = {
        case scala.util.Success(x) => Success(x)
        case scala.util.Failure(msg) => Error(msg)
    }

}
