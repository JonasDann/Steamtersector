package controllers

import javax.inject.Inject

import play.api.Configuration
import play.api.libs.json.{JsDefined, JsValue, JsSuccess, Json}
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Game {
  implicit val format = Json.format[Game]
}
case class Game (appid: Int, name: String, playtime_forever: Int, img_icon_url: String, img_logo_url: String) {
  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case game: Game => game.appid.equals(this.appid)
      case _ => false
    }
  }

  override def hashCode(): Int = appid
}

object Games {
  implicit val format = Json.format[Games]
}
case class Games (games: Seq[Game])

object OwnedGamesResponse {
  implicit val format = Json.format[OwnedGamesResponse]
}
case class OwnedGamesResponse (response: Games)

object User {
  implicit val format = Json.format[User]
}
case class User (personaname: String, profileurl: String, avatar: String)

class Application @Inject()(config:Configuration, wSClient: WSClient) extends Controller {

  val apikey = config.getString("steam.apikey").getOrElse {
    throw new Exception("Do you even configure? (You have to enter a steam api key in the config)")
  }

  def index = Action {
    Ok(views.html.index("Welcome to Stintersector"))
  }

  def games(users: String) = Action.async {
    Future.sequence(users.replace(" ", "").split(",").toSeq.map((user) => {
      wSClient.url("http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=" + apikey + "&steamid=" + user + "&include_appinfo=1").get()
    })).map((responsePerUser) => {
      val gamesPerUser = responsePerUser.map((response) => response.json.as[OwnedGamesResponse].response.games)
      val intersectedGames = gamesPerUser.drop(1).foldLeft(gamesPerUser.head)((acc, elem) => acc.intersect(elem))
      Ok(Json.toJson(intersectedGames))
    })
  }

  def user(user: String) = Action.async {
    wSClient.url("http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=" + apikey + "&steamids=" + user).get().map((response) => {
      response.json \ "response" \ "players" match {
        case JsDefined(users:JsValue) => Ok(Json.toJson(users.as[Seq[User]].head))
        case _ => NotFound
      }
    })
  }

}