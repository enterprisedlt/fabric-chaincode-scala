package com.github.apolubelov.fabric.contract

import java.nio.charset.StandardCharsets

import com.github.apolubelov.fabric.contract.Util.SuccessResponse
import com.github.apolubelov.fabric.contract.annotations.{ContractInit, ContractOperation}
import com.github.apolubelov.fabric.contract.codec.{JsonCodec, Utf8Codec}
import org.hyperledger.fabric.shim.ledger.CompositeKey
import org.hyperledger.fabric.shim.{Chaincode, ChaincodeStub}
import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import scala.collection.JavaConverters._

/*
 * @author Alexey Polubelov
 */
@RunWith(classOf[JUnitRunner])
class ContractBaseTest extends FunSuite {

    val TEST_CONTRACT: ContractBase = new ContractBase {
        implicit val Codec: Utf8Codec = Utf8Codec(JsonCodec)

        @ContractInit
        def testInit(context: ContractContext, p1: String, p2: Int, p3: Float, p4: Double, asset: Dummy): Chaincode.Response = {
            context.store.put("p1", p1)
            context.store.put("p2", p2)
            context.store.put("p3", p3)
            context.store.put("p4", p4)
            context.store.put("p5", asset)
            SuccessResponse()
        }

        @ContractOperation
        def testPutAsset(context: ContractContext, key: String, asset: Dummy): Chaincode.Response = {
            context.store.put("k1", asset)
            SuccessResponse()
        }

        @ContractOperation
        def testRawAsset(context: ContractContext, key: String, clz: Class[_], asset: Resolvable): Chaincode.Response = {
            context.store.put("k1", asset.resolve(clz))
            SuccessResponse()
        }

        override def resolveClassByName(name: String): Option[Class[_]] = if (name.equals("dummy")) Some(classOf[Dummy]) else throw new RuntimeException(s"Unknown type name $name")
    }

    case class Dummy(
        name: String,
        value: String
    )

    private val DummyAssetJson = "{\"name\":\"x\",\"value\":\"y\"}"
    private val DummyAssetJsonUtf8Bytes = DummyAssetJson.getBytes(StandardCharsets.UTF_8)

    test("init with typed args") {
        val api = mock(classOf[ChaincodeStub])
        when(api.getParameters).thenReturn(List("abc", "1", "2.2", "3.3", DummyAssetJson).asJava)

        assert(TEST_CONTRACT.init(api).getStatus == Chaincode.Response.Status.SUCCESS)

        verify(api).putState("p1", "\"abc\"".getBytes(StandardCharsets.UTF_8))
        verify(api).putState("p2", "1".getBytes(StandardCharsets.UTF_8))
        verify(api).putState("p3", "2.2".getBytes(StandardCharsets.UTF_8))
        verify(api).putState("p4", "3.3".getBytes(StandardCharsets.UTF_8))
        verify(api).putState(mkAssetKey("dummy", "p5"), DummyAssetJsonUtf8Bytes)
    }

    test("put dummy asset [raw]"){
        val api = mock(classOf[ChaincodeStub])
        when(api.getFunction).thenReturn("testRawAsset")
        when(api.getParameters).thenReturn(List("k1", "dummy", DummyAssetJson).asJava)

        val result = TEST_CONTRACT.invoke(api)

        assert(result.getStatus == Chaincode.Response.Status.SUCCESS)

        verify(api).putState(mkAssetKey("dummy", "k1"), DummyAssetJsonUtf8Bytes)
    }

    test("put dummy asset [oop]"){
        val api = mock(classOf[ChaincodeStub])
        when(api.getFunction).thenReturn("testPutAsset")
        when(api.getParameters).thenReturn(List("k1", DummyAssetJson).asJava)

        val result = TEST_CONTRACT.invoke(api)

        assert(result.getStatus == Chaincode.Response.Status.SUCCESS)

        verify(api).putState(mkAssetKey("dummy", "k1"), DummyAssetJsonUtf8Bytes)
    }

    def mkAssetKey(aType: String, key: String): String = new CompositeKey(aType, key).toString
}
