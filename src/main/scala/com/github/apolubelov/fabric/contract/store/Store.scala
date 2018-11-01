package com.github.apolubelov.fabric.contract.store

import com.github.apolubelov.fabric.contract.Util
import com.github.apolubelov.fabric.contract.codec.BinaryCodec
import org.apache.commons.logging.LogFactory
import org.hyperledger.fabric.shim.ledger.CompositeKey

import scala.collection.JavaConverters._
import scala.reflect.{ClassTag, classTag}

/*
 * @author Alexey Polubelov
 */
class Store(
    stateAccess: RawStateAccess
) {
    private[this] val logger = LogFactory.getLog(classOf[Store])

    def put[T](key: String, value: T)(implicit codec: BinaryCodec): Unit = {
        stateAccess.putState(wrapKey(value.getClass, key), codec.encode(value))
    }

    def get[T: ClassTag](key: String)(implicit codec: BinaryCodec): Option[T] = {
        val clz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
        get(key, clz)
    }

    def get[T](key: String, clz: Class[T])(implicit codec: BinaryCodec): Option[T] = {
        Option(stateAccess.getState(wrapKey(clz, key)))
          .map(codec.decode(_, clz))
    }

    def del[T: ClassTag](key: String)(implicit codec: BinaryCodec): Unit = {
        val clz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
        del(key, clz)
    }

    def del[T](key: String, clz: Class[T])(implicit codec: BinaryCodec): Unit = {
        stateAccess.delState(wrapKey(clz, key))
    }

    def list[T <: AnyRef : ClassTag](implicit codec: BinaryCodec): Iterable[(String, T)] = {
        val clz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
        list(clz)
    }

    def list[T <: AnyRef](clz: Class[T])(implicit codec: BinaryCodec): Iterable[(String, T)] = {
        val key = new CompositeKey(Util.camelCase(clz.getSimpleName))
        logger.debug(s"list: [${clz.getCanonicalName}] key: $key")
        stateAccess.getStateByPartialCompositeKey(key).asScala.map(kv =>
            (unwrapKey(clz, kv.getKey), codec.decode(kv.getValue, clz))
        )
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