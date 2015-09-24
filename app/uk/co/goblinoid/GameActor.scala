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
import play.api.libs.json._

import scala.collection.immutable.SortedMap
import scala.util.Try


object GameActor {
  def props(file: Path) = Props(new GameActor(file: Path))

  case class GetStatus()

  case class TerrorUpdate(terror: Int)

  case class PrUpdate(country: String, pr: Int)

  case class Reset()
}

class GameActor(file: Path) extends Actor {

  import GameActor._
  import context._

  import GameStateFormat._

  val defaultState = GameState(
    turn = 1,
    phase = 1,
    terrorRank = 23,
    countryPRs = SortedMap(
      "Brazil" -> CountryPR(4, SortedMap(1 -> 2, 2 -> 4, 3 -> 6, 4 -> 8, 5 -> 10, 6 -> 12, 7 -> 14, 8 -> 16)),
      "China" -> CountryPR(5, SortedMap(1 -> 2, 2 -> 5, 3 -> 8, 4 -> 11, 5 -> 14, 6 -> 17, 7 -> 20, 8 -> 23)),
      "France" -> CountryPR(6, SortedMap(1 -> 2, 2 -> 4, 3 -> 6, 4 -> 8, 5 -> 10, 6 -> 12, 7 -> 14, 8 -> 16)),
      "India" -> CountryPR(4, SortedMap(1 -> 2, 2 -> 4, 3 -> 6, 4 -> 8, 5 -> 10, 6 -> 12, 7 -> 14, 8 -> 16)),
      "Japan" -> CountryPR(7, SortedMap(1 -> 2, 2 -> 5, 3 -> 8, 4 -> 11, 5 -> 14, 6 -> 17, 7 -> 20, 8 -> 23)),
      "Russia" -> CountryPR(2, SortedMap(1 -> 2, 2 -> 4, 3 -> 6, 4 -> 8, 5 -> 10, 6 -> 12, 7 -> 14, 8 -> 16)),
      "UK" -> CountryPR(5, SortedMap(1 -> 2, 2 -> 5, 3 -> 8, 4 -> 11, 5 -> 14, 6 -> 17, 7 -> 20, 8 -> 23)),
      "USA" -> CountryPR(6, SortedMap(1 -> 2, 2 -> 5, 3 -> 8, 4 -> 11, 5 -> 14, 6 -> 17, 7 -> 20, 8 -> 23))
    )
  )

  def stateFromFile: Option[GameState] = {
    Try {
      val json = Files.readAllBytes(file)
      Json.parse(json).validate[GameState].get
    }.toOption
  }

  def stateToFile(state: GameState): Unit = {
    Files.write(file, Json.stringify(Json.toJson(state)).getBytes(StandardCharsets.UTF_8))
  }

  def backupState(state: GameState): Unit = {
    val (base, ext) = file.getFileName.toString.span(_.toString != ".")
    val backupFile = file.resolveSibling(base + LocalDateTime.now.format(DateTimeFormatter.ofPattern("-yyyy-MM-dd-HH-mm-ss")) + ext)

    if (Files.notExists(backupFile))
      Files.copy(file, backupFile)
  }

  val state = stateFromFile.getOrElse(defaultState)

  def buildReceive(state: GameState): Receive = {
    case GetStatus() =>
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
