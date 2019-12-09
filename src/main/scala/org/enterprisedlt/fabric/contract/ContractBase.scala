package org.enterprisedlt.fabric.contract

import java.io.{PrintWriter, StringWriter}
import java.lang.reflect.{InvocationTargetException, Method}
import java.nio.charset.StandardCharsets

import org.enterprisedlt.spec._
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

    private val logger: Logger = LoggerFactory.getLogger(this.getClass)

    type ChainCodeFunction = ChaincodeStub => Response

    private[this] val ChainCodeFunctions: Map[String, ChainCodeFunction] =
        scanMethods(this.getClass)
          .filter(_.isAnnotationPresent(classOf[ContractOperation]))
          .map(m => (m.getName, createChainCodeFunctionWrapper(m, m.getAnnotation(classOf[ContractOperation]).value())))
          .toMap

    private val InitFunction: Option[ChainCodeFunction] =
        scanMethods(this.getClass)
          .filter(_.isAnnotationPresent(classOf[ContractInit])) match {
            case Array() => None
            case Array(init) => Some(createChainCodeFunctionWrapper(init, OperationType.Invoke))
            case _ => throw new RuntimeException(s"Only 1 method annotated with @${classOf[ContractInit].getSimpleName} allowed")
        }

    private[this] def scanMethods(c: Class[_]): Array[Method] = {
        c.getDeclaredMethods ++
          c.getInterfaces.flatMap(scanMethods) ++
          Option(c.getSuperclass).map(scanMethods).getOrElse(Array.empty)
    }

    private[this] def createChainCodeFunctionWrapper(m: Method, opType: OperationType): ChainCodeFunction =
        opType match {
            case OperationType.Invoke =>
                val types = m.getParameters.toSeq.map(_.getType)
                chainCodeFunctionTemplate(m, types)

            case OperationType.Query =>
                val types = m.getParameters.toSeq.map(_.getType)
                chainCodeFunctionTemplate(m, types)
            //            case r => throw new RuntimeException(s"Method '${m.getName}' return type is [${r.getCanonicalName}], but must be one of ${classOf[InvokeResult[_, _]].getCanonicalName} ${classOf[QueryResult[_, _]].getCanonicalName}")
        }

    private[this] def chainCodeFunctionTemplate
    (m: Method, types: Seq[Class[_]])
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
                    case Success(v) => mkSuccessResponse(v)
                    case ErrorResult(payload) => mkErrorResponse(payload)
                    case ExecutionError(msg) => mkErrorResponse(msg)
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

    private def mkErrorResponse[T](v: T): Response =
        v match {
            //            case t: Throwable => mkExceptionResponse(t)
            case msg: String => new Chaincode.Response(Status.INTERNAL_SERVER_ERROR, msg, null)
            case other => new Chaincode.Response(Status.INTERNAL_SERVER_ERROR, null, codecs.resultEncoder.encode(other))
        }

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
