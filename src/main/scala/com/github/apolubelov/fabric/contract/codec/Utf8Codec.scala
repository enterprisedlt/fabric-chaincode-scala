package com.github.apolubelov.fabric.contract.codec

import java.nio.charset.StandardCharsets

/**
 * @author Alexey Polubelov
 */
class Utf8Codec(textCodec: TextCodec) extends BinaryCodec {

    override def encode[T](value: T): Array[Byte] = textCodec.encode(value).getBytes(StandardCharsets.UTF_8)

    override def decode[T](value: Array[Byte], clz: Class[T]): T = textCodec.decode(new String(value, StandardCharsets.UTF_8), clz)
}

object Utf8Codec {
    def apply(textCodec: TextCodec): Utf8Codec = new Utf8Codec(textCodec)
}