package controllers


import javax.inject._

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import play.api.data.Forms._
import play.api.data._
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import uk.co.goblinoid.EmailActor._
import uk.co.goblinoid.EmailActor

import play.api.Play.current
import play.api.i18n.Messages.Implicits._

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class Application @Inject()(system: ActorSystem, mailerClient: MailerClient) extends Controller {

  implicit val timeout = Timeout(5 second)
  val emailActor = system.actorOf(EmailActor.props(mailerClient), "email-actor")

  val emailForm: Form[SendRegistrationEmail] = {
    Form(
      mapping(
        "email" -> email
      )(SendRegistrationEmail.apply)(SendRegistrationEmail.unapply)
    )
  }

  def index = Action {
    implicit request =>
      Ok(views.html.index(emailForm))
  }

  def sendEmail = Action.async(parse.form(emailForm)) {
    implicit request =>
      val sendRegistrationEmail = request.body
      for {
        result <- (emailActor ? sendRegistrationEmail).mapTo[SendEmailResult]
      } yield {
        val redirect = Redirect(routes.Application.index())
        result match {
          case SendEmailSuccess() =>
            redirect.flashing(
              "type" -> "success",
              "message" -> "Thanks! Your email address has been received. We'll be in touch."
            )
          case SendEmailFailure() =>
            redirect.flashing(
              "type" -> "alert",
              "message" -> "There was a problem registering your email. Please try emailing us, or contacting us on facebook."
            )
        }
      }
  }

}
