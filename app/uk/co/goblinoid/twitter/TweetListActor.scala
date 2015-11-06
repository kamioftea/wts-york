package uk.co.goblinoid.twitter

import akka.actor._
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.ws.WS
import uk.co.goblinoid.util.OAuthCredentials
import scala.concurrent.duration._

import play.api.Play.current

import scala.concurrent.Future

object TweetListActor {
  def props() = Props(new TweetListActor)

  sealed trait TweetListMessages

  case object GetTweets extends TweetListMessages

  case class SendTweets(tweets: Seq[Tweet]) extends TweetListMessages

  case class RefreshTweets(tweets: Seq[Tweet]) extends TweetListMessages

  case object RefreshTweetsTick extends TweetListMessages
}

/**
  *
  * Created by Jeff on 05/11/2015.
  */
class TweetListActor extends Actor
{
  import TweetListActor._
  import context._

  private def getTweets = OAuthCredentials.fromConfig("twitter") match {
    case Some(twitterOAuth) =>
      WS.url("https://api.twitter.com/1.1/statuses/user_timeline.json?count=5&exclude_replies=true")
        .sign(OAuthCalculator(twitterOAuth.consumerKey, twitterOAuth.requestToken))
        .withRequestTimeout(2000)
        .get()
        .map(result => Twitter.toTweets(result.json))
    case _ => Future {
      Seq()
    }
  }

  val ticker = system.scheduler.schedule(0.second, 10.second, self, RefreshTweetsTick)

  def buildReceive(tweets: Seq[Tweet]): Receive = {
    case GetTweets =>
      sender() ! SendTweets(tweets)
    case RefreshTweetsTick =>
      getTweets.onSuccess { case newTweets => self ! RefreshTweets(newTweets) }
    case RefreshTweets(newTweets) =>
      become(buildReceive(newTweets))
  }

  def receive = buildReceive(Seq())
}
