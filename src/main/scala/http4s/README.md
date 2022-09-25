The http4s library is based on the concepts of Request and Response. Indeed, we respond to a Request through a set of
functions of type:

Request => Response

We call these functions routes, and a server is nothing more than a set of routes.

Very often, producing a Response from a Request means interacting with databases, external services, and so on, which
may have some side effects. However, as diligent functional developers, we aim to maintain the referential transparency
of our functions. Hence, the library surrounds the Response type into an effect F[_]. So, we change the previous route
definition to:

Request => F[Response]

Nevertheless, not all the Request will find a route to a Response. So, we need to take into consideration this fact,
defining a route as a function of type:

Request => F[Option[Response]]

Using a monad transformer, we can translate this type to:

Request => OptionT[F, Response]

Finally, using the types Cats provides us, we can rewrite the type Request => OptionT[F, Response] using the Kleisli
monad transformer. Remembering that the type:

Kleisli[F[_], A, B]

is just a wrapper around the function

A => F[B]

our route definition becomes

Kleisli[OptionT[F, *], Request, Response]

Easy, isn’t it?

Request => OptionT[F, Response]

Fortunately, the http4s library defines a type alias for the Kleisli monad transformer that is easier to understand for
human beings: HttpRoutes[F].

Looking at function definitions:

(1) HttpRoutes

HttpRoutes[F[_]] = Http[OptionT[F, *], F]

HttpRoutes is a kleisli with a Request input and a Response output, such that the response effect is an optional
inside the effect of the request and response bodies. HTTP routes can conveniently be constructed from a partial
function and combined as a SemigroupK.

Type parameters:

F – the effect type of the Request and Response bodies, and the base monad of the OptionT in which the response
is returned.

(2) Http

Http[F[_], G[_]] = Kleisli[F, Request[G], Response[G]]

Http is a kleisli with a Request input and a Response output. This type is useful for writing middleware that
are polymorphic over the return type F. Type parameters:

F – the effect type in which the Response is returned
G – the effect type of the Request and Response bodies

A Request encapsulates the entirety of the incoming HTTP request including the status line, headers, and a
possible request body.

(3) Request

final class Request[+F[_]] private (
  val method: Method,            // method [[Method.GET]], [[Method.POST]], etc.
  val uri: Uri,                  // uri representation of the request URI
  val httpVersion: HttpVersion,  // the HTTP version
  val headers: Headers,          // collection of [[Header]]s
  val entity: Entity[F],         // entity [[Entity]] defining the body of the request
  val attributes: Vault,         // Immutable Map used for carrying additional information in a type safe fashion
) extends Message[F]...

(4) Response 

final class Response[+F[_]] private (
  val status: Status,            // [[Status]] code and message
  val httpVersion: HttpVersion,  // the HTTP version
  val headers: Headers,          // [[Headers]] containing all response headers
  val entity: Entity[F],         // [[Entity]] representing the possible body of the response
  val attributes: Vault,         // [[org.typelevel.vault.Vault]] containing additional parameters which may be used 
                                 // by the http4s backend for additional processing such as java.io.File object
) extends Message[F]...

Response is a representation of the HTTP response to send back to the client

Finally, combining the function definitions:

HttpRoutes[F[_]] = Http[OptionT[F, *], F]

where

Http[F[_], G[_]] = Kleisli[F, Request[G], Response[G]]

So;

HttpRoutes[F[_]] = Kleisli[OptionT[F, *], Request[F], Response[F]]

(5) HttpApp

type HttpApp[F[_]] = Http[F, F]
                   = Kleisli[F, Request[F], Response[F]]
                   = Request[F] => F[Response[F]]

HttpApp is a kleisli with a [[Request]] input and a [[Response]] output, such that the response effect is
the same as the request and response bodies'. An HTTP app is total on its inputs.  An HTTP app may be run
by a server, and a client can be converted to or from an HTTP app. F is the effect type in which the
[[Response]] is returned, and also of the [[Request]] and [[Response]] bodies.
