package eu.kohout.dictionary
import akka.actor.{Actor, Cancellable, Props}
import eu.kohout.dictionary.CleansedDataAccumulator.{CreateDictionary, SendDictionary}
import com.typesafe.scalalogging.Logger
import eu.kohout.cleandata.CleanDataManager.CleansedData
import eu.kohout.loaddata.LoadDataManager.CreateDictionaryFromData

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object CleansedDataAccumulator {
  case object CreateDictionary extends CleansedDataAccumulatorMessage
  private case object SendDictionary extends CleansedDataAccumulatorMessage
  case class Dictionary(data: Seq[CleansedData]) extends CleansedDataAccumulatorMessage

  sealed trait CleansedDataAccumulatorMessage

  def props(): Props = Props(new CleansedDataAccumulator)
}

/** This actor will kill self after resolving all cleansed emails from which dictionary will be created.
  *
  */
class CleansedDataAccumulator extends Actor {

  implicit val ec: ExecutionContext = context.dispatcher

  private val log = Logger(self.path.toStringWithoutAddress)

  private var cleansedData: Seq[CleansedData] = Seq.empty
  private var sendToDictionary: Option[Cancellable] = None

  override def receive: Receive = {
    case CreateDictionary =>
      log.debug("Sending CreateDictionaryFromData to loadDataManager")

      sender() ! CreateDictionaryFromData

    case data: CleansedData =>
      sendToDictionary = sendToDictionary.fold(
        Some(
          context.system.scheduler
            .scheduleOnce(delay = 15 seconds, message = SendDictionary, receiver = self)
        )
      ) { cancellable =>
        cancellable.cancel()
        Some(
          context.system.scheduler.scheduleOnce(
            delay = 15 seconds,
            message = SendDictionary,
            receiver = self
          )
        )
      }

      cleansedData = cleansedData :+ data

    case SendDictionary =>
      log.debug("Dictionary resolved! size of cleansedData {}", cleansedData.size)

      context.parent ! CleansedDataAccumulator.Dictionary(cleansedData)
      context.stop(self)

  }
}