package com.github.apolubelov.fabric.contract.codec

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
            enrichWithTypeOverride(v)
            codec.toJson(v, jsonWriter)
        }

        private def enrichWithTypeOverride(value: JsonElement): Unit =
            Option(value)
              .filter(_.isJsonObject)
              .map(_.getAsJsonObject)
              .foreach { jo =>
                  jo.addProperty(
                      typeFieldName,
                      typeNamesResolver.resolveNameByType(typeToken.getType.asInstanceOf[Class[_]]))
              }

        override def read(jsonReader: JsonReader): T = {
            val json: JsonElement = new JsonParser().parse(jsonReader)
            findTypeOverride(json).flatMap { typeOverride =>
                resolveObjectInstance(typeOverride).orElse {
                    Option(codec.getAdapter(TypeToken.get(typeOverride)))
                      .map { custom =>
                          read(json, jsonReader, custom).asInstanceOf[T]
                      }
                }
            } getOrElse read(json, jsonReader, nextTypeAdapter)
        }

        private def findTypeOverride(json: JsonElement): Option[Class[_]] =
            Option(json)
              .filter(_.isJsonObject)
              .flatMap(jo => Option(jo.getAsJsonObject.get(typeFieldName)))
              .map(_.getAsString)
              .map(typeNamesResolver.resolveTypeByName)
              .filter(realType => typeToken.getType.getTypeName != realType.getCanonicalName)

        private def resolveObjectInstance(typeOverride: Class[_]): Option[T] =
            Option(typeOverride)
              .filter(_.getSimpleName.endsWith("$"))
              .flatMap { realType =>
                  Try(
                      realType.getField("MODULE$").get(realType).asInstanceOf[T]
                  ).toOption
              }

        private def toJsonElement(jsonWriter: JsonWriter, value: T): JsonElement = {
            val jsonTreeWriter = new JsonTreeWriter
            jsonTreeWriter.setLenient(jsonWriter.isLenient)
            jsonTreeWriter.setHtmlSafe(jsonWriter.isHtmlSafe)
            jsonTreeWriter.setSerializeNulls(jsonWriter.getSerializeNulls)
            nextTypeAdapter.write(jsonTreeWriter, value)
            jsonTreeWriter.get()
        }

        private def read[X](json: JsonElement, jsonReader: JsonReader, adapter: TypeAdapter[X]): X = {
            val tr = new JsonTreeReader(json)
            tr.setLenient(jsonReader.isLenient)
            adapter.read(tr)
        }
    }
}