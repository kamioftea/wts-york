package uk.co.goblinoid.playutils

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import uk.co.goblinoid.EmailActor

/**
 * Module for providing Guice injections
 */
class WtsModule extends AbstractModule with AkkaGuiceSupport {
  def configure() = {
    bindActor[EmailActor]("email-actor")
  }
}
