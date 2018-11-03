package com.github.apolubelov.fabric.contract

import java.io.{PrintWriter, StringWriter}
import java.lang.reflect.{InvocationTargetException, Method}
import java.nio.charset.StandardCharsets

import com.github.apolubelov.fabric.contract.annotations.{ContractInit, ContractOperation}
import com.github.apolubelov.fabric.contract.codec.{BinaryCodec, GsonCodec, TextCodec, Utf8Codec}
import org.hyperledger.fabric.shim.Chaincode.Response
import org.hyperledger.fabric.shim.Chaincode.Response.Status
import org.hyperledger.fabric.shim.{Chaincode, ChaincodeBase, ChaincodeStub}

import scala.collection.JavaConverters._

/*
 * @author Alexey Polubelov
 */
abstract class ContractBase extends ChaincodeBase {

    type ChainCodeFunction = ChaincodeStub => Response

    // default text codec which used for all encoding/decoding, exposed as public so one can override if require
    val defaultTextCodec: TextCodec = GsonCodec()

    // default parameters decoder, can be overridden
    def parametersDecoder: TextCodec = defaultTextCodec

    // default ledger codec, can be overridden
    def ledgerCodec: BinaryCodec = Utf8Codec(defaultTextCodec)

    // default result encoder, can be overridden
    def resultEncoder: BinaryCodec = ledgerCodec

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
            case x if classOf[ContractResponse].equals(x) || classOf[Unit].equals(x) =>
                m.getParameters.toSeq match {
                    case l +: tail if l.getType.equals(classOf[ContractContext]) =>
                        val types = tail.map(_.getType)
                        chainCodeFunctionTemplate(m, types, classOf[Unit].equals(x))
                    case _ => throw new RuntimeException(s"Wrong method parameter types - first parameter must be of type Ledger")
                }
            case r => throw new RuntimeException(s"Method '${m.getName}' return type [${r.getCanonicalName}] must be ${classOf[ContractResponse].getCanonicalName}")
        }

    private[this] def chainCodeFunctionTemplate
    (m: Method, types: Seq[Class[_]], functionReturnTypeIsUnit: Boolean)
      (api: ChaincodeStub): Response = api.getParameters.asScala.toArray match {
        case parameters if parameters.length == types.length =>
            m.setAccessible(true) // for anonymous instances
            try {
                m.invoke(this,
                    new ContractContext(api, ledgerCodec) +: parameters
                      .zip(types)
                      .map {
                          case (value, clz) if classOf[Class[_]].equals(clz) => resolveClassByName(value).getOrElse(throw new RuntimeException(s"No entity defined for '$value'"))
                          case (value, clz) if classOf[Resolvable].equals(clz) => new ResolvableImpl(value, parametersDecoder)
                          case (value, clz) => parametersDecoder.decode(value, clz).asInstanceOf[AnyRef]
                      }: _*
                ) match {
                    case Success(null) => mkSuccessResponse()
                    case Success(v) => mkSuccessResponse(v)
                    case Error(msg) => mkErrorResponse(msg)
                    case r if functionReturnTypeIsUnit && r == null => mkSuccessResponse() // if return type is Unit (i.e. void in Java) the return value must be null
                    case unexpected => throw new RuntimeException(s"Some strange magic happened [return value is $unexpected]")
                }
            } catch {
                case ex: InvocationTargetException => mkExceptionResponse(ex.getCause)
                case t: Throwable => mkExceptionResponse(t)

            }
        case parameters => mkErrorResponse(s"Wrong arguments count, expected ${types.length} but got ${parameters.length}")
    }

    private def mkSuccessResponse(): Response = new Chaincode.Response(Status.SUCCESS, null, null)

    private def mkSuccessResponse[T](v: T): Response = new Chaincode.Response(Status.SUCCESS, null, resultEncoder.encode(v))

    private def mkErrorResponse(msg: String): Response =
        new Chaincode.Response(Status.INTERNAL_SERVER_ERROR, msg, null)

    private def mkExceptionResponse(throwable: Throwable): Response =
        new Chaincode.Response(Status.INTERNAL_SERVER_ERROR, throwable.getMessage,
            stackTraceString(throwable).getBytes(StandardCharsets.UTF_8))

    private def stackTraceString(throwable: Throwable): String = Option(throwable).map { t =>
        val buffer = new StringWriter
        throwable.printStackTrace(new PrintWriter(buffer))
        buffer.toString
    } getOrElse ""


    def resolveClassByName(name: String): Option[Class[_]] =
        throw new RuntimeException("To use parameters of type Class override 'resolveClassByName'")

    override def init(api: ChaincodeStub): Response =
        InitFunction
          .map(_ (api))
          .getOrElse(mkSuccessResponse())

    override def invoke(api: ChaincodeStub): Response =
        ChainCodeFunctions
          .get(api.getFunction).map(_ (api))
          .getOrElse(mkErrorResponse(s"Unknown function ${api.getFunction}"))

    private class ResolvableImpl(
        raw: String,
        codec: TextCodec
    ) extends Resolvable {
        override def resolve[T](clz: Class[T]): T = codec.decode(raw, clz)
    }

}
