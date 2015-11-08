package controllers

import java.nio.file.Paths
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
import play.api.mvc._
import uk.co.goblinoid.auth.{Admin, AuthConfig}
import uk.co.goblinoid.twitter.{TweetListActor, Tweet}
import uk.co.goblinoid.{GameActor, GameState}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

@Singleton
class Status @Inject()(system: ActorSystem) extends Controller with AuthElement with AuthConfig {

  implicit val timeout = Timeout(1 second)

  import GameActor._
  import uk.co.goblinoid.twitter.TweetListActor._

  val filePath = Paths.get(current.configuration.getString("game.filepath").getOrElse("default-game.json"))
  val gameActor = system.actorOf(GameActor.props(filePath), "game-actor")

  val screenName = current.configuration.getString("twitter.screen_name")
  val tweetActor = system.actorOf(TweetListActor.props(screenName), "tweet-actor")

  def index = Action.async { _ =>
    for {
      gameState <- getGameState
      tweets <- getTweets
    } yield {
      Ok(views.html.gameState(gameState, tweets))
    }
  }

  def buildGameStateJson(state: GameState) = {
    val activities = state.phase.activities map {
      a => a.group -> JsString(a.description)
    }

    val phase = Json.obj(
      "name" -> state.phase.name,
      "activities" -> JsObject(activities)
    )

    Json.obj(
      "turn" -> state.turn,
      "phase" -> phase,
      "terrorLevel" -> state.terrorStep(-90, 90, 25),
      "countryPRs" -> state.countryPRs.mapValues(_.pr),
      "countryIncomes" -> state.countryPRs.mapValues(_.incomeLevels.values)
    )
  }

  def gameState = Action.async { _ =>
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
          prForm,
          incomeForm
        ))
    }
  }

  def getGameState: Future[GameState] = {
    (gameActor ? GetGameState()).mapTo[GameState]
  }

  def getTweets: Future[Seq[Tweet]] = {
    (tweetActor ? GetTweets).map {
      case SendTweets(tweets) => tweets
      case _ => Seq()
    }
  }

  def tweets = Action.async { _ =>

    def asJson(tweet: Tweet) = Json.obj(
      "id" -> JsString(tweet.id.toString()),
      "text" -> JsString(tweet.text),
      "posted" -> JsString(tweet.posted.format(DateTimeFormatter.ofPattern("H:mm:ss").withZone(ZoneId.systemDefault())))
    )

    implicit val writesTweet: Writes[Tweet] = Writes[Tweet] {tweet => asJson(tweet)}

    getTweets.map {
      tweets => Ok(Json.toJson(tweets))
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
        "pr" -> number(min = 1, max = 9)
      )(PrUpdate.apply)(PrUpdate.unapply)
    )
  }

  def updatePr() = StackAction(AuthorityKey -> Admin) { request =>
    prForm.bindFromRequest()(request).value.foreach(
      prUpdate => gameActor ! prUpdate
    )

    Redirect(routes.Status.editGameState())
  }

  val incomeForm: Form[IncomeUpdate] = {
    Form(
      mapping(
        "country" -> text(),
        "pr" -> number(min=1, max=9),
        "increment" -> boolean
      )(IncomeUpdate.apply)(IncomeUpdate.unapply)
    )
  }

  def updateIncome() = StackAction(AuthorityKey -> Admin) { request =>
    incomeForm.bindFromRequest()(request).value.foreach(
      incomeUpdate => gameActor ! incomeUpdate
    )

    Redirect(routes.Status.editGameState())
  }

  def advancePhase() = StackAction(AuthorityKey -> Admin) { request =>
    gameActor ! AdvancePhase()
    Redirect(routes.Status.editGameState())
  }

  def regressPhase() = StackAction(AuthorityKey -> Admin) { request =>
    gameActor ! RegressPhase()
    Redirect(routes.Status.editGameState())
  }

  def start() = StackAction(AuthorityKey -> Admin) { request =>
    gameActor ! Start()
    Redirect(routes.Status.editGameState())
  }

  def pause() = StackAction(AuthorityKey -> Admin) { request =>
    gameActor ! Pause()
    Redirect(routes.Status.editGameState())
  }

  def reset() = StackAction(AuthorityKey -> Admin) { request =>
    gameActor ! Reset()
    Redirect(routes.Status.editGameState())
  }
}
