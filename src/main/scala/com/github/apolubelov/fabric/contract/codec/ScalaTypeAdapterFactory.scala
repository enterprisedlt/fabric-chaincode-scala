package com.github.apolubelov.fabric.contract.codec

import java.lang.reflect.ParameterizedType

import com.google.gson._
import com.google.gson.internal.bind.{JsonTreeReader, JsonTreeWriter}
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.{JsonReader, JsonWriter}

import scala.util.Try

/**
  * @author Alexey Polubelov
  */
class ScalaTypeAdapterFactory(
    typeFieldName: String,
    typeNamesResolver: TypeNameResolver
) extends TypeAdapterFactory {
    override def create[T](codec: Gson, typeToken: TypeToken[T]): TypeAdapter[T] =
        new ScalaTypeAdapter(codec, typeToken, codec.getDelegateAdapter(this, typeToken))

    class ScalaTypeAdapter[T](codec: Gson, typeToken: TypeToken[T], nextTypeAdapter: TypeAdapter[T])
      extends TypeAdapter[T] {

        override def write(jsonWriter: JsonWriter, value: T): Unit = {
            val v = toJsonElement(jsonWriter, value)
            codec.toJson(v, jsonWriter)
        }

        private def toJsonElement(jsonWriter: JsonWriter, value: T): JsonElement =
            value match {
                case x if x == None || x == null => JsonNull.INSTANCE
                case Some(x) =>
                    val jsonTreeWriter = mkTreeWriter(jsonWriter)
                    codec.getAdapter(clazz(x)).write(jsonTreeWriter, x)
                    enrichWithTypeOverride(jsonTreeWriter.get(), x)
                case _ =>
                    val jsonTreeWriter = mkTreeWriter(jsonWriter)
                    codec.getDelegateAdapter(ScalaTypeAdapterFactory.this, TypeToken.get(clazz(value)))
                      .write(jsonTreeWriter, value)
                    enrichWithTypeOverride(jsonTreeWriter.get(), value)
            }

        private def mkTreeWriter(jsonWriter: JsonWriter): JsonTreeWriter = {
            val jsonTreeWriter = new JsonTreeWriter
            jsonTreeWriter.setLenient(jsonWriter.isLenient)
            jsonTreeWriter.setHtmlSafe(jsonWriter.isHtmlSafe)
            jsonTreeWriter.setSerializeNulls(jsonWriter.getSerializeNulls)
            jsonTreeWriter
        }

        private def enrichWithTypeOverride[X](jsonValue: JsonElement, value: X): JsonElement =
            Option(jsonValue)
              .filter(_.isJsonObject)
              .map(_.getAsJsonObject)
              .map { jo =>
                  jo.addProperty(typeFieldName, typeNamesResolver.resolveNameByType(value.getClass))
                  jo
              } getOrElse jsonValue

        override def read(jsonReader: JsonReader): T = {
            val json: JsonElement = new JsonParser().parse(jsonReader)
            typeToken.getType match {
                case pt: ParameterizedType if pt.getRawType == classOf[Option[_]] =>
                    val actualType = pt.getActualTypeArguments()(0)
                    val next = codec.getAdapter(TypeToken.get(actualType))
                    Option(readX(jsonReader, json, next)).asInstanceOf[T]
                case _ =>
                    readX(jsonReader, json, nextTypeAdapter)
            }
        }

        private def readX[X](jsonReader: JsonReader, json: JsonElement, next: TypeAdapter[X]): X =
            findTypeOverride(json).flatMap { typeOverride =>
                resolveObjectInstance(typeOverride).orElse {
                    Option(codec.getAdapter(TypeToken.get(typeOverride)))
                      .map { custom =>
                          readJson(json, jsonReader, custom)
                      }
                }.map(_.asInstanceOf[X])
            } getOrElse readJson(json, jsonReader, next)


        private def findTypeOverride(json: JsonElement): Option[Class[_]] =
            Option(json)
              .filter(_.isJsonObject)
              .flatMap(jo => Option(jo.getAsJsonObject.get(typeFieldName)))
              .map(_.getAsString)
              .map(typeNamesResolver.resolveTypeByName)
              .filter(realType => typeToken.getType.getTypeName != realType.getCanonicalName)

        private def resolveObjectInstance[X](typeOverride: Class[X]): Option[X] =
            Option(typeOverride)
              .filter(_.getSimpleName.endsWith("$"))
              .flatMap { realType =>
                  Try(
                      realType.getField("MODULE$").get(realType).asInstanceOf[X]
                  ).toOption
              }

        private def readJson[X](json: JsonElement, jsonReader: JsonReader, adapter: TypeAdapter[X]): X = {
            val tr = new JsonTreeReader(json)
            tr.setLenient(jsonReader.isLenient)
            adapter.read(tr)
        }

        private def clazz[X](v: X): Class[X] = v.getClass.asInstanceOf[Class[X]]
    }

}