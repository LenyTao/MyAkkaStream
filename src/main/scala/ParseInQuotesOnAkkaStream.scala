import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration.DurationInt

object ParseInQuotesOnAkkaStream extends App {

  implicit val system: ActorSystem = ActorSystem("QuickStart")
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

  val source: Source[Char, NotUsed] = Source(
    """Ignore
      |"text1" some other ignore "text2"
      |"text3 which is not completed
      |""".stripMargin.toCharArray)

  val runnable = source
    .via(
      Flow[Char].fold((None: Option[List[Char]], List.empty[String])) {
        case ((None, result), '\"') => (Some(Nil), result)
        case ((None, result), _) => (None, result)
        case ((Some(symbol), result), '\"') => (None, result.::(symbol.map(x => x.toString).reduce((a, b) => a + b)))
        case ((Some(symbol), result), char) => (Some(symbol.::(char)), result)
      }.map(x => x._2.map(x => x.reverse).reverse)
    ).toMat(Sink.head)(Keep.right)

  val result = runnable.run()
  result.onComplete(_ => system.terminate())
  result.onComplete(_ => println(Await.result(result, 1.second).equals(List("text1", "text2"))))
}



