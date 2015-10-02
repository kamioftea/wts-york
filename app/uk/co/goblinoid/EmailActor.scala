package uk.co.goblinoid

import akka.actor._
import play.api.libs.mailer.Email

import play.api.libs.mailer._

import scala.language.reflectiveCalls
import scala.util.{Failure, Success, Try}

object EmailActor {
  def props(mailerClient: MailerClient) = Props(new EmailActor(mailerClient))

  case class SendRegistrationEmail(email: String)

  sealed trait SendEmailResult

  case class SendEmailSuccess() extends SendEmailResult

  case class SendEmailFailure() extends SendEmailResult

}

/**
 *
 * Created by Jeff on 01/10/2015.
 */
// todo: Create mailer client from config
class EmailActor(mailerClient: MailerClient) extends Actor {

  import EmailActor._

  import play.api.Play.current

  def receive: Receive = {
    case SendRegistrationEmail(email_address) =>
      val email = Email(
        "Registration for WTS",
        current.configuration.getString("email.from").getOrElse(""),
        current.configuration.getStringSeq("email.to").getOrElse(Seq()),
        // sends text, HTML or both...
        bodyText = Some(s"$email_address is interested in WTS")
      )

      Try {
        mailerClient.send(email)
      } match {
        case Success(_) => sender() ! SendEmailSuccess()
        case Failure(error) => sender() ! SendEmailFailure()
      }
  }
}
