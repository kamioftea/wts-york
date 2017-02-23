package uk.co.goblinoid

/**
 *
 * Created by Jeff on 22/09/2015.
 */

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

import _root_.play.Logger
import akka.actor._
import _root_.play.api.libs.json._
import uk.co.goblinoid.twitter.Tweet

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.collection.immutable.SortedMap
import scala.util.{Failure, Success, Try}


object GameActor {
  def props(stateFile: Path) = Props(new GameActor(stateFile))

  sealed trait GameActorMessage

  case class GetGameState() extends GameActorMessage

  case class TerrorUpdate(terror: Int) extends GameActorMessage

  case class PrUpdate(country: String, pr: Int) extends GameActorMessage

  case class IncomeUpdate(country: String, modifier: Int) extends GameActorMessage

  case class AdvancePhase() extends GameActorMessage

  case class RegressPhase() extends GameActorMessage

  case class Tick() extends GameActorMessage

  case class Start() extends GameActorMessage

  case class Pause() extends GameActorMessage

  case class Reset() extends GameActorMessage

  case class ToggleBold(id: BigInt, isBold: Boolean) extends GameActorMessage

  case class SetFeatured(tweet: Option[Tweet]) extends GameActorMessage
}

class GameActor(stateFile: Path) extends Actor {

  import GameActor._
  import context._

  import GameStateFormat._

  val defaultState = GameState(
    turn = 1,
    phaseIndex = 0,
    terrorLevel = 0,
    countryPRs = SortedMap(
      "Brazil" -> CountryPR(6, 0, SortedMap(1 -> 2, 2 -> 5, 3 -> 6, 4 -> 7, 5 -> 8, 6 -> 9, 7 -> 10, 8 -> 11, 9 -> 12)),
      "China" -> CountryPR(6, 0, SortedMap(1 -> 3, 2 -> 4, 3 -> 6, 4 -> 8, 5 -> 10, 6 -> 12, 7 -> 14, 8 -> 16, 9 -> 18)),
      "France" -> CountryPR(6, 0, SortedMap(1 -> 2, 2 -> 5, 3 -> 6, 4 -> 7, 5 -> 8, 6 -> 9, 7 -> 10, 8 -> 11, 9 -> 12)),
      "India" -> CountryPR(6, 0, SortedMap(1 -> 2, 2 -> 5, 3 -> 6, 4 -> 7, 5 -> 8, 6 -> 9, 7 -> 10, 8 -> 11, 9 -> 12)),
      "Japan" -> CountryPR(6, 0, SortedMap(1 -> 3, 2 -> 5, 3 -> 6, 4 -> 8, 5 -> 9, 6 -> 10, 7 -> 12, 8 -> 13, 9 -> 14)),
      "Russia" -> CountryPR(6, 0, SortedMap(1 -> 2, 2 -> 4, 3 -> 5, 4 -> 6, 5 -> 7, 6 -> 8, 7 -> 9, 8 -> 10, 9 -> 11)),
      "UK" -> CountryPR(6, 0, SortedMap(1 -> 2, 2 -> 5, 3 -> 6, 4 -> 7, 5 -> 8, 6 -> 9, 7 -> 10, 8 -> 11, 9 -> 12)),
      "US" -> CountryPR(6, 0, SortedMap(1 -> 3, 2 -> 5, 3 -> 7, 4 -> 9, 5 -> 11, 6 -> 13, 7 -> 15, 8 -> 17, 9 -> 20))
    )
  )

  def stateFromFile: Option[GameState] =
    if (Files.notExists(stateFile))
      None
    else
      Try {
        val json = Files.readAllBytes(stateFile)
        Json.parse(json).validate[GameState]
      } match {
        case Success(JsSuccess(gameState, _)) => Some(gameState)
        case Success(JsError(errs)) =>
          Logger.error("Failed to read file: " + errs.mkString(";"))
          None
        case Failure(error) =>
          Logger.error("Failed to read file", error)
          None
      }

  def stateToFile(state: GameState): Unit = {
    Files.write(stateFile, Json.stringify(Json.toJson(state.paused())).getBytes(StandardCharsets.UTF_8))
  }

  val state: GameState = stateFromFile.getOrElse(defaultState)

  def backupState(state: GameState): Unit = {
    val (base, ext) = stateFile.getFileName.toString.span(_.toString != ".")
    val backupFile = stateFile.resolveSibling(base + LocalDateTime.now.format(DateTimeFormatter.ofPattern("-yyyy-MM-dd-HH-mm-ss")) + ext)

    if (Files.notExists(stateFile))
      stateToFile(state)

    if (Files.notExists(backupFile))
      Files.copy(stateFile, backupFile)
  }

  def buildReceive(state: GameState, ticker: Option[Cancellable]): Receive = {
    case GetGameState() =>
      sender() ! state

    case TerrorUpdate(newTerror) =>
      val newState = state.withTerror(newTerror)
      stateToFile(newState)
      
      become(buildReceive(newState, ticker))

    case PrUpdate(country, newPr) =>
      val newState = state.withCountryPr(country, newPr)
      stateToFile(newState)
      
      become(buildReceive(newState, ticker))

    case IncomeUpdate(country, modifier) =>
      val newState = state.withCountryModifier(country, modifier)
      stateToFile(newState)

      become(buildReceive(newState, ticker))

    case AdvancePhase() =>
      backupState(state)
      val newState = state.advancePhase()
      stateToFile(newState)
      
      become(buildReceive(newState, ticker))

    case RegressPhase() =>
      backupState(state)
      val newState = state.regressPhase()
      stateToFile(newState)
      
      become(buildReceive(newState, ticker))

    case Tick() => (state.phaseEnd, state.pauseStart) match {
      case (Some(phaseEnd), None) if LocalDateTime.now isAfter phaseEnd =>
        backupState(state)
        become(buildReceive(state.advancePhase(), ticker))
        
      case _ =>
        become(buildReceive(state, ticker))
    }

    case Start() =>
      val newTicker = ticker match {
        case Some(existing) if !existing.isCancelled => ticker
        case _ =>
          Some(
            system.scheduler.schedule(1 second, 1 second, self, Tick())
          )
      }
      
      backupState(state)
      val newState = state.started()
      stateToFile(newState)
      
      become(buildReceive(newState, newTicker))

    case Pause() =>
      ticker.foreach(_.cancel())

      backupState(state)
      val newState = state.paused()
      stateToFile(newState)

      become(buildReceive(newState, None))

    case Reset() =>
      ticker.foreach(_.cancel())
      
      backupState(state)
      val newState = defaultState.stopped()
      stateToFile(newState)
      
      become(buildReceive(newState, None))

    case ToggleBold(id, true) =>
      backupState(state)
      val newState = state.withBold(state.boldTweetIds :+ id)
      become(buildReceive(newState, ticker))

    case ToggleBold(id, false) =>
      backupState(state)
      val newState = state.withBold(state.boldTweetIds.filter(_ != id))
      stateToFile(newState)

      become(buildReceive(newState, ticker))

    case SetFeatured(maybeTweet) =>
      backupState(state)
      val newState = state.withFeatured(maybeTweet)
      stateToFile(newState)

      become(buildReceive(newState, ticker))
  }

  def receive: Receive = buildReceive(state, None)
}
