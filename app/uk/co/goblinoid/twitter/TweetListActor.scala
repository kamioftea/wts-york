package uk.co.goblinoid.twitter

import akka.actor._
import play.api.Play.current
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.ws.WS
import uk.co.goblinoid.util.OAuthCredentials

import scala.concurrent.Future
import scala.concurrent.duration._

object TweetListActor {
  def props(screenName: Option[String]) = Props(new TweetListActor(screenName))

  sealed trait TweetListMessages

  case class GetTweets(count: Int = 5) extends TweetListMessages

  case class SendTweets(tweets: Seq[Tweet]) extends TweetListMessages

  case class RefreshTweets(tweets: Seq[Tweet]) extends TweetListMessages

  case object RefreshTweetsTick extends TweetListMessages

  val USER_TIMELINE_URL = "https://api.twitter.com/1.1/statuses/user_timeline.json"

  val TWEET_FETCH_AMOUNT = 20
}

/**
  *
  * Created by Jeff on 05/11/2015.
  */
class TweetListActor(screenName: Option[String]) extends Actor
{
  import TweetListActor._
  import context._

  val params: Map[String, String] = Map(
    "count" -> TWEET_FETCH_AMOUNT.toString
  ) ++ ( screenName map ("screen_name" -> _))

  private def getTweets = OAuthCredentials.fromConfig("twitter") match {
    case Some(twitterOAuth) =>

      WS.url(USER_TIMELINE_URL)
        .withQueryString(params.toList: _*)
        .sign(OAuthCalculator(twitterOAuth.consumerKey, twitterOAuth.requestToken))
        .withRequestTimeout(2000)
        .get()
        .map(result => Twitter.toTweets(result.json).filter(t => t.posted.getYear == 2017))

    case _ => Future {
      Seq()
    }
  }

  system.scheduler.schedule(0.second, 10.second, self, RefreshTweetsTick)

  def buildReceive(tweets: Seq[Tweet]): Receive = {
    case GetTweets(count) =>
      sender() ! SendTweets(tweets.take(count))
    case RefreshTweetsTick =>
      getTweets.onSuccess { case newTweets => self ! RefreshTweets(newTweets) }
    case RefreshTweets(newTweets) =>
      become(buildReceive(newTweets))
  }

  def receive: Receive = buildReceive(Seq())


}
