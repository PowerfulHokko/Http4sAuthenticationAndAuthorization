package com.hokko
package p1

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.comcast.ip4s.port
import fs2.io.net.tls.TLSContext
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.*
import org.http4s.dsl.io.*
import org.http4s.dsl.io.{->, GET, Root}

import java.io.{File, FileInputStream}
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.middleware.HttpsRedirect
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.security.KeyStore
import javax.net.ssl
import scala.util.{Failure, Success, Try, Using}

object ServerWithTLS extends IOApp {

  val SSLContext: Option[SSLContext] = {
    Try{
        val ksFile = new File("src/main/resources/sec/ks/myserver.jks")
        val keystorePass = "hokkokeystore".toCharArray
        val keyStore = KeyStore.getInstance(ksFile, keystorePass)
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        keyManagerFactory.init(keyStore, keystorePass)

        val sslContext = ssl.SSLContext.getInstance("TLS")
        sslContext.init(keyManagerFactory.getKeyManagers, null, null)
        sslContext
    } match
      case Failure(exception) => {
        println(exception.getMessage)
        System.exit(500)
        None
      }
      case Success(value) => Some(value)
  }

  def routes = HttpRoutes.of[IO]{
    case GET -> Root => Ok("Hello from TLS")
  }

  val tlsContext = TLSContext.Builder
    .forAsync[IO]
    .fromSSLContext(SSLContext.get)

  implicit val logging: Slf4jFactory[IO] = Slf4jFactory.create[IO]

  val httpsRedirect = HttpsRedirect(routes)
  val server: Resource[IO, Server] = EmberServerBuilder.default[IO]
    .withPort(port"9000")
    .withTLS(tlsContext).withErrorHandler{
      e => BadRequest(e.getMessage)
    }
    .withHttpApp(httpsRedirect.orNotFound)
    .build

  override def run(args: List[String]): IO[ExitCode] = server.useForever

}
