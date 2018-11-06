package com.github.apolubelov.fabric.contract.store

import com.github.apolubelov.fabric.contract.Util
import com.github.apolubelov.fabric.contract.codec.BinaryCodec
import org.hyperledger.fabric.shim.ledger.CompositeKey
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.reflect.{ClassTag, classTag}

/*
 * @author Alexey Polubelov
 */
class Store(
    stateAccess: RawStateAccess,
    codec: BinaryCodec
) {
    private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

    def put[T](key: String, value: T): Unit = {
        logger.trace(s"PUT: $key $value")
        stateAccess.putState(wrapKey(value.getClass, key), codec.encode(value))
    }

    def get[T: ClassTag](key: String): Option[T] =
        get(key, classTag[T].runtimeClass.asInstanceOf[Class[T]])

    def get[T](key: String, clz: Class[T]): Option[T] = {
        // getState can return some non null value which in turn can be decoded to null...
        val result = Option(stateAccess.getState(wrapKey(clz, key)))
          .flatMap(x => Option(codec.decode(x, clz)))
        logger.trace(s"GET: $key $result")
        result
    }

    def del[T: ClassTag](key: String): Unit =
        del(key, classTag[T].runtimeClass.asInstanceOf[Class[T]])

    def del[T](key: String, clz: Class[T]): Unit = {
        logger.trace(s"DEL: $key")
        stateAccess.delState(wrapKey(clz, key))
    }

    def list[T <: AnyRef : ClassTag]: Iterable[(String, T)] =
        list(classTag[T].runtimeClass.asInstanceOf[Class[T]])

    def list[T <: AnyRef](clz: Class[T]): Iterable[(String, T)] = {
        logger.trace(s"LIST[${clz.getSimpleName}]")
        stateAccess
          .getStateByPartialCompositeKey(
              new CompositeKey(Util.camelCase(clz.getSimpleName)))
          .asScala.map(kv => (unwrapKey(clz, kv.getKey), codec.decode(kv.getValue, clz)))
    }


    private[this] def wrapKey(clz: Class[_], key: String): String = clz match {
        case x if isSimpleType(x) => key
        case o => new CompositeKey(Util.camelCase(o.getSimpleName), key).toString
    }

    private[this] def unwrapKey(clz: Class[_], key: String): String = clz match {
        case x if isSimpleType(x) => key
        case o => CompositeKey.parseCompositeKey(key).getAttributes.get(0) // TODO: make it more smart
    }

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