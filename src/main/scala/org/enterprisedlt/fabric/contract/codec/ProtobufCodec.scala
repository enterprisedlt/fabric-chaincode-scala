package org.enterprisedlt.fabric.contract.codec

import com.google.protobuf.{CodedInputStream, CodedOutputStream, Message, Parser}

/**
  * @author Maxim Fedin
  */
class ProtobufCodec extends BinaryCodec {

    override def encode[T](value: T): Array[Byte] = {
        value match {

            case m: Byte =>
                val buffer = new Array[Byte](1)
                CodedOutputStream.newInstance(buffer).write(m)
                buffer

            case m: Boolean =>
                val buffer = new Array[Byte](1)
                CodedOutputStream.newInstance(buffer).writeBoolNoTag(m)
                buffer

            case m: Short =>
                val buffer = new Array[Byte](2)
                CodedOutputStream.newInstance(buffer).writeSInt32NoTag(m.toInt)
                buffer

            case m: Char =>
                val buffer = new Array[Byte](2)
                CodedOutputStream.newInstance(buffer).writeSInt32NoTag(m.toInt)
                buffer

            case m: Int =>
                val buffer = new Array[Byte](4)
                CodedOutputStream.newInstance(buffer).writeSInt32NoTag(m)
                buffer

            case m: Float =>
                val buffer = new Array[Byte](4)
                CodedOutputStream.newInstance(buffer).writeFloatNoTag(m)
                buffer

            case m: Long =>
                val buffer = new Array[Byte](8)
                CodedOutputStream.newInstance(buffer).writeSInt64NoTag(m)
                buffer

            case m: Double =>
                val buffer = new Array[Byte](8)
                CodedOutputStream.newInstance(buffer).writeDoubleNoTag(m)
                buffer

            case m: Message => m.toByteArray

            case _ => throw new Exception("Unsupported class")
        }
    }

    override  def decode[T](value: Array[Byte], clz: Class[T]): T = {
        clz match {

            case x if classOf[Int].equals(x) =>
                val t = CodedInputStream.newInstance(value).readSInt32()
                t.asInstanceOf[T]

            case x if classOf[Byte].equals(x) =>
                val t = CodedInputStream.newInstance(value).readRawByte()
                t.asInstanceOf[T]

            case x if classOf[Short].equals(x) =>
                val t: Int = CodedInputStream.newInstance(value).readSInt32()
                t.toShort.asInstanceOf[T]

            case x if classOf[Char].equals(x) =>
                val t = CodedInputStream.newInstance(value).readSInt32()
                t.toChar.asInstanceOf[T]

            case x if classOf[Float].equals(x) =>
                val t = CodedInputStream.newInstance(value).readFloat()
                t.asInstanceOf[T]

            case x if classOf[Long].equals(x) =>
                val t = CodedInputStream.newInstance(value).readSInt64()
                t.asInstanceOf[T]

            case x if classOf[Double].equals(x) =>
                val t = CodedInputStream.newInstance(value).readDouble()
                t.asInstanceOf[T]

            case x if x.isInstanceOf[Class[Message]] =>
                val parser = clz.getMethod("parser").invoke(null).asInstanceOf[Parser[T]]
                parser.parseFrom(value)

            case _ =>
                throw new Exception("Unsupported class")

        }
    }
}

object ProtobufCodec {
    def apply(): ProtobufCodec = new ProtobufCodec()
}
