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

import scala.concurrent.Future
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
        "name" -> optional(text),
        "email" -> email
      )(SendRegistrationEmail.apply)(SendRegistrationEmail.unapply)
    )
  }

  def index = Action {
    implicit request =>
      Ok(views.html.index(emailForm))
  }

  def sendEmail = Action.async(
  {
    implicit request =>
      // Bind the submission
      emailForm.bindFromRequest.fold(
        // If there was a validation error - just display the index page with errors
        formWithErrors => {
          Future.successful(
            BadRequest(views.html.index(formWithErrors))
          )
        },
        // If it bound successfully we have a SendRegistrationEmail message to send to the email actor
        sendRegistrationEmail => {
          for {
            result <- (emailActor ? sendRegistrationEmail).mapTo[SendEmailResult]
          } yield {
            // we're going to redirect back to the home message
            val redirect = Redirect(routes.Application.index())
            // with relevant flash data to indicate success or failure...
            result match {
              case SendEmailSuccess() =>
                redirect.flashing(
                  "icon" -> "fa fa-check-circle",
                  "type" -> "success",
                  "message" -> "Thanks! Your email address has been received. We'll be in touch."
                )
              case SendEmailFailure() =>
                redirect.flashing(
                  "icon" -> "fa fa-exclamation-triangle",
                  "type" -> "alert",
                  "message" -> "There was a problem registering your email. Please try emailing us, or contacting us on facebook."
                )
            }
          }
        }
      )
    }
  )
}