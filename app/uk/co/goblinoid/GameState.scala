package uk.co.goblinoid

import java.time.{Duration, LocalDateTime}
import java.time.format.DateTimeFormatter

import _root_.play.api.libs.json._
import _root_.play.api.libs.functional.syntax._
import play.api.Logger
import uk.co.goblinoid.twitter.Tweet

import scala.collection.immutable.SortedMap

case class GameState(turn: Int,
                     phaseIndex: Int,
                     terrorLevel: Int,
                     countryPRs: SortedMap[String, CountryPR],
                     phaseEnd: Option[LocalDateTime] = None,
                     pauseStart: Option[LocalDateTime] = None,
                     featuredTweet: Option[Tweet] = None,
                     boldTweetIds: Seq[BigInt] = Seq()) {

  // phaseIndex is 1 indexed due to game labelling
  lazy val phase: Phase =
    if(Phase.phases.isDefinedAt(phaseIndex - 1))
      Phase.phases(phaseIndex - 1)
    else
      // TODO: Load from config
      Phase("Watch the Skies hasn't started yet", Seq(
        Activity("Starts", "9am, 25th February 2017"),
        Activity("Location", "D Bar, University of York")
      ), Duration.ZERO)

  def terrorStep(min: Int, max: Int, step: Int = 1): Int = {
    if (step == 0) throw new IllegalArgumentException("Step must not be 0")

    val step_factor = GameState.TERROR_RANK_MAX / step

    if (step_factor == 0)
      max
    else
      ((terrorLevel / step) * (max - min).toDouble / step_factor).toInt + min
  }

  def withTerror(newTerror: Int): GameState = copy(terrorLevel = newTerror)

  def withCountryPr(country: String, newPr: Int): GameState = countryPRs.get(country) match {
    case Some(currentPr) => copy(countryPRs = countryPRs.updated(country, currentPr.withPr(newPr)))
    case None => this
  }

  def withCountryIncome(country: String, pr: Int, increment: Boolean): GameState = countryPRs.get(country) match {
    case Some(currentPr) => copy(countryPRs = countryPRs.updated(country, currentPr.withIncome(pr, increment)))
    case None => this
  }

  def setPhase(newTurn: Int, newPhaseIndex: Int): GameState = {
    Logger.warn(s"newTurn: $newTurn, newPhaseIndex: $newPhaseIndex")
    val newPhase = Phase.phases(newPhaseIndex - 1)

    // We only want to update these if they are set, so map over the option
    val newPhaseEnd = phaseEnd map { _ => LocalDateTime.now plus newPhase.duration }
    val newPauseStart = pauseStart map { _ => LocalDateTime.now }

    copy(turn = newTurn, phaseIndex = newPhaseIndex, phaseEnd = newPhaseEnd, pauseStart = newPauseStart)
  }

  def advancePhase(): GameState = {
    val (newTurn, newPhaseIndex) =
      if (this.phaseIndex >= Phase.phases.length)
        (turn + 1, 1)
      else
        (turn, phaseIndex + 1)

    setPhase(newTurn, newPhaseIndex)
  }

  def regressPhase(): GameState = {
    val (newTurn, newPhaseIndex) =
      if (this.phaseIndex <= 1)
        (turn - 1, Phase.phases.length)
      else
        (turn, phaseIndex - 1)

    setPhase(newTurn, newPhaseIndex)
  }

  def started(): GameState = (phaseEnd, pauseStart) match {
    case (Some(oldPhaseEnd), Some(oldPauseStart)) =>
      // Paused
      val pauseLength = Duration.between(oldPauseStart, LocalDateTime.now)
      copy(phaseEnd = Some(oldPhaseEnd plus pauseLength), pauseStart = None)

    case (None, _) =>
      // Stopped
      copy(phaseEnd = Some(LocalDateTime.now plus phase.duration), pauseStart = None)

    case _ =>
      // Already Started
      this
  }

  def paused(): GameState = (phaseEnd, pauseStart) match {
    case (Some(_), None) =>
      // Started
      copy(pauseStart = Some(LocalDateTime.now))

    case _ =>
      // Already Stopped or Paused
      this
  }

  def stopped(): GameState = copy(phaseEnd = None, pauseStart = None)

  lazy val isStarted: Boolean = phaseEnd.isDefined && pauseStart.isEmpty

  def withBold(ids: Seq[BigInt]): GameState = copy(boldTweetIds = ids)

  def withFeatured(maybeTweet: Option[Tweet]): GameState = copy(featuredTweet = maybeTweet)
}

object GameState {
  val TERROR_RANK_MAX = 250
}

object GameStateFormat {

  import uk.co.goblinoid.util.SortedMapFormat._
  import CountryPRFormat._
  import uk.co.goblinoid.twitter.BigIntFormat._
  import twitter.TwitterInternalFormat._

  implicit val zonedDateTimeReads: Reads[LocalDateTime] =
    __.read[String].map(LocalDateTime.parse(_, DateTimeFormatter.ISO_DATE_TIME))

  implicit val zonedDateTimeWrites: Writes[LocalDateTime] = Writes[LocalDateTime] {
    dateTime => JsString(dateTime.format(DateTimeFormatter.ISO_DATE_TIME))
  }

  implicit val readsGameState: Reads[GameState] = (
    (JsPath \ "turn").read[Int] and
      (JsPath \ "phase").read[Int] and
      (JsPath \ "terror_rank").read[Int] and
      (JsPath \ "country_prs").read[SortedMap[String, CountryPR]] and
      (JsPath \ "phase_start").readNullable[LocalDateTime] and
      (JsPath \ "pause_start").readNullable[LocalDateTime] and
      (JsPath \ "featured_tweet").readNullable[Tweet] and
      (JsPath \ "bold_tweet_ids").readNullable[Seq[BigInt]].map(_.getOrElse(Seq()))
    )(GameState.apply _)

  implicit val writesGameState: Writes[GameState] = (
    (JsPath \ "turn").write[Int] and
      (JsPath \ "phase").write[Int] and
      (JsPath \ "terror_rank").write[Int] and
      (JsPath \ "country_prs").write[SortedMap[String, CountryPR]] and
      (JsPath \ "phase_start").writeNullable[LocalDateTime] and
      (JsPath \ "pause_start").writeNullable[LocalDateTime] and
      (JsPath \ "featured_tweet").writeNullable[Tweet] and
      (JsPath \ "bold_tweet_ids").write[Seq[BigInt]]
    )(unlift(GameState.unapply))

}

case class CountryPR(pr: Int, incomeLevels: SortedMap[Int, Int]) {

  lazy val income: Int = incomeLevels.getOrElse(pr, 0)

  def withPr(newPr: Int): CountryPR = copy(pr = newPr)

  def withIncome(prToUpdate: Int, increment: Boolean): CountryPR = {
    val newIncome =
      if(increment) incomeLevels(prToUpdate) + 1
      else incomeLevels(prToUpdate) - 1

    copy(incomeLevels = incomeLevels.updated(prToUpdate, newIncome))
  }
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
