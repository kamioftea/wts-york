package uk.co.goblinoid

import _root_.play.api.libs.mailer.{Email, MailerClient}
import akka.actor._

import scala.language.reflectiveCalls
import scala.util.{Failure, Success, Try}

object EmailActor {
  def props(mailerClient: MailerClient) = Props(new EmailActor(mailerClient))

  case class SendRegistrationEmail(name: Option[String], email: String)

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

  import _root_.play.api.Play.current

  def receive: Receive = {
    case SendRegistrationEmail(name, email_address) =>
      val email = Email(
        s"Registration for WTS: $email_address",
        current.configuration.getString("email.from").getOrElse(""),
        current.configuration.getStringSeq("email.to").getOrElse(Seq()),
        // sends text, HTML or both...
        bodyText = Some(s"${name.getOrElse("Someone")} is interested in WTS.\n\nTheir email is: $email_address")
      )

      Try {
        mailerClient.send(email)
      } match {
        case Success(_) => sender() ! SendEmailSuccess()
        case Failure(error) => sender() ! SendEmailFailure()
      }
  }
}
