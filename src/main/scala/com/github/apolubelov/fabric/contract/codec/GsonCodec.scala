package com.github.apolubelov.fabric.contract.codec

import com.google.gson.{Gson, GsonBuilder}

/*
 * @author Alexey Polubelov
 */
//Note: Gson is already dependency of fabric-chaincode-shim so lets use it instead of other Json serializers
class GsonCodec(
    skipText: Boolean = true,
    gsonOptions: GsonBuilder => GsonBuilder = x => x
) extends TextCodec {

    override def encode[T](value: T): String = {
        value match {
            case text: String if skipText => text
            case v => gson.toJson(v)
        }
    }

    override def decode[T](value: String, clz: Class[T]): T = {
        clz match {
            case v if skipText && classOf[String].equals(v) => value.asInstanceOf[T]
            case _ => gson.fromJson(value, clz)
        }
    }

    private[this] def gson: Gson = gsonOptions(new GsonBuilder).create
}

object GsonCodec {
    def apply(
        skipText: Boolean = true,
        gsonOptions: GsonBuilder => GsonBuilder = x => x
    ): GsonCodec =
        new GsonCodec(skipText, gsonOptions)
}