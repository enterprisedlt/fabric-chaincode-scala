package org.enterprisedlt.fabric.contract.store

import org.enterprisedlt.fabric.contract.codec.BinaryCodec
import org.hyperledger.fabric.shim.ledger
import org.hyperledger.fabric.shim.ledger.{CompositeKey, QueryResultsIterator}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.reflect.{ClassTag, classTag}

/**
  * @author Alexey Polubelov
  */
class Store(
    stateAccess: RawStateAccess,
    codec: BinaryCodec,
    simpleTypesPartitionName: String
) {
    private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

    def put[T](key: Key, value: T): Unit = {
        logger.trace(s"PUT: $key $value")
        stateAccess.putState(wrapKey(value.getClass, key).toString, codec.encode(value))
    }

    def get[T: ClassTag](key: Key): Option[T] =
        get(key, classTag[T].runtimeClass.asInstanceOf[Class[T]])

    def get[T](key: Key, clz: Class[T]): Option[T] = {
        // getState can return some non null value which in turn can be decoded to null...
        val result = Option(stateAccess.getState(wrapKey(clz, key).toString))
          .flatMap(x => Option(codec.decode(x, clz)))
        logger.trace(s"GET: $key $result")
        result
    }

    def del[T: ClassTag](key: Key): Unit =
        del(key, classTag[T].runtimeClass.asInstanceOf[Class[T]])

    def del[T](key: Key, clz: Class[T]): Unit = {
        logger.trace(s"DEL: $key")
        stateAccess.delState(wrapKey(clz, key).toString)
    }

    def list[T <: AnyRef : ClassTag]: Iterable[KeyValue[T]] =
        list(classTag[T].runtimeClass.asInstanceOf[Class[T]])

    def list[T <: AnyRef : ClassTag](key: Key): Iterable[KeyValue[T]] =
        list(classTag[T].runtimeClass.asInstanceOf[Class[T]], key)

    def list[T <: AnyRef](clz: Class[T]): Iterable[KeyValue[T]] = {
        list(clz, Key.empty)
    }

    def list[T <: AnyRef](clz: Class[T], key: Key): Iterable[KeyValue[T]] = {
        logger.trace(s"LIST (${clz.getSimpleName}, $key)")
        convertKeyValue(stateAccess.getStateByPartialCompositeKey(wrapKey(clz, key)), clz)
    }

    def listKeys[T <: AnyRef](clz: Class[T], key: Key): Iterable[String] = {
        logger.trace(s"LIST KEYS (${clz.getSimpleName}, $key)")
        stateAccess
          .getStateByPartialCompositeKey(wrapKey(clz, key))
          .asScala.map(kv => unwrapKey(clz, kv.getKey))
    }

    def query[T: ClassTag](q: String): Iterable[KeyValue[T]] = {
        query(classTag[T].runtimeClass.asInstanceOf[Class[T]], q)
    }

    def query[T](clz: Class[T], query: String): Iterable[KeyValue[T]] = {
        convertKeyValue(stateAccess.queryState(query), clz)
    }

    private[this] def convertKeyValue[T](v: QueryResultsIterator[ledger.KeyValue], clz: Class[T]): Iterable[KeyValue[T]] =
        v.asScala.map(kv => KeyValue(unwrapKey(clz, kv.getKey), codec.decode(kv.getValue, clz)))

    private[this] def wrapKey(clz: Class[_], key: Key): CompositeKey =
        new CompositeKey(
            clz match {
                case x if isSimpleType(x) => simpleTypesPartitionName
                case o => o.getSimpleName
            },
            key.parts: _*
        )

    private[this] def unwrapKey(clz: Class[_], key: String): String =
        //TODO: Key(CompositeKey.parseCompositeKey(key).getAttributes.asScala:_*)
        CompositeKey.parseCompositeKey(key).getAttributes.asScala.mkString(CompositeKey.NAMESPACE)

    private[this] def isSimpleType(clz: Class[_]): Boolean = {
        clz.isPrimitive ||
          classOf[String].equals(clz) ||
          classOf[java.lang.Boolean].equals(clz) ||
          classOf[java.lang.Character].equals(clz) ||
          classOf[java.lang.Byte].equals(clz) ||
          classOf[java.lang.Short].equals(clz) ||
          classOf[java.lang.Integer].equals(clz) ||
          classOf[java.lang.Long].equals(clz) ||
          classOf[java.lang.Float].equals(clz) ||
          classOf[java.lang.Double].equals(clz)
    }
}