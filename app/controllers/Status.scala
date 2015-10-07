package controllers

import java.nio.file.Paths
import javax.inject._

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import jp.t2v.lab.play2.auth.AuthElement
import play.api.Play.current
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Messages.Implicits._
import play.api.libs.json._
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.ws.WS
import play.api.mvc._
import uk.co.goblinoid.auth.{Admin, AuthConfig}
import uk.co.goblinoid.twitter.{Tweet, Twitter}
import uk.co.goblinoid.util.OAuthCredentials
import uk.co.goblinoid.{GameActor, GameState}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

@Singleton
class Status @Inject()(system: ActorSystem) extends Controller with AuthElement with AuthConfig {

  implicit val timeout = Timeout(1 second)

  import GameActor._

  val filePath = Paths.get(current.configuration.getString("game.filepath").getOrElse("default-game.json"))

  val gameActor = system.actorOf(GameActor.props(filePath), "game-actor")

  def index = AsyncStack(AuthorityKey -> Admin) { _ =>
    for {
      gameState <- getGameState
      tweets <- getTweets
    } yield {
      Ok(views.html.gameState(gameState, tweets))
    }
  }

  def buildGameStateJson(state: GameState) = Json.obj(
    "terrorLevel" -> state.terrorStep(-90, 90, 25),
    "countryPRs" -> state.countryPRs.mapValues(_.pr)
  )

  def gameState = AsyncStack(AuthorityKey -> Admin) { _ =>
    getGameState map { gameState =>
      val json = buildGameStateJson(gameState)

      Ok(json)
    }
  }

  def editGameState = AsyncStack(AuthorityKey -> Admin) { _ =>
    getGameState map { gameState =>
        Ok(views.html.editGameState(
          gameState,
          terrorForm.fill(TerrorUpdate(gameState.terrorLevel)),
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

  def updateTerror() = StackAction(AuthorityKey -> Admin) { request =>
    terrorForm.bindFromRequest()(request).value.foreach(
      terrorUpdate => gameActor ! terrorUpdate
    )

    Redirect(routes.Status.editGameState())
  }

  val prForm: Form[PrUpdate] = {
    Form(
      mapping(
        "country" -> text(),
        "pr" -> number(min=1, max=8)
      )(PrUpdate.apply)(PrUpdate.unapply)
    )
  }

  def updatePr() = StackAction(AuthorityKey -> Admin) { request =>
    prForm.bindFromRequest()(request).value.foreach(
      prUpdate => gameActor ! prUpdate
    )

    Redirect(routes.Status.editGameState())
  }

  def reset() = StackAction(AuthorityKey -> Admin) { request =>
    gameActor ! Reset()
    Redirect(routes.Status.editGameState())
  }
}
