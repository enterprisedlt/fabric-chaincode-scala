package org.enterprisedlt.fabric.contract.codec

import com.google.gson.{Gson, GsonBuilder}
import org.slf4j.{Logger, LoggerFactory}

/**
 * @author Alexey Polubelov
 */
//Note: Gson is already dependency of fabric-chaincode-shim so lets use it instead of other Json serializers
case class GsonCodec(
    skipText: Boolean = true,
    gsonOptions: GsonBuilder => GsonBuilder = x => x
) extends TextCodec {
    private val logger: Logger = LoggerFactory.getLogger(this.getClass)

    override def encode[T](value: T): String =
        value match {
            case text: String if skipText =>
                logger.trace(s"Skipped encoding of pure text value '$value'")
                text
            case _ =>
                val result = gson.toJson(value)
                logger.trace(s"Encoded '$value' ==> '$result'")
                result
        }


    override def decode[T](value: String, clz: Class[T]): T =
        clz match {
            case v if skipText && classOf[String].equals(v) =>
                logger.trace(s"Skipped decoding of pure text value '$value'")
                value.asInstanceOf[T]
            case _ =>
                val result = gson.fromJson(value, clz)
                logger.trace(s"Decoded '$value' ==> '$result'")
                result
        }


    private[this] def gson: Gson = gsonOptions(new GsonBuilder).create
}
