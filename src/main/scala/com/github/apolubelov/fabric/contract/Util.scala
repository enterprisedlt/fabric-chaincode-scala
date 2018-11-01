package com.github.apolubelov.fabric.contract

import java.io.{PrintWriter, StringWriter}
import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets.UTF_8

import com.github.apolubelov.fabric.contract.codec.BinaryCodec
import com.google.protobuf.{ByteString, CodedInputStream}
import org.apache.commons.logging.LogFactory
import org.hyperledger.fabric.shim.Chaincode
import org.hyperledger.fabric.shim.Chaincode.Response
import org.hyperledger.fabric.shim.Chaincode.Response.Status

/*
 * @author Alexey Polubelov
 */
object Util {

    implicit class RawLedgerValue(ledgerValue: Array[Byte]) {
        def asUtf8String: String = new String(ledgerValue, UTF_8)
    }

    implicit class StringToLedgerValue(ledgerValue: String) {
        def asUtf8Bytes: Array[Byte] = ledgerValue.getBytes(UTF_8)
    }

    object SuccessResponse {
        def apply(message: String, payload: Array[Byte]): Response = new Chaincode.Response(Status.SUCCESS, message, payload)

        def apply(): Response = apply(null, null)

//        def apply(payload: Array[Byte]): Response = apply(null, payload)

        def apply[T](payload: T)(implicit codec: BinaryCodec): Response = apply(null, codec.encode(payload))
    }

    object ErrorResponse {
        def apply(message: String, payload: Array[Byte]): Response = new Chaincode.Response(Status.INTERNAL_SERVER_ERROR, message, payload)

        def apply(): Response = apply(null, null)

        def apply(message: String): Response = apply(message, null)

        def apply(payload: Array[Byte]): Response = apply(null, payload)

        def apply(throwable: Throwable): Response = apply(throwable.getLocalizedMessage, stackTraceString(throwable).getBytes(StandardCharsets.UTF_8))
    }

    def stackTraceString(throwable: Throwable): String = Option(throwable).map { t =>
        val buffer = new StringWriter
        throwable.printStackTrace(new PrintWriter(buffer))
        buffer.toString
    } getOrElse ""

    case class ClientIdentity(
        mspId: String
    )

    object ClientIdentity {
        private[this] val logger = LogFactory.getLog(classOf[ClientIdentity])

        // This ugly code is just copied and pasted from proto-buf generated class:
        // org.hyperledger.fabric.protos.msp.Identities.SerializedIdentity
        // TODO: this must be removed after release of https://jira.hyperledger.org/browse/FAB-12568
        def apply(serialized: Array[Byte]): ClientIdentity = {
            logger.debug("Trying to deserialize Creator data...")
            val input = CodedInputStream.newInstance(serialized)
            var done = false
            var mspId = ""
            var idBytes = ByteString.EMPTY
            while (!done) {
                input.readTag match {
                    case 0 =>
                        done = true
                    case 10 =>
                        mspId = input.readStringRequireUtf8
                    case 18 =>
                        idBytes = input.readBytes
                    case t =>
                        if (!input.skipField(t)) done = true
                }
            }
            logger.debug(s"Creator data deserialized - MspId: $mspId")
            ClientIdentity(mspId)
        }
    }

    def camelCase(value: String): String = value.substring(0, 1).toLowerCase + value.substring(1)
}
