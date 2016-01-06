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

  case class IncomeUpdate(country: String, pr: Int, increment: Boolean) extends GameActorMessage

  case class AdvancePhase() extends GameActorMessage

  case class RegressPhase() extends GameActorMessage

  case class Tick() extends GameActorMessage

  case class Start() extends GameActorMessage

  case class Pause() extends GameActorMessage

  case class Reset() extends GameActorMessage

}

class GameActor(stateFile: Path) extends Actor {

  import GameActor._
  import context._

  import GameStateFormat._

  val defaultState = GameState(
    turn = 1,
    phaseIndex = 0,
    terrorLevel = 23,
    countryPRs = SortedMap(
      "Brazil" -> CountryPR(6, SortedMap(1 -> 3, 2 -> 5, 3 -> 7, 4 -> 9, 5 -> 12, 6 -> 14, 7 -> 16, 8 -> 18, 9 -> 20)),
      "China" -> CountryPR(6, SortedMap(1 -> 5, 2 -> 7, 3 -> 9, 4 -> 12, 5 -> 15, 6 -> 18, 7 -> 21, 8 -> 23, 9 -> 26)),
      "France" -> CountryPR(6, SortedMap(1 -> 4, 2 -> 6, 3 -> 8, 4 -> 10, 5 -> 13, 6 -> 15, 7 -> 17, 8 -> 19, 9 -> 21)),
      "India" -> CountryPR(6, SortedMap(1 -> 3, 2 -> 5, 3 -> 7, 4 -> 9, 5 -> 11, 6 -> 14, 7 -> 16, 8 -> 18, 9 -> 20)),
      "Japan" -> CountryPR(6, SortedMap(1 -> 4, 2 -> 6, 3 -> 8, 4 -> 10, 5 -> 13, 6 -> 16, 7 -> 18, 8 -> 21, 9 -> 23)),
      "Russia" -> CountryPR(6, SortedMap(1 -> 3, 2 -> 5, 3 -> 7, 4 -> 9, 5 -> 11, 6 -> 13, 7 -> 15, 8 -> 17, 9 -> 18)),
      "UK" -> CountryPR(6, SortedMap(1 -> 4, 2 -> 6, 3 -> 8, 4 -> 10, 5 -> 13, 6 -> 15, 7 -> 17, 8 -> 19, 9 -> 21)),
      "US" -> CountryPR(6, SortedMap(1 -> 5, 2 -> 8, 3 -> 11, 4 -> 14, 5 -> 16, 6 -> 19, 7 -> 22, 8 -> 25, 9 -> 28))
    )
  )

  def stateFromFile: Option[GameState] =
    if (Files.notExists(stateFile))
      None
    else
      Try {
        val json = Files.readAllBytes(stateFile)
        Json.parse(json).validate[GameState].get
      } match {
        case Success(gameState) => Some(gameState)
        case Failure(error) =>
          Logger.error("Failed to read file", error)
          None
      }

  def stateToFile(state: GameState): Unit = {
    Files.write(stateFile, Json.stringify(Json.toJson(state.paused())).getBytes(StandardCharsets.UTF_8))
  }

  val state = stateFromFile.getOrElse(defaultState)

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

    case IncomeUpdate(country, pr, increment) =>
      val newState = state.withCountryIncome(country, pr, increment)
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
  }

  def receive = buildReceive(state, None)
}
