package p1

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.comcast.ip4s.{ipv4, port}
import fs2.io.net.Network
import fs2.io.net.tls.TLSParameters
import org.http4s.{AuthedRoutes, Entity, HttpRoutes, Request, Response}
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.server.middleware.authentication.DigestAuth
import org.http4s.server.{AuthMiddleware, Router, Server}
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.io.File
import java.security.KeyStore
import java.time.LocalDateTime
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import scala.util.{Failure, Success, Try}


// Models
trait Inbound
case class MessageTypeA(message: String) extends Inbound
case class MessageTypeB(timestamp: String, pos: Int, flag: Boolean) extends Inbound

trait Outbound
case class SuccessInfo(timestamp: String, message: String)
case class OutboundSuccess(info: SuccessInfo) extends Outbound
case class FailureReason(timestamp: String, reason: String)
case class OutboundFailure(reason: FailureReason) extends Outbound

object Codecs {
  import cats.syntax.functor._ // Enables the `widen` method

  implicit val messageTypeADecoder: Decoder[MessageTypeA] = deriveDecoder
  implicit val messageTypeBDecoder: Decoder[MessageTypeB] = deriveDecoder
  implicit val inboundDecoder: Decoder[Inbound] = Decoder[MessageTypeA].widen.or(Decoder[MessageTypeB].widen)

  implicit val successInfoEncoder: Encoder[SuccessInfo] = deriveEncoder
  implicit val outboundSuccessEncoder: Encoder[OutboundSuccess] = deriveEncoder
  implicit val failureReasonEncoder: Encoder[FailureReason] = deriveEncoder
  implicit val outboundFailureEncoder: Encoder[OutboundFailure] = deriveEncoder
}

case class User(id: Long, name: String)

val passMap: Map[String, (Long, String, String)] = Map[String, (Long, String, String)](
  "jurgen" -> (1L, "127.0.0.1", "pw123")
)

object DigestAuthImpl{
  import org.http4s.server.middleware.authentication.DigestAuth.Md5HashedAuthStore

  private val ha1 = (username: String, realm: String, pw: String) => {
    Md5HashedAuthStore.precomputeHash[IO](username, realm, pw)
  }

  private val funcPass: String => IO[Option[(User, String)]] = (usr_name: String) =>
    val cleaned = usr_name.toLowerCase
    passMap.get(cleaned) match
      case Some((id,realm, pw)) => ha1(cleaned,realm, pw).flatMap(hash => IO(Some(User(id, cleaned), hash)))
      case None => IO(None)

  def middleware: String => IO[AuthMiddleware[IO, User]] = (realm: String) =>
    DigestAuth.applyF[IO, User](realm, Md5HashedAuthStore(funcPass))


}

object SimpleTcpServer extends IOApp with com.typesafe.scalalogging.LazyLogging{

  import Codecs._

  private def digestRoutes = AuthedRoutes.of[User, IO]{
    case req@GET -> Root / "login" as user =>
      Ok(s"Welcome $user")
    
  }

  private val digestService = DigestAuthImpl.middleware("127.0.0.1").map(wrapper => wrapper(digestRoutes))

  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case req @ POST -> Root / "proc" =>
      req
        .as[Inbound]
        .map {
          case MessageTypeA(message) =>
            OutboundSuccess(SuccessInfo(LocalDateTime.now.toString, s"Msg received: $message"))
          case MessageTypeB(timestamp, pos, flag) =>
            OutboundSuccess(SuccessInfo(LocalDateTime.now.toString, s"Flag received: $timestamp, $pos, $flag"))
        }
        .handleError(e => OutboundFailure(FailureReason(LocalDateTime.now.toString, e.getMessage)))
        .flatMap {
          case success: OutboundSuccess => Ok(success)
          case failure: OutboundFailure => BadRequest(failure)
        }
  }


  private val router: Resource[IO, HttpRoutes[IO]] =
    for {
      secureRoutes <- Resource.eval(digestService) // Lift IO[HttpRoutes[IO]] into Resource
      combinedRoutes = Router(
        "/" -> routes,
        "/s" -> secureRoutes
      )
    } yield combinedRoutes

  val SSLContext: Option[SSLContext] = {
    Try {
      val ksFile = new File("src/main/resources/sec/ks/myserver.jks")
      val keystorePass = "hokkokeystore".toCharArray
      val keyStore = KeyStore.getInstance(ksFile, keystorePass)
      val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      keyManagerFactory.init(keyStore, keystorePass)

      val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
      sslContext.init(keyManagerFactory.getKeyManagers, null, null)
      sslContext
    } match
      case Failure(exception) =>
        println(exception.getMessage)
        None
      case Success(value) => Some(value)
  }

  private val tls = Network[IO].tlsContext.fromSSLContext(SSLContext.orNull)

  private val serverResource: Resource[IO, Server] = {
    implicit val logging: Slf4jFactory[IO] = Slf4jFactory.create[IO]
    logger.info("Server starting")


    def logHeadersMiddleware(routes: HttpRoutes[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req@_ =>
        // Log the headers of every request
        logger.info(s"Received request with headers: ${req.headers.headers.mkString("\n")}")
        routes(req).getOrElseF(InternalServerError()) // Forward the request to the next route in the chain
    }

    router.flatMap { app =>
      val logged = logHeadersMiddleware(app)
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withTLS(tls, TLSParameters.Default)
        .withHttp2
        .withConnectionErrorHandler{ case error =>
          IO.println(error.getMessage).as(Response(status = BadRequest, entity = Entity.utf8String(error.getMessage)))
        }
        .withHttpApp(logged.orNotFound)
        .build
    }

  }

  override def run(args: List[String]): IO[ExitCode] =
    serverResource.useForever

}
