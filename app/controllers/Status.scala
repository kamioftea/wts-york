package controllers

import java.nio.file.{Path, Paths}
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
import uk.co.goblinoid.twitter.{Tweet, TweetListActor}
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

  val filePath: Path = Paths.get(current.configuration.getString("game.filepath").getOrElse("default-game.json"))
  val gameActor: ActorRef = system.actorOf(GameActor.props(filePath), "game-actor")

  val screenName: Option[String] = current.configuration.getString("twitter.screen_name")
  val tweetActor: ActorRef = system.actorOf(TweetListActor.props(screenName), "tweet-actor")

  def index: Action[AnyContent] = Action.async { _ =>
    for {
      gameState <- getGameState
      tweets <- getTweets()
    } yield {
      Ok(views.html.gameState(gameState, tweets))
    }
  }

  def buildGameStateJson(state: GameState): JsObject = {
    import uk.co.goblinoid.twitter.Twitter.{writesTweet, bigIntWrites}

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
      "phaseEnd" -> state.phaseEnd,
      "paused" -> state.pauseStart.isDefined,
      "terrorLevel" -> state.terrorStep(-90, 90, 1),
      "countryPRs" -> state.countryPRs.mapValues(_.pr),
      "countryIncomes" -> state.countryPRs.mapValues(_.incomeLevels.values),
      "featuredTweet" -> state.featuredTweet,
      "boldTweetIds" -> state.boldTweetIds
    )
  }

  def gameState: Action[AnyContent] = Action.async { _ =>
    getGameState map { gameState =>
      val json = buildGameStateJson(gameState)

      Ok(json)
    }
  }

  def editGameState: Action[AnyContent] = AsyncStack(AuthorityKey -> Admin) { _ =>
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

  def getTweets(count: Int = 5): Future[Seq[Tweet]] = {
    (tweetActor ? GetTweets(count)).map {
      case SendTweets(tweets) => tweets
      case _ => Seq()
    }
  }

  def tweets(count: Int = 5): Action[AnyContent] = Action.async { _ => {

    import uk.co.goblinoid.twitter.Twitter._

    getTweets(count).map {
      tweets => Ok(Json.toJson(tweets))
    }
  }}

  val terrorForm: Form[TerrorUpdate] = {
    Form(
      mapping(
        "terror" -> number(min = 0, max = 250)
      )(TerrorUpdate.apply)(TerrorUpdate.unapply)
    )
  }

  def updateTerror(): Action[AnyContent] = StackAction(AuthorityKey -> Admin) { request =>
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

  def updatePr(): Action[AnyContent] = StackAction(AuthorityKey -> Admin) { request =>
    prForm.bindFromRequest()(request).value.foreach(
      prUpdate => gameActor ! prUpdate
    )

    Redirect(routes.Status.editGameState())
  }

  val incomeForm: Form[IncomeUpdate] = {
    Form(
      mapping(
        "country" -> text(),
        "pr" -> number(min = 1, max = 9),
        "increment" -> boolean
      )(IncomeUpdate.apply)(IncomeUpdate.unapply)
    )
  }

  def updateIncome(): Action[AnyContent] = StackAction(AuthorityKey -> Admin) { request =>
    incomeForm.bindFromRequest()(request).value.foreach(
      incomeUpdate => gameActor ! incomeUpdate
    )

    Redirect(routes.Status.editGameState())
  }

  def advancePhase(): Action[AnyContent] = StackAction(AuthorityKey -> Admin) { request =>
    gameActor ! AdvancePhase()
    Redirect(routes.Status.editGameState())
  }

  def regressPhase(): Action[AnyContent] = StackAction(AuthorityKey -> Admin) { request =>
    gameActor ! RegressPhase()
    Redirect(routes.Status.editGameState())
  }

  def start(): Action[AnyContent] = StackAction(AuthorityKey -> Admin) { request =>
    gameActor ! Start()
    Redirect(routes.Status.editGameState())
  }

  def pause(): Action[AnyContent] = StackAction(AuthorityKey -> Admin) { request =>
    gameActor ! Pause()
    Redirect(routes.Status.editGameState())
  }

  def reset(): Action[AnyContent] = StackAction(AuthorityKey -> Admin) { request =>
    gameActor ! Reset()
    Redirect(routes.Status.editGameState())
  }

  def media() = Action { implicit request =>
    Ok(views.html.media())
  }

  def toggleBold(id: String, isBold: Boolean) = Action { _ =>
    gameActor ! ToggleBold(BigInt(id), isBold)
    Ok(Json.obj("received" -> true))
  }
}
