package uk.co.goblinoid

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * The game phases 
 * Created by jeff on 07/10/2015.
 */
case class Phase(name: String, activities: Seq[Activity], duration: Duration)

case class Activity(group: String, description: String)

object Phase {
  val phases = Vector(
    Phase(
      "Operations Phase (Allocation)", 
      Seq(
        Activity("UN Ambassadors", "Go to the Security Council table."), 
        Activity("Heads of State","Buy influence cards from nation control."), 
        Activity("Generals", "Goto world map with interceptors, special agents, and megabucks needed for the turn."), 
        Activity("Scientists", "Buy research points from science control.")
      ),
      20 seconds
    ),
    Phase(
      "Operations Phase (Place Units)",
      Seq(
        Activity("UN Ambassadors", "Discuss current issues and crises. Draft and agree on any security council resolutions."),
        Activity("Heads of State/Other Diplomats", "Circulate and conduct diplomacy. May visit the UN, but not interrupt."),
        Activity("Generals", "Place units and resources in the ready box. Then aliens place ships. Then generals may place or move units."),
        Activity("Scientists", "Conduct research with science control. Arrange a science conference or declare a trade fair. May liaise with other players, but not go to the UN or World Map.")
      ),
      5 minutes
    ),
    Phase(
      "Operations Phase (Resolve Interceptions)",
      Seq(
        Activity("UN Ambassadors", "Discuss current issues and crises. Draft and agree on any security council resolutions."),
        Activity("Heads of State/Other Diplomats", "Circulate and conduct diplomacy. May visit the UN, but not interrupt."),
        Activity("Generals", "Control will resolve interceptions. Human and alien command players should be ready for any resolutions they are involved in."),
        Activity("Scientists", "Conduct research with science control. Arrange a science conference or trade fair. May liaise with other players, but not go to the UN or World Map.)")
      ),
      5 minutes
    ),
    Phase(
      "Operations Phase (Resolve Ground Forces)",
      Seq(
        Activity("UN Ambassadors", "Discuss current issues and crises. Draft and agree on any security council resolutions."),
        Activity("Heads of State/Other Diplomats", "Circulate and conduct diplomacy. May visit the UN, but not interrupt."),
        Activity("Generals", "Aliens declare missions they wish to launch. Resolve ground conflicts."),
        Activity("Scientists", "Conduct research with science control. Arrange a science conference or trade fair. May liaise with other players, but not go to the UN or World Map.)")
      ),
      5 minutes
    ),
    Phase(
      "End of Operations Phase",
      Seq(
        Activity("All players", "Return to team table."),
        Activity("Control", "Update PR tracks.)")
      ),
      20 seconds
    ),
    Phase(
      "Mid Turn Team Time",
      Seq(
        Activity("All players", "Report back to teams and plan for the next diplomacy phase."),
        Activity("Media", "May perform an announcement.)")
      ),
      5 minutes
    ),
    Phase(
      "Diplomacy Phase",
      Seq(
        Activity("Diplomats", "May go to the world map to resolve political/agent actions."),
        Activity("Scientists", "May attend ONE science conference OR trade fair."),
        Activity("Other players", "May circulate to discuss plans, make agreements, threaten, cajole and flatter.)")
      ),
      14 minutes
    ),
    Phase(
      "End of Turn Team Time",
      Seq(
        Activity("All players", "Report back to teams and plan for the next turn's operations phase."),
        Activity("Media", "May perform an announcement.)"))
      ,
      5 minutes
    )
  )
}
