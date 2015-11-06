package uk.co.goblinoid.auth


import controllers.routes
import jp.t2v.lab.play2.auth.{AuthConfig => BaseAuthConfig, CookieTokenAccessor}
import play.api.Logger
import play.api.mvc.{Results, Result, RequestHeader}

import scala.reflect._
import scala.concurrent.{Future, ExecutionContext}

/**
 * @see https://github.com/t2v/play2-auth
 */
trait AuthConfig extends BaseAuthConfig {

  /**
   * A type that is used to identify a user.
   * `String`, `Int`, `Long` and so on.
   */
  type Id = String

  /**
   * A type that represents a user in your application.
   * `User`, `Account` and so on.
   */
  type User = Account

  /**
   * A type that is defined by every action for authorization.
   * This sample uses the following trait:
   *
   * sealed trait Role
   * case object Administrator extends Role
   * case object NormalUser extends Role
   */
  type Authority = Role

  /**
   * A `ClassTag` is used to retrieve an id from the Cache API.
   * Use something like this:
   */
  val idTag: ClassTag[Id] = classTag[Id]

  /**
   * The session timeout in seconds
   */
  val sessionTimeoutInSeconds: Int = 3600

  val adminMatcher = "admin\\d*".r

  /**
   * A function that returns a `User` object from an `Id`.
   * You can alter the procedure to suit your application.
   */
  def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]] = Future.successful(
    id match {
      case adminMatcher() => Some(Account("Admin", Admin))
      case "guest" => Some(Account("Guest", Guest))
      case _ => None
    }
  )

  /**
   * Where to redirect the user after a successful login.
   */
  def loginSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    val uri =
      request.session.get("access_uri")
        .filterNot(_ == routes.User.login().url)
        .getOrElse(routes.Application.index().url)

    Future.successful(
      Results.Redirect(uri)
        .flashing(
          "icon" -> "fa fa-check-circle",
          "type" -> "success",
          "message" -> "You have successfully logged in.")
        .withSession(request.session - "access_uri")
    )
  }

  /**
   * Where to redirect the user after logging out
   */
  def logoutSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] =
    Future.successful(Results.Redirect(routes.User.login()))

  /**
   * If the user is not logged in and tries to access a protected resource then redirect them as follows:
   */
  def authenticationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    val access_uri =
      if (request.uri == routes.User.login().url
        || request.headers.get("X-Requested-With").contains("XMLHttpRequest")
        || request.method != "GET")
        // Redirected to login page, or is an XMLHttpRequest - keep previous, falling back to index.
        None
      else
        Some(request.uri)

    val response = Results.Redirect(routes.User.login())

    Future.successful(
      access_uri match {
        case Some(uri) => response.withSession("access_uri" -> uri)
        case None => response
      }
    )
  }

  /**
   * If authorization failed (usually incorrect password) redirect the user as follows:
   */
  override def authorizationFailed(request: RequestHeader, user: User, authority: Option[Authority])(implicit context: ExecutionContext): Future[Result] = {
    Future.successful(Results.Forbidden(views.html.error.forbidden(user)))
  }

  /**
   * A function that determines what `Authority` a user has.
   * You should alter this procedure to suit your application.
   */
  def authorize(user: User, authority: Authority)(implicit ctx: ExecutionContext): Future[Boolean] = Future.successful {
    (user.role, authority) match {
      case (Admin, _) => true
      case (RegisteredUser, RegisteredUser) => true
      case (_, Guest) => true
      case _ => false
    }
  }

  /**
   * (Optional)
   * You can custom SessionID Token handler.
   * Default implementation use Cookie.
   */
  override lazy val tokenAccessor = new CookieTokenAccessor(
    /*
     * Whether use the secure option or not use it in the cookie.
     */
    // The data available by logging on is not worth protecting with an SSL certificate
    cookieSecureOption = false,
    cookieMaxAge = Some(sessionTimeoutInSeconds)
  )

}
