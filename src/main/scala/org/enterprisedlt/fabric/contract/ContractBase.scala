package org.enterprisedlt.fabric.contract

import java.io.{PrintWriter, StringWriter}
import java.lang.reflect.{InvocationTargetException, Method, Parameter}
import java.nio.charset.StandardCharsets

import org.enterprisedlt.fabric.contract.exception.ResolveRoleFunctionException
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
    simpleTypesPartitionName: String = "SIMPLE",
    resolveRole: ContractContext => String = throw ResolveRoleFunctionException()
) extends ChaincodeBaseAdapter {
    type ChainCodeFunction = ChaincodeStub => Response

    private val logger: Logger = LoggerFactory.getLogger(this.getClass)

    private[this] val ChainCodeFunctions: Map[String, ChainCodeFunction] =
        scanMethods(this.getClass)
          .filter(_.isAnnotationPresent(classOf[ContractOperation]))
          .map(m => (m.getName, createChainCodeFunctionWrapper(m)))
          .toMap

    private val InitFunction: Option[ChainCodeFunction] =
        scanMethods(this.getClass)
          .filter(_.isAnnotationPresent(classOf[ContractInit])) match {
            case Array() => None
            case Array(init) => Some(createChainCodeFunctionWrapper(init))
            case _ => throw new RuntimeException(s"Only 1 method annotated with @${classOf[ContractInit].getSimpleName} allowed")
        }

    private[this] def scanMethods(c: Class[_]): Array[Method] = {
        c.getDeclaredMethods ++
          c.getInterfaces.flatMap(scanMethods) ++
          Option(c.getSuperclass).map(scanMethods).getOrElse(Array.empty)
    }

    private[this] def createChainCodeFunctionWrapper(m: Method): ChainCodeFunction =
        m.getReturnType match {
            case x if classOf[ContractResult[_]].equals(x) => chainCodeFunctionTemplate(m)
            case r =>
                throw new RuntimeException(
                    s"Method '${m.getName}' return type [${r.getCanonicalName}] must be ${classOf[ContractResult[_]].getCanonicalName}"
                )
        }

    private[this] def chainCodeFunctionTemplate(m: Method)(api: ChaincodeStub): Response =
        try {
            m.setAccessible(true) // for anonymous instances
            logger.debug(s"Executing ${m.getName}")
            val context = new ContractContext(api, codecs, simpleTypesPartitionName)
            Either.cond(
                Option(m.getAnnotation(classOf[Restrict]))
                  .map(_.value())
                  .forall(_.contains(resolveRole(context))), (), "Access denied")
              .flatMap { _ =>
                  makeParameters(m.getParameters, api.getArgs.asScala.tail.toArray, api.getTransient)
              }
              .flatMap { parameters =>
                  ContextHolder.set(context)
                  val result = m.invoke(this, parameters: _*)
                  ContextHolder.clear()
                  logger.debug(s"Execution of ${m.getName} done, result: $result")
                  result.asInstanceOf[ContractResult[_]]
              }
            match {
                case Right(v) => mkSuccessResponse(v)
                case Left(msg) => mkErrorResponse(msg)
                //                case unexpected => mkErrorResponse(s"Some strange magic happened [return value is $unexpected]")
            }
        } catch {
            case ex: InvocationTargetException =>
                logger.error("Exception during contract operation invoke", ex)
                mkExceptionResponse(ex.getCause)
            case t: Throwable =>
                logger.error("Exception during contract operation invoke (library)", t)
                mkExceptionResponse(t)
        }

    private def makeParameters(
        parameters: Array[Parameter],
        arguments: Array[Array[Byte]],
        transientMap: java.util.Map[String, Array[Byte]]
    ): Either[String, Array[AnyRef]] =
        foldLeftEither(parameters)((0, Array.empty[AnyRef])) { case ((i, result), parameter) =>
            if (parameter.isAnnotationPresent(classOf[Transient])) {
                val valueBytes = Option(transientMap.get(parameter.getName)).toRight(s"${parameter.getName} value is missing in transient map")
                val value = valueBytes.map(v => codecs.transientDecoder.decode(v, parameter.getType).asInstanceOf[AnyRef])
                value.map { v => (i, result :+ v) }
            }
            else {
                if (i < arguments.length) {
                    val valueBytes = arguments(i)
                    val value = codecs.parametersDecoder.decode(valueBytes, parameter.getType).asInstanceOf[AnyRef]
                    Right((i + 1, result :+ value))
                } else Left(s"Wrong arguments count")
            }
        }.map(_._2)

    private def foldLeftEither[X, L, R](elements: Iterable[X])(z: R)(f: (R, X) => Either[L, R]): Either[L, R] =
        elements.foldLeft(Right(z).asInstanceOf[Either[L, R]]) { case (r, x) => r.flatMap(v => f(v, x)) }

    private def mkSuccessResponse(): Response = new Chaincode.Response(Status.SUCCESS, null, null)

    private def mkSuccessResponse[T](v: T): Response = new Chaincode.Response(Status.SUCCESS, null, codecs.resultEncoder.encode(v))

    private def mkErrorResponse[T](v: T): Response =
        v match {
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
