import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{FlowShape, UniformFanInShape, UniformFanOutShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object MyHomeWork_WC extends App {
  val flowSystem = GraphDSL.create[FlowShape[Char, Int]]() {
    implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val bcast: UniformFanOutShape[Char, Char] = builder.add(Broadcast[Char](3))
      val merge: UniformFanInShape[Int, Int] = builder.add(Merge[Int](3))

      val flow_CounterSymbols = Flow[Char].filterNot { s => s.toString matches ("[^a-zA-Z]") }.map(x => 1).fold(0)(_ + _)
      val flow_CounterWords = Flow[Char].fold((None: Option[List[Char]], 0)) {
        case ((None, result), char) if (char.isLetter) =>
          (Some(List(char)), result)
        case ((None, result), _) =>
          (None, result)
        case ((Some(symbol), result), char) if (!char.isLetter) =>
          (None, result + 1)
        case ((Some(symbol), result), char) =>
          (Some(symbol.:+(char)), result)
      }.map(x => if (x._1.isEmpty) x._2 else x._2 + 1)

      val flow_CounterLine = Flow[Char].filter(_ == '\n').map(x => 1).fold(1)(_ + _)

      val inputFlow = builder.add(Flow[Char])
      val outputFlow = builder.add(Flow[Int])

      inputFlow.out ~> bcast
      bcast ~> flow_CounterSymbols ~> merge
      bcast ~> flow_CounterWords ~> merge
      bcast ~> flow_CounterLine ~> merge
      merge ~> outputFlow.in
      FlowShape(inputFlow.in, outputFlow.out)
  }

  val sink: Sink[Int, Future[Seq[Int]]] = Sink.seq[Int]
  implicit val system: ActorSystem = ActorSystem("QuickStart")
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

  val pipeline: Future[Seq[Int]] = Source(
    """I bought my wife and daughter
      |Multicolored stockings
      |Points, points
      |""".stripMargin.toCharArray
  ).via(flowSystem).runWith(sink)
  pipeline.onComplete(_ => system.terminate())
  val result = Await.result(pipeline, 1.second)
  println(
    s"""
       |CounterWords:   ${result.head}
       |CounterSymbols: ${result(1)}
       |CounterLine:    ${result(2)}
       |""".stripMargin
  )
}

