package controllers

import jp.t2v.lab.play2.auth.LoginLogout
import play.api.Logger
import play.api.data.Form
import play.api.mvc.{Action, Controller}
import uk.co.goblinoid.auth.{Account, Guest, AuthConfig}

import play.api.data.Forms._

import scala.concurrent.ExecutionContext.Implicits.global

import play.api.Play.current
import play.api.i18n.Messages.Implicits._

/**
 *
 * Created by Jeff on 04/10/2015.
 */
class User extends Controller with LoginLogout with AuthConfig {

  /** Your application's login form.  Alter it to fit your application */
  val loginForm = Form[Account] {
    mapping("username" -> text, "password" -> text)(Account.authenticate)(u => Some(u.name, ""))
      .verifying("Invalid username or password", user => user.role != Guest)
  }

  /** Alter the login page action to suit your application. */
  def login = Action { implicit request =>
    Ok(views.html.user.login(loginForm))
  }

  /**
   * Return the `gotoLogoutSucceeded` method's result in the logout action.
   *
   * Since the `gotoLogoutSucceeded` returns `Future[Result]`,
   * you can add a procedure like the following.
   *
   * gotoLogoutSucceeded.map(_.flashing(
   * "success" -> "You've been logged out"
   * ))
   */
  def logout = Action.async { implicit request =>
    // do something...
    gotoLogoutSucceeded.map(_.flashing(
      "icon" -> "fa fa-check-circle",
      "type" -> "success",
      "message" -> "You have successfully logged out."
    ))
  }

  /**
   * Return the `gotoLoginSucceeded` method's result in the login action.
   *
   * Since the `gotoLoginSucceeded` returns `Future[Result]`,
   * you can add a procedure like the `gotoLogoutSucceeded`.
   */
  def authenticate = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors =>
        authenticationFailed(request).map(_.flashing(
          "icon" -> "fa fa-times-circle",
          "type" -> "alert",
          "message" -> "Username or password invalid."
        )),
      user =>
        gotoLoginSucceeded(user.name).map(_.flashing(
          "icon" -> "fa fa-check-circle",
          "type" -> "success",
          "message" -> "You have successfully logged in."
        ))
    )
  }

}
