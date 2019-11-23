package org.enterprisedlt.fabric.contract.codec

import org.enterprisedlt.fabric.proto.TestMessage
import org.scalatest.FunSuite
import org.scalatest.Matchers._


/**
  * @author Maxim Fedin
  */
class ProtobufCodecTest extends FunSuite {

    val codec = new ProtobufCodec()

    case class TestClass()

    test("Protobuf structure encoding/decoding works fine") {
        val msg: TestMessage = TestMessage.newBuilder()
          .setStringValue("Hello World")
          .setIntValue(10)
          .build()
        //
        val encoded = codec.encode[TestMessage](msg)
        val decoded = codec.decode[TestMessage](encoded, classOf[TestMessage])
        //
        assert(msg == decoded)
    }

    test("Unsupported class for non protobuf object works fine") {

        an[java.lang.Exception] should be thrownBy { // Ensure a particular exception type is thrown
            codec.decode[TestClass](new Array[Byte](0), classOf[TestClass])
        }
    }

    test("Int structure encoding/decoding works fine") {
        val msg: Int = 100
        //
        val encoded = codec.encode[Int](msg)
        val decoded = codec.decode[Int](encoded, classOf[Int])
        //
        assert(msg == decoded)
    }

    test("Byte structure encoding/decoding works fine") {
        val msg: Byte = 123
        //
        val encoded = codec.encode[Byte](msg)
        val decoded = codec.decode[Byte](encoded, classOf[Byte])
        //
        assert(msg == decoded)
    }


    test("Short structure encoding/decoding works fine") {
        val msg: Short = 2
        //
        val encoded = codec.encode[Short](msg)
        val decoded = codec.decode[Short](encoded, classOf[Short])
        //
        assert(msg == decoded)
    }

    test("Char structure encoding/decoding works fine") {
        val msg: Char = 'X'
        //
        val encoded = codec.encode[Char](msg)
        val decoded = codec.decode[Char](encoded, classOf[Char])
        //
        assert(msg == decoded)
    }

    test("Float structure encoding/decoding works fine") {
        val msg: Float = .1f
        //
        val encoded = codec.encode[Float](msg)
        val decoded = codec.decode[Float](encoded, classOf[Float])
        //
        assert(msg == decoded)
    }

    test("Long structure encoding/decoding works fine") {
        val msg: Long = 100000
        //
        val encoded = codec.encode[Long](msg)
        val decoded = codec.decode[Long](encoded, classOf[Long])
        //
        assert(msg == decoded)
    }

    test("Double structure encoding/decoding works fine") {
        val msg: Double = 100000.12
        //
        val encoded = codec.encode[Double](msg)
        val decoded = codec.decode[Double](encoded, classOf[Double])
        //
        assert(msg == decoded)
    }


}
