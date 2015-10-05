package uk.co.goblinoid.auth

/**
 *
 * Created by Jeff on 04/10/2015.
 */
case class Account(name: String, role: Role) {

}

object Account {

  def authenticate(username: String, password: String): Account = {
    play.api.Play.current.configuration.getString("users." + username)
      .flatMap(
        user_password => {
          if (password == user_password)
            Some(Account(username, if (username == "admin") Admin else RegisteredUser))
          else
            Some(Account(username, Guest))
        }
      ).getOrElse(Account("", Guest))
  }

}