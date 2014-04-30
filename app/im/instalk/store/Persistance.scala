package im.instalk.store

import com.datastax.driver.core._
import play.api.Configuration
import akka.actor.ActorSystem
import akka.event.Logging
import im.instalk.protocol.RoomId
import im.instalk.User
import scala.collection.JavaConverters._
import play.api.libs.json._


trait Persistence {
  def storeEvent(room: RoomId, seqNr: Long, op: String, data: JsObject, topic: String, members: Iterable[User]): Unit
  def getNextAvailableSeqNr(room: RoomId): Option[Long]
  def getTopic(room: RoomId): Option[String]
  def dropRoomHistory(room: RoomId): Unit
  def dropAllRooms(): Unit //Dangerous!
  def close(): Unit
}

class CassandraPersistence(val config: Configuration)(implicit actorSystem: ActorSystem) extends Persistence with CassandraConfig with CassandraStatements {
  val log = Logging.getLogger(actorSystem, this)

  private[this] val cluster = Cluster.builder.addContactPoints(contactPoints: _*).build
  private[this] val metadata = cluster.getMetadata()
  val session = cluster.connect()
  log.info("Connected to Cassandra Cluster {}", metadata.getClusterName)
  session.execute(createKeyspace)
  session.execute(createTable)

  val createEventStmt = session.prepare(createEvent).setConsistencyLevel(writeConsistency)
  val selectLatestEventsStmt = session.prepare(selectLatestEvents).setConsistencyLevel(readConsistency)
  val selectOlderEventsStmt = session.prepare(selectOlderEvents).setConsistencyLevel(readConsistency)
  val selectLatestSeqNrStmt = session.prepare(selectLatestSeqNr).setConsistencyLevel(readConsistency)
  val selectLatestTopicStmt = session.prepare(selectLatestTopic).setConsistencyLevel(readConsistency)

  val dropRoomHistoryStmt = session.prepare(dropRoom).setConsistencyLevel(writeConsistency)

  def storeEvent(room: RoomId, seqNr: Long, op: String, data: JsObject, topic: String, members: Iterable[User]): Unit = {
    val users = members.map(Json.toJson(_).toString).toList.asJava
    session.execute(createEventStmt.bind(room, seqNr: java.lang.Long, op, topic, data.toString, users))
  }

  def getNextAvailableSeqNr(room: RoomId): Option[Long] = {
    val result = session.execute(selectLatestSeqNrStmt.bind(room)).one
    if (null != result)
      Some(result.getLong(0) + 1)
    else
      None
  }

  def getTopic(room: RoomId): Option[String] = {
    val result = session.execute(selectLatestTopicStmt.bind(room)).one
    if (null != result)
      Some(result.getString(0))
    else
      None
  }

  def dropRoomHistory(room: RoomId): Unit = session.execute(dropRoomHistoryStmt.bind(room))

  def dropAllRooms(): Unit = session.execute(dropTable)

  //create the table if not exists
  def close(): Unit = {
    session.close()
    cluster.close()
  }
}