package com.github.apolubelov.fabric.contract.codec

import com.google.gson.{Gson, GsonBuilder}

/*
 * @author Alexey Polubelov
 */
//Note: Gson is already dependency of fabric-chaincode-shim so lets use it instead of other Json serializers
object JsonCodec extends TextCodec {

    override def encode[T](value: T): String = gson.toJson(value)

    override def decode[T](value: String, clz: Class[T]): T = gson.fromJson(value, clz)

    private[this] def gson: Gson = (new GsonBuilder).create
}
