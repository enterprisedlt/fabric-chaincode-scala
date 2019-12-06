package org.enterprisedlt.fabric.contract

import java.nio.charset.StandardCharsets
import java.util

import org.enterprisedlt.fabric.contract.annotation.{ContractInit, ContractOperation}
import org.enterprisedlt.general.codecs._
import org.enterprisedlt.general.gson._
import org.hyperledger.fabric.shim.ledger.{CompositeKey, QueryResultsIterator}
import org.hyperledger.fabric.shim.{Chaincode, ChaincodeStub, ledger}
import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import scala.collection.JavaConverters._

/**
 * @author Alexey Polubelov
 */
@RunWith(classOf[JUnitRunner])
class ContractBaseTest extends FunSuite {

    private val NamesResolver = new TypeNameResolver() {
        override def resolveTypeByName(name: String): Class[_] = if ("dummy" equals name) classOf[Dummy] else throw new IllegalStateException(s"Unexpected class name: $name")

        override def resolveNameByType(clazz: Class[_]): String = "dummy"
    }

    val TEST_CONTRACT: ContractBase = new ContractBase(
        ContractCodecs(
            Utf8Codec(
                GsonCodec(gsonOptions = _.encodeTypes(typeFieldName = "#TYPE#", typeNamesResolver = NamesResolver))
            )
        )
    ) {

        @ContractInit
        def testInit(p1: String, p2: Int, p3: Float, p4: Double, asset: Dummy): Unit = {
            ContextHolder.get.store.put("p1", p1)
            ContextHolder.get.store.put("p2", p2)
            ContextHolder.get.store.put("p3", p3)
            ContextHolder.get.store.put("p4", p4)
            ContextHolder.get.store.put("p5", asset)
        }

        @ContractOperation
        def testPutAsset(key: String, asset: Dummy): Unit = {
            ContextHolder.get.store.put("k1", asset)
        }

        @ContractOperation
        def testGetAsset(key: String): ContractResponse = {
            ContextHolder.get.store.get[Dummy](key).map(Success(_)).getOrElse(Error(s"No asset for key: $key"))
        }

        @ContractOperation
        def testQueryAsset(query: String): ContractResponse = {
            Success(ContextHolder.get.store.query[Dummy](query).toArray)
        }
    }

    private val DummyAssetJson = s"""{"name":"x","value":"y","#TYPE#":"dummy"}"""
    private val DummyAssetJsonUtf8Bytes = DummyAssetJson.getBytes(StandardCharsets.UTF_8)

    test("init with typed args") {
        val api = mock(classOf[ChaincodeStub])
        when(api.getArgs).thenReturn(mkCCArgs("abc", "1", "2.2", "3.3", DummyAssetJson))

        val result = performAndLog(() => TEST_CONTRACT.init(api))
        assert(result.getStatus == Chaincode.Response.Status.SUCCESS)

        verify(api).putState(key("p1"), "abc".getBytes(StandardCharsets.UTF_8))
        verify(api).putState(key("p2"), "1".getBytes(StandardCharsets.UTF_8))
        verify(api).putState(key("p3"), "2.2".getBytes(StandardCharsets.UTF_8))
        verify(api).putState(key("p4"), "3.3".getBytes(StandardCharsets.UTF_8))
        verify(api).putState(mkAssetKey("Dummy", "p5"), DummyAssetJsonUtf8Bytes)
    }

    //    test("put dummy asset [raw]") {
    //        val api = mock(classOf[ChaincodeStub])
    //        when(api.getFunction).thenReturn("testRawAsset")
    //        when(api.getParameters).thenReturn(List("k1", "dummy", DummyAssetJson).asJava)
    //
    //        val result = performAndLog(() => TEST_CONTRACT.invoke(api))
    //
    //        assert(result.getStatus == Chaincode.Response.Status.SUCCESS)
    //
    //        verify(api).putState(mkAssetKey("dummy", "k1"), DummyAssetJsonUtf8Bytes)
    //    }

    test("put dummy asset") {
        val api = mock(classOf[ChaincodeStub])
        when(api.getFunction).thenReturn("testPutAsset")
        when(api.getArgs).thenReturn(mkCCArgs("k1", DummyAssetJson))

        val result = performAndLog(() => TEST_CONTRACT.invoke(api))

        assert(result.getStatus == Chaincode.Response.Status.SUCCESS)

        verify(api).putState(mkAssetKey("Dummy", "k1"), DummyAssetJsonUtf8Bytes)
    }

    test("get dummy asset success") {
        val api = mock(classOf[ChaincodeStub])
        when(api.getFunction).thenReturn("testGetAsset")
        when(api.getArgs).thenReturn(mkCCArgs("k1"))
        when(api.getState(mkAssetKey("Dummy", "k1"))).thenReturn(DummyAssetJsonUtf8Bytes)

        val result = performAndLog(() => TEST_CONTRACT.invoke(api))

        assert(result.getStatus == Chaincode.Response.Status.SUCCESS)
        assert(result.getPayload sameElements DummyAssetJsonUtf8Bytes)
    }

    test("get dummy asset error") {
        val api = mock(classOf[ChaincodeStub])
        when(api.getFunction).thenReturn("testGetAsset")
        when(api.getArgs).thenReturn(mkCCArgs("k1"))
        when(api.getState(mkAssetKey("Dummy", "k1"))).thenReturn(null)

        val result = performAndLog(() => TEST_CONTRACT.invoke(api))

        assert(result.getStatus == Chaincode.Response.Status.INTERNAL_SERVER_ERROR)
        assert(result.getMessage == "No asset for key: k1")
    }

    test("query dummy asset") {
        val api = mock(classOf[ChaincodeStub])
        when(api.getFunction).thenReturn("testQueryAsset")
        when(api.getArgs).thenReturn(mkCCArgs("test query"))
        when(api.getQueryResult("test query")).thenReturn(DummyResultsIterator)

        val result = performAndLog(() => TEST_CONTRACT.invoke(api))

        assert(result.getStatus == Chaincode.Response.Status.SUCCESS)
        assert(result.getPayload sameElements "[{\"key\":\"dummy\",\"value\":{\"name\":\"x\",\"value\":\"y\",\"#TYPE#\":\"dummy\"},\"#TYPE#\":\"dummy\"}]".getBytes(StandardCharsets.UTF_8))
    }

    def key(k: String): String = mkAssetKey("SIMPLE", k)

    def mkAssetKey(aType: String, key: String): String = new CompositeKey(aType, key).toString

    private def mkCCArgs(args: String*): util.List[Array[Byte]] =
        (new Array[Byte](0) +: args.map(_.getBytes(StandardCharsets.UTF_8))).asJava

    def performAndLog(f: () => Chaincode.Response): Chaincode.Response = {
        val result = f()
        if (result != null) {
            println(s"Status: ${result.getStatus}")
            println(s"Message: ${result.getMessage}")
            println(s"Payload ${
                if (result.getPayload == null) {
                    "null"
                } else {
                    new String(result.getPayload, StandardCharsets.UTF_8)
                }
            }")
        }
        result
    }

    object DummyResultsIterator extends QueryResultsIterator[ledger.KeyValue] {

        override def iterator(): util.Iterator[ledger.KeyValue] = Seq(DummyKeyValue.asInstanceOf[ledger.KeyValue]).toIterator.asJava

        override def close(): Unit = {}
    }

    object DummyKeyValue extends ledger.KeyValue {
        override def getKey: String = mkAssetKey("Dummy", "dummy")

        override def getValue: Array[Byte] = DummyAssetJsonUtf8Bytes

        override def getStringValue: String = DummyAssetJson
    }

}


case class Dummy(
    name: String,
    value: String
)
