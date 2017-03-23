package ru.pavlenov

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import akka.dispatch.{BoundedPriorityMailbox, PriorityGenerator}
import com.typesafe.config.Config
import ru.pavlenov.Parent.{Create, Stop}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Created by Pavlenov Semen on 22.03.17.
  */
object Main extends App {

  implicit val system = ActorSystem("AKKA-SEED")

  val parent = system.actorOf(Props[Parent], "Parent")

  parent ! Create("Child-1")
  parent ! Create("Child-2")
  parent ! Create("Child-3")
  Thread.sleep(7000)
  parent ! Stop("Child-1")
  parent ! Create("Child-4")
  parent ! Stop("Child-2")
  parent ! Stop("Child-3")
  Thread.sleep(7000)
  parent ! Stop("Child-4")

  //  system.terminate()

}

class Parent extends ActorWithLogging {

  import Parent._
  import context.dispatcher

  val children = mutable.HashMap.empty[String, ActorRef]

  val ticker = context.system.scheduler.schedule(0 millis, 5 second, self, Start)

  override def postStop(): Unit = {
    super.postStop()
    ticker.cancel()
  }

  override def receive: Receive = {
    case Create(name) ⇒
      log.info("Receive Create({})", name)
      create(name)
    case Stop(name) ⇒
      log.info("Receive Stop({})", name)
      kill(name)
    case Start ⇒
      children foreach {
        case (name, child) ⇒ child ! Child.Run
      }
    case Terminated(child) ⇒
      log.info("Child dead {}", child.path.name)
      context unwatch child
      children -= child.path.name
  }

  private def create(name: String) = {
    val child = context watch context.actorOf(Props[Child].withMailbox("custom-mailbox"), name)
    children += name → child
  }

  private def kill(name: String) = {
    /**
      * The actor will continue to process its current message (if any),
      * but no additional messages will be processed.
      */
    context stop children(name)
  }

}

object Parent {

  case class Create(name: String)

  case class Stop(name: String)

  case object Start

}

class Child extends ActorWithLogging {

  import Child._
  import context.dispatcher

  override def receive: Receive = await

  def await: Receive = {
    case Run ⇒
      log.info(s"${name} Receive 'Run', run job")
      log.info(s"${name} Switch context to work")
      context become work
      startWork() recover {
        case ex: Throwable ⇒ log.error(ex, "ERROR!")
      } map { _ ⇒
        log.info(s"${name} Work complete!")
        log.info(s"${name} Switch context to await")
        context become await
      }

  }

  def work: Receive = {
    case Run ⇒
      log.info(s"${name} Received 'Run', not react, because already started job")
  }

  private def startWork(): Future[Unit] = {
    log.info(s"${name} Start work")
    Future {
      Thread.sleep(15000)
    }
  }

}

object Child{
  case object Run
}

trait ActorWithLogging extends Actor with ActorLogging{

  val name = self.path.name

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"${Console.GREEN}Start actor ${name}${Console.RESET}")
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info(s"${Console.RED}Stop actor ${name}${Console.RESET}")
  }

}

class CustomMailbox(settings: ActorSystem.Settings, config: Config) extends BoundedPriorityMailbox(
  PriorityGenerator {
    case Child.Run => 100
    case PoisonPill => 1
  }, capacity = 1, pushTimeOut = 0 millis)

