package uk.co.goblinoid

/**
 *
 * Created by Jeff on 22/09/2015.
 */

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

import akka.actor._
import _root_.play.api.libs.json._

import scala.collection.immutable.SortedMap
import scala.util.Try


object GameActor {
  def props(state_file: Path) = Props(new GameActor(state_file))

  case class GetGameState()

  case class TerrorUpdate(terror: Int)

  case class PrUpdate(country: String, pr: Int)

  case class Reset()
}

class GameActor(state_file: Path) extends Actor {

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

  def stateFromFile: Option[GameState] = Try {
    val json = Files.readAllBytes(state_file)
    Json.parse(json).validate[GameState].get
  }.toOption


  def stateToFile(state: GameState): Unit = {
    Files.write(state_file, Json.stringify(Json.toJson(state)).getBytes(StandardCharsets.UTF_8))
  }

  def backupState(state: GameState): Unit = {
    val (base, ext) = state_file.getFileName.toString.span(_.toString != ".")
    val backupFile = state_file.resolveSibling(base + LocalDateTime.now.format(DateTimeFormatter.ofPattern("-yyyy-MM-dd-HH-mm-ss")) + ext)

    if (Files.notExists(backupFile))
      Files.copy(state_file, backupFile)
  }

  val state = stateFromFile.getOrElse(defaultState)

  def buildReceive(state: GameState): Receive = {
    case GetGameState() =>
      sender() ! state

    case TerrorUpdate(newTerror) =>
      val newState: GameState = state.withTerror(newTerror)
      stateToFile(newState)
      become(buildReceive(newState))

    case PrUpdate(country, newPr) =>
      val newState: GameState = state.withCountryPr(country, newPr)
      stateToFile(newState)
      become(buildReceive(newState))

    case Reset() =>
      backupState(state)
      stateToFile(defaultState)
      become(buildReceive(defaultState))
  }

  def receive = buildReceive(state)
}
