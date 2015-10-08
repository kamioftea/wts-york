package uk.co.goblinoid

import java.time.{Duration, LocalDateTime}
import java.time.format.DateTimeFormatter

import _root_.play.api.libs.json._
import _root_.play.api.libs.functional.syntax._

import scala.collection.immutable.SortedMap

case class GameState(turn: Int,
                     phaseIndex: Int,
                     terrorLevel: Int,
                     countryPRs: SortedMap[String, CountryPR],
                     phaseEnd: Option[LocalDateTime] = None,
                     pauseStart: Option[LocalDateTime] = None) {
  // phaseIndex is 1 indexed due to game labelling
  lazy val phase =
    if(Phase.phases.isDefinedAt(phaseIndex - 1))
      Phase.phases(phaseIndex - 1)
    else
      // TODO: Load from config
      Phase("Watch the Skies hasn't started yet", Seq(Activity("Starts", "9am, 7th November 2015")), Duration.ZERO)

  def terrorStep(min: Int, max: Int, step: Int = 1): Int = {
    if (step == 0) throw new IllegalArgumentException("Step must not be 0")

    val step_factor = GameState.TERROR_RANK_MAX / step

    if (step_factor == 0)
      max
    else
      (terrorLevel / step) * ((max - min) / step_factor) + min
  }

  def withTerror(newTerror: Int) = GameState(turn, phaseIndex, newTerror, countryPRs)

  def withCountryPr(country: String, newPr: Int) = countryPRs.get(country) match {
    case Some(currentPr) => GameState(turn, phaseIndex, terrorLevel, countryPRs.updated(country, currentPr.withPr(newPr)))
    case None => this
  }

  def setPhase(newTurn: Int, newPhaseIndex: Int): GameState = {
    val newPhase = Phase.phases(newPhaseIndex - 1)

    // We only want to update these if they are set, so map over the option
    val newPhaseEnd = phaseEnd map { _ => LocalDateTime.now plus newPhase.duration }
    val newPauseStart = pauseStart map { _ => LocalDateTime.now }

    GameState(newTurn, newPhaseIndex, terrorLevel, countryPRs, newPhaseEnd, newPauseStart)
  }

  def advancePhase() = {
    val (newTurn, newPhaseIndex) =
      if (this.phaseIndex >= Phase.phases.length)
        (turn + 1, 1)
      else
        (turn, phaseIndex + 1)

    setPhase(newTurn, newPhaseIndex)
  }

  def regressPhase() = {
    val (newTurn, newPhaseIndex) =
      if (this.phaseIndex <= 1)
        (turn - 1, Phase.phases.length)
      else
        (turn, phaseIndex - 1)

    setPhase(newTurn, newPhaseIndex)
  }

  def started() = (phaseEnd, pauseStart) match {
    case (Some(oldPhaseEnd), Some(oldPauseStart)) =>
      // Paused
      val pauseLength = Duration.between(oldPauseStart, LocalDateTime.now)
      GameState(turn, phaseIndex, terrorLevel, countryPRs, Some(oldPhaseEnd plus pauseLength), None)

    case (None, _) =>
      // Stopped
      GameState(turn, phaseIndex, terrorLevel, countryPRs, Some(LocalDateTime.now plus phase.duration), None)

    case _ =>
      // Already Started
      this
  }

  def paused() = (phaseEnd, pauseStart) match {
    case (Some(_), None) =>
      // Started
      GameState(turn, phaseIndex, terrorLevel, countryPRs, phaseEnd, Some(LocalDateTime.now))

    case _ =>
      // Already Stopped or Paused
      this
  }

  def stopped() = GameState(turn, phaseIndex, terrorLevel, countryPRs, None, None)
}

object GameState {
  val TERROR_RANK_MAX = 250
}

object GameStateFormat {

  import uk.co.goblinoid.util.SortedMapFormat._
  import CountryPRFormat._

  implicit val zonedDateTimeReads: Reads[LocalDateTime] =
    __.read[String].map(LocalDateTime.parse(_, DateTimeFormatter.ISO_DATE_TIME))

  implicit val zonedDateTimeWrites = Writes[LocalDateTime] {
    dateTime => JsString(dateTime.format(DateTimeFormatter.ISO_DATE_TIME))
  }

  implicit val readsGameState: Reads[GameState] = (
    (JsPath \ "turn").read[Int] and
      (JsPath \ "phase").read[Int] and
      (JsPath \ "terror_rank").read[Int] and
      (JsPath \ "country_prs").read[SortedMap[String, CountryPR]] and
      (JsPath \ "phase_start").readNullable[LocalDateTime] and
      (JsPath \ "pause_start").readNullable[LocalDateTime]
    )(GameState.apply _)

  implicit val writesGameState: Writes[GameState] = (
    (JsPath \ "turn").write[Int] and
      (JsPath \ "phase").write[Int] and
      (JsPath \ "terror_rank").write[Int] and
      (JsPath \ "country_prs").write[SortedMap[String, CountryPR]] and
      (JsPath \ "phase_start").writeNullable[LocalDateTime] and
      (JsPath \ "pause_start").writeNullable[LocalDateTime]
    )(unlift(GameState.unapply))

}

case class CountryPR(pr: Int, incomeLevels: SortedMap[Int, Int]) {
  lazy val income = incomeLevels.getOrElse(pr, 0)

  def withPr(newPr: Int) = CountryPR(newPr, incomeLevels)
}

object CountryPRFormat {

  import uk.co.goblinoid.util.SortedMapFormat._

  implicit val readsCountryPR: Reads[CountryPR] = (
    (JsPath \ "pr").read[Int] and
      (JsPath \ "income_levels").read[SortedMap[Int, Int]]
    )(CountryPR.apply _)

  implicit val writesCountryPR: Writes[CountryPR] = (
    (JsPath \ "pr").write[Int] and
      (JsPath \ "income_levels").write[SortedMap[Int, Int]]
    )(unlift(CountryPR.unapply))

}
