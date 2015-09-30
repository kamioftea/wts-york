package controllers


import java.nio.file.Paths
import javax.inject.Singleton
import javax.inject._

import akka.util.Timeout
import play.api.Play.current
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.ws.WS
import play.api.mvc._

import uk.co.goblinoid.{GameState, GameActor}
import uk.co.goblinoid.twitter.{Tweet, Twitter}
import uk.co.goblinoid.util.OAuthCredentials

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.actor._
import akka.pattern.ask
import scala.concurrent.duration._
import scala.language.postfixOps

import play.api.data._
import play.api.data.Forms._

import play.api.i18n.Messages.Implicits._

@Singleton
class Application @Inject()(system: ActorSystem) extends Controller {

  implicit val timeout = Timeout(1 second)

  import GameActor._

  val filePath = Paths.get(current.configuration.getString("game.filepath").getOrElse("default-game.json"))

  val gameActor = system.actorOf(GameActor.props(filePath), "game-actor")

  def index = Action {
    Ok(views.html.index())
  }

  def gameState = Action.async {
    for {
      gameState <- getGameState
      tweets <- getTweets
    } yield {
      Ok(views.html.gameState(gameState, tweets))
    }
  }

  def editGameState = Action.async {
    getGameState.map {
      case gameState =>
        Ok(views.html.editGameState(
          gameState,
          terrorForm.fill(TerrorUpdate(gameState.terrorRank)),
          prForm
        ))
    }
  }

  def getGameState: Future[GameState] = {
    (gameActor ? GetGameState()).mapTo[GameState]
  }

  def getTweets: Future[Seq[Tweet]] = {
    OAuthCredentials.fromConfig("twitter") match {
      case Some(twitterOAuth) =>
        WS.url("https://api.twitter.com/1.1/statuses/user_timeline.json?count=10&exclude_replies=true")
          .sign(OAuthCalculator(twitterOAuth.consumerKey, twitterOAuth.requestToken))
          .withRequestTimeout(2000)
          .get()
          .map(result => Twitter.toTweets(result.json))
      case _ => Future {
        Seq()
      }
    }
  }

  val terrorForm: Form[TerrorUpdate] = {
    Form(
      mapping(
        "terror" -> number(min = 0, max = 250)
      )(TerrorUpdate.apply)(TerrorUpdate.unapply)
    )
  }

  def terror() = Action.async {
    for {
      gameState <- getGameState
    } yield {
      Ok(views.html.status.worldTerror(gameState.terrorRank))
    }
  }

  def updateTerror() = Action(parse.form(terrorForm)) {
    implicit request =>
      val terrorUpdate = request.body
      gameActor ! terrorUpdate
      Redirect(routes.Application.editGameState())
  }

  val prForm: Form[PrUpdate] = {
    Form(
      mapping(
        "country" -> text(),
        "pr" -> number(min=1, max=8)
      )(PrUpdate.apply)(PrUpdate.unapply)
    )
  }

  def updatePr() = Action(parse.form(prForm)) {
    implicit request =>
      val prUpdate = request.body
      gameActor ! prUpdate
      Redirect(routes.Application.editGameState())
  }

  def reset() = Action {
    gameActor ! Reset()
    Redirect(routes.Application.editGameState())
  }
}
