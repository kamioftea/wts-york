package uk.co.goblinoid

import _root_.play.api.Logger
import _root_.play.api.libs.json.{Writes, JsPath, Reads}
import _root_.play.api.libs.functional.syntax._

import scala.collection.immutable.SortedMap

case class GameState(turn: Int, phase: Int, terrorRank: Int, countryPRs: SortedMap[String, CountryPR]) {

  def terrorLevel(min: Int, max: Int, step: Int = 1): Int = {
    if (step == 0) throw new IllegalArgumentException("Step must not be 0")

    val step_factor = GameState.TERROR_RANK_MAX / step

    if (step_factor == 0) max
    else (terrorRank / step) * ((max - min) / step_factor) + min
  }

  def withTerror(newTerror: Int) = GameState(turn, phase, newTerror, countryPRs)

  def withCountryPr(country: String, newPr: Int) = countryPRs.get(country) match {
    case Some(currentPr) => GameState(turn, phase, terrorRank, countryPRs.updated(country, currentPr.withPr(newPr)))
    case None => this
  }

}

object GameState {
  val TERROR_RANK_MAX = 250
}

case class CountryPR(pr: Int, incomeLevels: SortedMap[Int, Int]) {
  lazy val income = incomeLevels.getOrElse(pr, 0)

  def withPr(newPr: Int) = CountryPR(newPr, incomeLevels)
}

object GameStateFormat {

  import uk.co.goblinoid.util.SortedMapFormat._
  import CountryPRFormat._

  implicit val readsGameState: Reads[GameState] = (
    (JsPath \ "turn").read[Int] and
      (JsPath \ "phase").read[Int] and
      (JsPath \ "terror_rank").read[Int] and
      (JsPath \ "country_prs").read[SortedMap[String, CountryPR]]
    )(GameState.apply _)

  implicit val writesGameState: Writes[GameState] = (
    (JsPath \ "turn").write[Int] and
      (JsPath \ "phase").write[Int] and
      (JsPath \ "terror_rank").write[Int] and
      (JsPath \ "country_prs").write[SortedMap[String, CountryPR]]
    )(unlift(GameState.unapply))
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