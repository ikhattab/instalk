/*
 * Copyright 2014 The Instalk Project
 *
 * The Instalk Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package im.instalk.actors

import akka.actor._
import play.api.mvc._
import scala.util.Random
import im.instalk.User

object ClientManager {
  case class CreateClient(r: RequestHeader)
}
class ClientManager extends Actor with ActorLogging {
  def receive = {
    case ClientManager.CreateClient(r) =>
      //try to figure out who is the user
      //TODO: let's query the user registery, for now we are assuming anonymous
      val actor = context.actorOf(Props(new WebSocketActor(User.Anonymous(User.generateColor, User.generateUsername), Client.props, r.remoteAddress)), "client-" + Math.abs(Random.nextInt))
      actor.tell(WebSocketActor.GetActuator, sender)
  }
}