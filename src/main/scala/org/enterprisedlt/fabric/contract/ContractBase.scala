package org.enterprisedlt.fabric.contract

import java.io.{PrintWriter, StringWriter}
import java.lang.reflect.{InvocationTargetException, Method}
import java.nio.charset.StandardCharsets

import org.enterprisedlt.fabric.contract.annotation.{ContractInit, ContractOperation}
import org.hyperledger.fabric.shim.Chaincode.Response
import org.hyperledger.fabric.shim.Chaincode.Response.Status
import org.hyperledger.fabric.shim.{Chaincode, ChaincodeBaseAdapter, ChaincodeStub}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

/**
 * @author Alexey Polubelov
 */
abstract class ContractBase(
    codecs: ContractCodecs = ContractCodecs(),
    simpleTypesPartitionName: String = "SIMPLE"
) extends ChaincodeBaseAdapter {

    val logger: Logger = LoggerFactory.getLogger(this.getClass)

    type ChainCodeFunction = ChaincodeStub => Response

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
                val types = m.getParameters.toSeq.map(_.getType)
                chainCodeFunctionTemplate(m, types, classOf[Unit].equals(x))
            case r => throw new RuntimeException(s"Method '${m.getName}' return type [${r.getCanonicalName}] must be ${classOf[ContractResponse].getCanonicalName}")
        }

    private[this] def chainCodeFunctionTemplate
    (m: Method, types: Seq[Class[_]], functionReturnTypeIsUnit: Boolean)
      (api: ChaincodeStub)
    : Response = api.getArgs.asScala.tail.toArray match {
        case parameters if parameters.length == types.length =>
            m.setAccessible(true) // for anonymous instances
            try {
                logger.debug(s"Executing ${m.getName} ${parameters.mkString("(", ", ", ")")}")
                ContextHolder.set(new ContractContext(api, codecs, simpleTypesPartitionName))
                val result = m.invoke(this,
                    parameters
                      .zip(types)
                      .map {
                          case (value, clz) => codecs.parametersDecoder.decode(value, clz).asInstanceOf[AnyRef]
                      }: _*
                )
                ContextHolder.clear()
                logger.debug(s"Execution of ${m.getName} done, result: $result")
                result match {
                    // if return type is Unit (i.e. void in Java) the return value must be null:
                    case r if functionReturnTypeIsUnit && r == null => mkSuccessResponse()
                    case Success(null) => mkSuccessResponse()
                    case Success(v) => mkSuccessResponse(v)
                    case Error(msg) => mkErrorResponse(msg)
                    case unexpected => mkErrorResponse(s"Some strange magic happened [return value is $unexpected]")
                }
            } catch {
                case ex: InvocationTargetException =>
                    logger.error("Exception during contract operation invoke", ex)
                    mkExceptionResponse(ex.getCause)
                case t: Throwable =>
                    logger.error("Exception during contract operation invoke (library)", t)
                    mkExceptionResponse(t)

            }
        case parameters => mkErrorResponse(s"Wrong arguments count, expected ${types.length} but got ${parameters.length}")
    }

    private def mkSuccessResponse(): Response = new Chaincode.Response(Status.SUCCESS, null, null)

    private def mkSuccessResponse[T](v: T): Response = new Chaincode.Response(Status.SUCCESS, null, codecs.resultEncoder.encode(v))

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

    override def init(api: ChaincodeStub): Response =
        try {
            InitFunction
              .map(_ (api))
              .getOrElse(mkSuccessResponse())
        } catch {
            case t: Throwable =>
                logger.error("Got exception during init", t)
                throw t
        }

    override def invoke(api: ChaincodeStub): Response =
        try {
            ChainCodeFunctions
              .get(api.getFunction).map(_ (api))
              .getOrElse {
                  val msg = s"Unknown function ${api.getFunction}"
                  logger.debug(msg)
                  mkErrorResponse(msg)
              }
        } catch {
            case t: Throwable =>
                logger.error("Got exception during invoke", t)
                throw t
        }
}
