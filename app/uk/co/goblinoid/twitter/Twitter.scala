package uk.co.goblinoid.twitter

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

import play.api.libs.functional.syntax._
import play.api.libs.json._


/**
 *
 * Created by jeff on 22/09/2015.
 */
object Twitter {
  val TWITTER_TIME_FORMAT = "EEE MMM dd HH:mm:ss Z yyyy"

  implicit val bigIntReads: Reads[BigInt] =
    __.read[String].map(BigInt(_))

  implicit val bigIntWrites: Writes[BigInt] = Writes[BigInt] {i => JsString(i.toString)}

  implicit val zonedDateTimeReads: Reads[ZonedDateTime] =
    // Parses e.g. "Thu Aug 20 11:26:20 +0000 2015" => ZonedDateTime
    __.read[String].map(ZonedDateTime.parse(_, DateTimeFormatter.ofPattern(TWITTER_TIME_FORMAT)))

  implicit val tweetReads: Reads[Tweet] = (
    (JsPath \ "id_str").read[BigInt] and
    (JsPath \ "text").read[String] and
    (JsPath \ "created_at").read[ZonedDateTime]
    )(Tweet.apply _)

  def toTweets(json: JsValue): Seq[Tweet] = {
    json.validate[Seq[Tweet]].getOrElse(Seq())
  }

  implicit val writesTweet: Writes[Tweet] = Writes[Tweet] {tweet => Json.obj(
    "id" -> JsString(tweet.id.toString()),
    "text" -> JsString(tweet.text),
    "posted" -> JsString(tweet.posted.format(DateTimeFormatter.ofPattern("H:mm:ss").withZone(ZoneId.systemDefault())))
  )}
}

case class Tweet(id: BigInt, text: String, posted: ZonedDateTime)
