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
