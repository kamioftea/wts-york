package uk.co.goblinoid.util

import play.api.Configuration
import play.api.Play._
import play.api.libs.oauth.{RequestToken, ConsumerKey}

case class OAuthCredentials(consumerKey: ConsumerKey, requestToken: RequestToken)

object OAuthCredentials
{
  def fromConfig(configKey: String): Option[OAuthCredentials] = {
    def buildConsumerKey(config: Configuration): Option[ConsumerKey] = for {
      consumer_key <- config.getString("consumer_key")
      consumer_secret <- config.getString("consumer_secret")
    } yield ConsumerKey(consumer_key, consumer_secret)

    def buildRequestToken(config: Configuration): Option[RequestToken] = for {
      token <- config.getString("access_token")
      secret <- config.getString("access_secret")
    } yield RequestToken(token, secret)

    for {
      config <- current.configuration.getConfig(configKey)
      consumerKey <- buildConsumerKey(config)
      requestToken <- buildRequestToken(config)
    } yield OAuthCredentials(consumerKey, requestToken)
  }
}
