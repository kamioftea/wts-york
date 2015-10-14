package uk.co.goblinoid

import _root_.play.api.Logger
import _root_.play.api.libs.mailer.{Email, MailerClient}
import akka.actor._

import scala.language.{reflectiveCalls, postfixOps}
import scala.util.{Failure, Success, Try}

import javax.inject._

object EmailActor {

  case class SendRegistrationEmail(name: Option[String], email: String, isFresher: Boolean, roles: Seq[String])

  sealed trait SendEmailResult

  case class SendEmailSuccess() extends SendEmailResult

  case class SendEmailFailure() extends SendEmailResult

}

/**
 *
 * Created by Jeff on 01/10/2015.
 */
class EmailActor @Inject()(mailerClient: MailerClient) extends Actor {

  import EmailActor._

  import _root_.play.api.Play.current

  def receive: Receive = {
    case SendRegistrationEmail(name, email_address, isFresher, preferences) =>
      val email = Email(
        s"Registration for WTS: $email_address",
        current.configuration.getString("email.from").getOrElse(""),
        current.configuration.getStringSeq("email.to").getOrElse(Seq()),
        // sends text, HTML or both...
        bodyText = Some(
          s"${name.getOrElse("Someone")} is interested in WTS.\n\nTheir email is: $email_address\n\n" +
          { if(isFresher)  s"They are a Fresher.\n\n" else "" } +
          s"Role Preferences: ${preferences mkString ", "}."
        )
      )

      Try {
        mailerClient.send(email)
      } match {
        case Success(_) => sender() ! SendEmailSuccess()
        case Failure(error) =>
          Logger.error(error.getMessage, error)
          sender() ! SendEmailFailure()
      }
  }
}
