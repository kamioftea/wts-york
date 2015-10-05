package uk.co.goblinoid.auth

/**
 *
 * Created by Jeff on 04/10/2015.
 */
sealed trait Role {}

case object Admin extends Role
case object RegisteredUser extends Role
case object Guest extends Role
