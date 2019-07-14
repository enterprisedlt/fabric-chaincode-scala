package org.enterprisedlt.fabric.contract.msp

import java.security.cert.{CertificateFactory, X509Certificate}

import org.hyperledger.fabric.protos.msp.Identities
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
class ClientIdentity(
    identity: Identities.SerializedIdentity
) {
    val mspId: String = identity.getMspid
    lazy val x509Certificate: X509Certificate = parseX509Certificate(identity)

    private def parseX509Certificate(identity: Identities.SerializedIdentity): X509Certificate = {
        val certFactory = CertificateFactory.getInstance("X.509")
        certFactory.generateCertificate(identity.getIdBytes.newInput()).asInstanceOf[X509Certificate]
    }

}

object ClientIdentity {
    private val logger = LoggerFactory.getLogger(classOf[ClientIdentity])

    def apply(serialized: Array[Byte]): ClientIdentity = {
        logger.trace("Trying to decode Creator data...")
        val identity: Identities.SerializedIdentity = Identities.SerializedIdentity.parseFrom(serialized)
        logger.trace(s"Creator data decoded - MspId: ${identity.getMspid}")
        new ClientIdentity(identity)
    }
}