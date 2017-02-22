package controllers

import javax.inject._

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import play.api.data.Forms._
import play.api.data._
import play.api.mvc._
import uk.co.goblinoid.EmailActor._

import play.api.Play.current
import play.api.i18n.Messages.Implicits._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

@Singleton
class Application @Inject()(@Named("email-actor") emailActor: ActorRef)
                           (implicit ec: ExecutionContext) extends Controller {

  implicit val timeout = Timeout(5 seconds)

  val roles = Seq(
    "Head Of State",
    "UN Ambassador",
    "Diplomat",
    "General",
    "Scientist",
    "Alien"
  )

  val emailForm: Form[SendRegistrationEmail] = {
    val rowsAsData = (roles.indices map {role => s"roles[$role]"}) zip roles toMap

    Form(
      mapping(
        "name" -> optional(text),
        "email" -> email,
        "isFresher" -> boolean,
        "roles" -> seq(text)
      )(SendRegistrationEmail.apply)(SendRegistrationEmail.unapply)
    ).bind(rowsAsData).discardingErrors
  }

  def index = Action {
    implicit request =>
      Ok(views.html.index(emailForm, roles))
  }

  def sendEmail: Action[AnyContent] = Action.async(
  {
    implicit request =>
      // Bind the submission
      emailForm.bindFromRequest.fold(
        // If there was a validation error - just display the index page with errors
        formWithErrors => {
          Future.successful(
            BadRequest(views.html.index(formWithErrors, roles))
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
