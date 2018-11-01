package com.github.apolubelov.fabric.contract

import java.lang.reflect.Method

import com.github.apolubelov.fabric.contract.Util.{ErrorResponse, SuccessResponse}
import com.github.apolubelov.fabric.contract.annotations.{ContractInit, ContractOperation}
import com.github.apolubelov.fabric.contract.codec.{JsonCodec, TextCodec}
import org.hyperledger.fabric.shim.Chaincode.Response
import org.hyperledger.fabric.shim.{ChaincodeBase, ChaincodeStub}

import scala.collection.JavaConverters._

/*
 * @author Alexey Polubelov
 */
abstract class ContractBase extends ChaincodeBase {

    type ChainCodeFunction = ChaincodeStub => Response

    // default parameters decoder, can be overridden
    def parametersDecoder: TextCodec = JsonCodec

    private[this] val ChainCodeFunctions: Map[String, ChainCodeFunction] =
        this.getClass.getDeclaredMethods
          .filter(_.isAnnotationPresent(classOf[ContractOperation]))
          .map(m => (m.getName, createChainCodeFunctionWrapper(m)))
          .toMap

    private val InitFunction: Option[ChainCodeFunction] = this.getClass.getDeclaredMethods
      .filter(_.isAnnotationPresent(classOf[ContractInit])) match {
        case Array() => None
        case Array(init) => Some(createChainCodeFunctionWrapper(init))
        case _ => throw new RuntimeException(s"Only 1 method annotated with @${classOf[ContractInit].getSimpleName} allowed")
    }

    private[this] def createChainCodeFunctionWrapper(m: Method): ChainCodeFunction =
        m.getReturnType match {
            // TODO: use better type for response
            case x if classOf[Response].equals(x) =>
                m.getParameters.toSeq match {
                    case l +: tail if l.getType.equals(classOf[ContractContext]) =>
                        val types = tail.map(_.getType)
                        chainCodeFunctionTemplate(m, types)
                    case _ => throw new RuntimeException(s"Wrong method parameter types - first parameter must be of type Ledger")
                }
            case _ => throw new RuntimeException(s"Method '${m.getName}' return type must be ${classOf[Response].getCanonicalName}")
        }

    private[this] def chainCodeFunctionTemplate
    (m: Method, types: Seq[Class[_]])
      (api: ChaincodeStub): Response = api.getParameters.asScala.toArray match {
        case parameters if parameters.length == types.length =>
            m.setAccessible(true) // for anonymous instances
            m.invoke(this,
                new ContractContext(api) +: parameters
                  .zip(types)
                  .map {
                      case (value, clz) if classOf[Class[_]].equals(clz) => resolveClassByName(value).getOrElse(throw new RuntimeException(s"No entity defined for '$value'"))
                      case (value, clz) if classOf[Resolvable].equals(clz) => new ResolvableImpl(value, parametersDecoder)
                      case (value, clz) => parametersDecoder.decode(value, clz).asInstanceOf[AnyRef]
                  }: _*
            ).asInstanceOf[Response]
        case parameters => ErrorResponse(s"Wrong arguments count, expected ${types.length} but got ${parameters.length}")
    }

    def resolveClassByName(name: String): Option[Class[_]] =
        throw new RuntimeException("To use parameters of type Class override 'resolveClassByName'")

    override def init(api: ChaincodeStub): Response =
        InitFunction
          .map(_ (api))
          .getOrElse(SuccessResponse())

    override def invoke(api: ChaincodeStub): Response =
        ChainCodeFunctions
          .get(api.getFunction).map(_ (api))
          .getOrElse(ErrorResponse(s"Unknown function ${api.getFunction}"))

    private class ResolvableImpl(
        raw: String,
        codec: TextCodec
    ) extends Resolvable {
        override def resolve[T](clz: Class[T]): T = codec.decode(raw, clz)
    }

}
