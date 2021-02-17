import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{FlowShape, UniformFanInShape, UniformFanOutShape}
import akka.stream.scaladsl.{Broadcast, FileIO, Flow, Framing, GraphDSL, Merge, Sink}
import akka.util.ByteString

import java.nio.file.Paths
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object MyHomeWork_WC extends App {
  implicit val system: ActorSystem = ActorSystem("QuickStart")
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val flowSystem = GraphDSL.create[FlowShape[ByteString, (String, Int)]]() {
    implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val bcast: UniformFanOutShape[String, String] = builder.add(Broadcast[String](3))
      val merge: UniformFanInShape[(String, Int), (String, Int)] = builder.add(Merge[(String, Int)](3))

      val flow_converter = Framing.delimiter(ByteString(" "), 50_331_648, true).map(_.utf8String)

      val flow_CounterLetter = Flow[String]
        .map(s => s.replaceAll(("[^a-zA-Z]"), ""))
        .map(x => x.length).fold(0)(_ + _)
        .map(x => "CounterLetter" -> x)

      val flow_CounterWords = Flow[String]
        .map(x => x.replaceAll("[^a-zA-Z ]", " ").split(" ").filter(_ != ""))
        .map(x => x.length)
        .fold(0)(_ + _).map(x => "CounterWords" -> x)

      val flow_CounterLine = Flow[String]
        .map(x => x.toCharArray)
        .map(x => x.filter(_ == '\n'))
        .map(x => x.length)
        .fold(1)(_ + _)
        .map(x => ("CounterLine" -> x))

      val inputFlow = builder.add(Flow[ByteString])
      val outputFlow = builder.add(Flow[(String, Int)])

      inputFlow.out ~> flow_converter ~> bcast
      bcast ~> flow_CounterLetter ~> merge
      bcast ~> flow_CounterWords ~> merge
      bcast ~> flow_CounterLine ~> merge
      merge ~> outputFlow.in
      FlowShape(inputFlow.in, outputFlow.out)
  }

  val source = FileIO.fromPath(Paths.get("stih.txt"))
  val sink: Sink[(String, Int), Future[Seq[(String, Int)]]] = Sink.seq[(String, Int)]
  val pipeline: Future[Seq[(String, Int)]] = source.via(flowSystem).runWith(sink)

  pipeline.onComplete(_ => system.terminate())

  val result: Seq[(String, Int)] = Await.result(pipeline, 2.second)
  println(result)
}

