package uk.co.goblinoid.twitter

import java.time.{LocalDate, LocalTime, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

import play.api.libs.functional.syntax._
import play.api.libs.json._

object BigIntFormat {
  implicit val bigIntReads: Reads[BigInt] =
    __.read[String].map(BigInt(_))

  implicit val bigIntWrites: Writes[BigInt] = Writes[BigInt] {i => JsString(i.toString)}
}

object Twitter {
  import BigIntFormat._

  val TWITTER_TIME_FORMAT = "EEE MMM dd HH:mm:ss Z yyyy"

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

object TwitterInternalFormat {
  import BigIntFormat._

  implicit val writesTweet: Writes[Tweet] = Twitter.writesTweet

  implicit val zonedDateTimeReads: Reads[ZonedDateTime] =
    __.read[String].map(ts => ZonedDateTime.of(LocalDate.now(), LocalTime.parse(ts, DateTimeFormatter.ofPattern("H:mm:ss")), ZoneId.systemDefault()))

  implicit val readsTweet: Reads[Tweet] = (
    (JsPath \ "id").read[BigInt] and
      (JsPath \ "text").read[String] and
      (JsPath \ "posted").read[ZonedDateTime]
  )(Tweet.apply _)

}

case class Tweet(id: BigInt, text: String, posted: ZonedDateTime)
