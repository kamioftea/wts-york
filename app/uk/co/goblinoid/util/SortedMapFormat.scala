package uk.co.goblinoid.util

import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.collection.immutable.SortedMap

/**
 *
 * Created by jeff on 23/09/2015.
 */
object SortedMapFormat {
  implicit def readsPair[T, U](implicit rT: Reads[T], rU: Reads[U]): Reads[(T, U)] = (
    (JsPath \ "k").read[T] and
      (JsPath \ "v").read[U]
    ).tupled

  implicit def writesPair[T, U](implicit wT: Writes[T], rU: Writes[U]): Writes[(T, U)] = (
    (JsPath \ "k").write[T] and
      (JsPath \ "v").write[U]
    ).tupled

  implicit def readsSortedMap[T,U](implicit r: Reads[Seq[(T, U)]], o: Ordering[T]): Reads[SortedMap[T, U]] = __.read[Seq[(T, U)]].map(_.foldLeft(SortedMap[T, U]())({ case (map, (k, v)) => map.updated(k, v) }))

  implicit def writesSortedMap[T,U](implicit w: Writes[Seq[(T, U)]]): Writes[SortedMap[T, U]] = Writes(map => w.writes(map.toSeq))
}
