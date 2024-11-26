package com.hokko
package p1

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.port
import fs2.io.net.Network
import fs2.io.net.tls.TLSParameters
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.->
import org.http4s.dsl.io.{GET, Root}
import org.http4s.dsl.impl.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.io.File
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import scala.util.{Failure, Success, Try}

object TLS2 extends IOApp {

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

  val tls = Network[IO].tlsContext.fromSSLContext(SSLContext.orNull)

  def routes = HttpRoutes.of[IO]{
    case GET -> Root => Ok("Hello from TLS")
  }
  
  def server = {
    implicit val logging: Slf4jFactory[IO] = Slf4jFactory.create[IO]
    
    EmberServerBuilder.default[IO]
      .withTLS(tls, TLSParameters.Default)
      .withHttp2
      .withPort(port"9002")
      .withHttpApp(routes.orNotFound)
      .withErrorHandler{
        case error => BadRequest(error.getMessage)
      }
      .build
      .useForever
  }

  override def run(args: List[String]): IO[ExitCode] = server.as(ExitCode.Success)

}
