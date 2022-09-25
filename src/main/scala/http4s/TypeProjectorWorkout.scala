package http4s

import cats.{Functor, Semigroup}

// From https://underscore.io/blog/posts/2016/12/05/type-lambdas.html
object TypeProjectorWorkout {
  def main(args: Array[String]): Unit = {

    def showResult[T](r: T): String = s":\n  => $r"

    println("Type Lambdas!")
    println("=============")

    println("\n(1) Basics\n")

    // list is a type constructor, L is a type alias
    type L = List[Option[(Int, Double)]]

    // T is a type alias that takes a parameter
    type T[A] = Option[Map[Int, A]]

    val t: T[String] = Some(Map(1 -> "abc", 2 -> "xyz"))

    println(s"Option[Map[Int, A]] instantiated via T[String]${showResult(t)}")

    // trait Functor[F[_]] ... {
    //  def map[A, B](fa: F[A])(f: A => B): F[B]
    // }

    // Map from Option[A] to Option[B]
    type F1 = Functor[Option] // OK, as Option[T]

    def optionFunctor: F1 = new F1 {
      override def map[A, B](fa: Option[A])(f: A => B): Option[B] = fa.map(f)
    }

    val maybeString = Some("333")
    println(s"Functor[Option] mapping $maybeString to length${showResult(optionFunctor.map(maybeString)(_.length))}")

    // Map from List[A] to List[B]
    type F2 = Functor[List] // OK, as List[T]

    def listFunctor: F2 = new F2 {
      override def map[A, B](fa: List[A])(f: A => B): List[B] = fa.map(f)
    }

    val ascendingList = List(1, 2, 3)
    println(s"Functor[List] triple each element of $ascendingList${showResult(listFunctor.map(ascendingList)(_ * 3))}")

    // Map from Map[A] to Map[B] ???
    // type F3 = Functor[Map]    // Not OK, as Map[K,V]; error is "Map takes 2 type parameters, expected: 1"

    // Type aliases are often used to ‘partially apply’ a type constructor,
    // and so to ‘adapt’ the kind of the type to be used

    type IntKeyMap[A] = Map[Int, A]

    // IntKeyMap now takes a single type parameter, and the compiler is happy with that.

    // Map from IntKeyMap[A] to IntKeyMap[B]
    // i.e. from Map[Int, A] to Map[Int, B]
    type F3 = Functor[IntKeyMap] // ok

    def mapWithIntKeyFunctor: F3 = new F3 {
      override def map[A, B](fa: IntKeyMap[A])(f: A => B): IntKeyMap[B] = fa.map {
        case (k, v) => (k + 1, f(v))
      }
    }

    val farmMap = Map(1 -> "how", 2 -> "now", 3 -> "brown", 4 -> "cow")
    val reverseMapValues = mapWithIntKeyFunctor.map(farmMap)(_.reverse)
    println(s"Functor[Map[Int, *]] via type alias, reversing values of $farmMap${showResult(reverseMapValues)}")

    println("\n(2) Type Lambdas!\n")

    // Can we achieve the same goal without having to declare an alias?

    // For scala 2, There is currently no direct syntax for partial application of type constructors in Scala

    // But we can use a Type lambda

    // This type lambda represents a Functor that maps from T[A] to T[B] i.e.
    // from Map[Int, A] to Map[Int, B] (by substituting A and B into T in turn)

    // OK, remember Functor[F[_]]
    type F4 = Functor[({type T[A] = Map[Int, A]})#T]

    // The above is the equivalent of;

    // type T[A] = Map[Int, A]
    // type F4 = Functor[T]

    // Note that we can declare a local variable of type TT[A] (similar to how we have previously said above):
    type TT[A] = Map[Int, A]
    val tt: TT[String] = Map(1 -> "abc", 2 -> "xyz")
    println(s"Map[Int, A] instantiated via TT[String]${showResult(tt)}")

    // But we cannot declare a local variable using the lambda syntax: it is only valid in the context of a HKT

    // We can read the type lambda syntax as: declaring an anonymous type, inside of which we define the
    // desired type alias, and then accessing its type member with the # syntax.

    def mapWithIntKeyFunctor2: F4 = new F4 {
      override def map[A, B](fa: Map[Int, A])(f: A => B): Map[Int, B] = fa.map {
        case (k, v) => (k + 1, f(v))
      }
    }

    val aMap = Map(1 -> 2.0, 2 -> 3.0, 3 -> 4.0, 4 -> 5.0)
    println(
      s"Functor[Map[Int, *]] via type lambda, incrementing key by 1 and squaring values of $aMap" +
        showResult(mapWithIntKeyFunctor2.map(aMap)(d => d * d))
    )

    // A type lambda is harder to read than using an extra line to declare a type alias the traditional way.

    // You should use a type alias whenever possible to keep your code clean and readable.

    // HOWEVER, sometimes type lambdas are unavoidable. Consider the following rather ABSTRACT EXAMPLE:

    // def foo[A[_, _], B](functor: Functor[A[B, ?]]) // won't compile, "Cannot resolve symbol ?"

    // Consider this type lambda
    def foo[A[_, _], B](functor: Functor[({type T[C] = A[B, C]})#T]): Functor[({type T[C] = A[B, C]})#T] = functor

    // Let's look closer at what the type lambda defines
    // Remember functor definition
    // trait Functor[F[_]] extends Invariant[F] {
    //   def map[A, B](fa: F[A])(f: A => B): F[B]
    //   ...
    // }

    // To construct such a lambda we need a function where A[_, _] and B are already in scope, so we'll rename the
    // types of the map method from [A, B] to [S, T] to avoid type shadowing. We get:
    def aFunctor[A[_, _], B]: Functor[({type TT[C] = A[B, C]})#TT] = new Functor[({type TT[C] = A[B, C]})#TT] {
      // The above type lambda represents a Functor that maps from TT[S] to TT[T], where TT[C] = A[B, C].
      // Substituting S and T into TT in turn, we see that the lambda maps from A[B, S] to A[B, T].

      // It's difficult to implement this method without knowing more about the types
      override def map[S, T](fa: A[B, S])(f: S => T): A[B, T] = ???
    }

    // Can we use aFunctor method and pass the functor it returns as an argument to the foo method?
    val mapWithStringKeyFunctor: Functor[({type TT[C] = Map[String, C]})#TT] = aFunctor[Map, String]
    val fooMapWithStringKeyFunctor: Functor[({type TT[C] = Map[String, C]})#TT] = foo(mapWithStringKeyFunctor) // Compiles OK

    // val f = aFunctor
    // val fooFFunctor = foo(f) // Doesn't compile, we need to line up the types :)

    def lineUpTheTypes[A[_, _], B]: Functor[({type TT[C] = A[B, C]})#TT] = {
      foo(aFunctor[A, B]) // Compiles OK
    }

    // This time let's reimplement aFunctor, but give a bit more typing help so we can write a
    // sensible implementation of the map method

    // Note that two type hints use different techniques;
    // A uses upper type bound (https://docs.scala-lang.org/tour/upper-type-bounds.html)
    // B uses context bound (https://stackoverflow.com/questions/2982276/what-is-a-context-bound-in-scala)
    def anotherFunctor[A[K, V] <: Map[K, V], B: Semigroup]: Functor[({type TT[C] = A[B, C]})#TT] =
      new Functor[({type TT[C] = A[B, C]})#TT] {
        override def map[S, T](fa: A[B, S])(f: S => T): A[B, T] = fa.map {
          case (k, v) => (Semigroup[B].combine(k, k), f(v))
        }.asInstanceOf[A[B, T]]
      }

    // Can we use anotherFunctor method and pass the functor it returns as an argument to the foo method?
    val mapWithStringKeyAnotherFunctor: Functor[({type TT[C] = Map[String, C]})#TT] = anotherFunctor[Map, String]
    val fooMapWithStringKeyAnotherFunctor: Functor[({type TT[C] = Map[String, C]})#TT] = foo(mapWithStringKeyAnotherFunctor) // Compiles OK

    val alphabetMap = Map("1" -> "abc", "2" -> "xyz")
    println(
      s"Functor[Map[String, *]] via type lambda, combining key with itself and reversing values of $alphabetMap" +
        showResult(fooMapWithStringKeyAnotherFunctor.map(alphabetMap)(_.reverse))
    )

    // val f = anotherFunctor    // Doesn't compile, diverging implicit expansion for type cats.Semigroup[B]
    // val fooFAnotherFunctor = foo(f)

    def lineUpTheTypes2[A[K, V] <: Map[K, V], B: Semigroup]: Functor[({type TT[C] = A[B, C]})#TT] = {
      foo(anotherFunctor[A, B]) // Compiles OK
    }

    val oneTwoMap = Map(1 -> 1, 2 -> 2)
    println(
      s"Functor[Map[Int, *]] via type lambda, combining key with itself and adding 4 to values of $oneTwoMap" +
        showResult(lineUpTheTypes2[Map, Int].map(oneTwoMap)(_ + 4))
    )

    println("\n(3) Splitting foo definition\n")

    // If we prefer not to use type lambdas we can split the definition of foo in two:

    class Foo[A[_, _], B] {
      type AB[C] = A[B, C]

      def apply(functor: Functor[AB]): Functor[AB] = functor // Apply in a class :)
    }

    def aFoo[A[_, _], B] = new Foo[A, B]

    // Let's kick aFoo's tyres
    val aFooMapWithStringKeyFunctor: Functor[({type TT[C] = Map[String, C]})#TT] = aFoo[Map, String](mapWithStringKeyFunctor)

    // val f = aFunctor
    // val aFooFAFunctor = aFoo(f) // Doesn't compile, we need to line up the types :)

    def lineUpTheTypes3[A[_, _], B]: Functor[({type TT[C] = A[B, C]})#TT] = {
      aFoo[A, B](aFunctor[A, B]) // Compiles OK
    }

    def lineUpTheTypes4[A[K, V] <: Map[K, V], B: Semigroup]: Functor[({type TT[C] = A[B, C]})#TT] = {
      aFoo[A, B](anotherFunctor[A, B]) // Compiles OK
    }

    val roloMap = Map("ro" -> 1, "lo" -> 2)
    println(
      s"Functor[Map[String, *]] via Foo, combining key with itself and dividing values by 1 of $roloMap" +
        showResult(lineUpTheTypes4[Map, String].map(roloMap)(_ / 1))
    )

    val aFooMapWithStringKeyFunctor2 = aFoo[Map, String](mapWithStringKeyAnotherFunctor)
    println(
      s"Functor[Map[String, *]] via Foo, combining key with itself and reversing values of $alphabetMap" +
        showResult(aFooMapWithStringKeyFunctor2.map(alphabetMap)(_.reverse))
    )

    println("\n(4) Kind projector plugin\n")

    // Using kind projector plugin (https://github.com/typelevel/kind-projector)
    type F = Functor[Map[Int, *]] // now works!

    def mapKindProjected: F = new F {
      override def map[A, B](fa: Map[Int, A])(f: A => B): Map[Int, B] = fa.map {
        case (k, v) => (k, f(v))
      }
    }

    println(
      s"Functor[Map[Int, *]] via kind projector, reversing values of $farmMap" +
        showResult(mapKindProjected.map(farmMap)(_.reverse))
    )

    def fooKind[A[_, _], B](functor: Functor[A[B, *]]): Functor[A[B, *]] = functor // now works!

    println(
      s"Functor[Map[Int, *]] via kind projector take 2 (fooKind), reversing values of $farmMap" +
        showResult(fooKind(mapKindProjected).map(farmMap)(_.reverse))
    )

    def aKindFunctor[A[K, V] <: Map[K, V], B: Semigroup]: Functor[A[B, *]] = new Functor[A[B, *]] {
      override def map[S, T](fa: A[B, S])(f: S => T): A[B, T] = fa.map {
        case (k, v) => (Semigroup[B].combine(k, k), f(v))
      }.asInstanceOf[A[B, T]]
    }

    println(
      s"Functor[Map[String, *]] via kind projector, combining key with itself and multiplying values by 10 of $roloMap" +
        showResult(aKindFunctor[Map, String].map(roloMap)(_ * 10))
    )

    // As per readme, Tuple2[*, Double] is equivalent to: type R[A] = Tuple2[A, Double]

    type R[A] = Tuple2[A, Double]

    def swap(t: R[_]): (Double, _) = t.swap

    val tuple = ("egg", 1.0)
    println(s"swap $tuple${showResult(swap(tuple))}")

    // Which suggests (to me)...

    // def swap1(t: Tuple2[*, Double]): (Double, _) = t.swap

    // But this doesn't compile, error, type Λ$ takes type parameters

    // As with type lambdas above, kind projectors can only be used with HKT's,

    // Can I use  type lambda for this? Nope!
    // Error (implied), type T takes type parameters
    // def swap2(t: ({type T[A] = Tuple2[A, Double]})#T): (Double, _) = t.swap

    def aTupleFunctor1: Functor[R] = new Functor[R] {
      override def map[A, B](fa: Tuple2[A, Double])(f: A => B): Tuple2[B, Double] = (f(fa._1), fa._2)
    }

    def aTupleFunctor2: Functor[Tuple2[*, Double]] = new Functor[Tuple2[*, Double]] {
      override def map[A, B](fa: (A, Double))(f: A => B): (B, Double) = (f(fa._1), fa._2)
    }

    val tuple2 = (1, 1.0)
    println(s"Functor[Tuple2[A, Double]] map $tuple2 Int key to double${showResult(aTupleFunctor1.map(tuple2)(_.toDouble))}")

    val tuple3 = ("Hello", 1.0)
    println(s"Functor[Tuple2[A, Double]] map $tuple3 String key to length${showResult(aTupleFunctor1.map(tuple3)(_.length))}")

    println(s"Functor[Tuple2[*, Double]] map $tuple2 Int key to double${showResult(aTupleFunctor2.map(tuple2)(_.toDouble))}")

    val tuple4 = (2, 1.0)
    println(s"Functor[Tuple2[*, Double]] quadruple $tuple4 Int key${showResult(aTupleFunctor2.map(tuple4)(_ * 4))}")
  }
}

// Did you find this article? It covers the new context bound feature, within the context of array improvements.
//
// Generally, a type parameter with a context bound is of the form [T: Bound];
// it is expanded to plain type parameter T together with an implicit parameter of type Bound[T].
//
//Consider the method tabulate which forms an array from the results of applying a given function f on a range of numbers from 0 until a given length. Up to Scala 2.7, tabulate could be written as follows:
//
//def tabulate[T](len: Int, f: Int => T) = {
//    val xs = new Array[T](len)
//    for (i <- 0 until len) xs(i) = f(i)
//    xs
//}
//In Scala 2.8 this is no longer possible, because runtime information is necessary to create the right representation of Array[T]. One needs to provide this information by passing a ClassManifest[T] into the method as an implicit parameter:
//
//def tabulate[T](len: Int, f: Int => T)(implicit m: ClassManifest[T]) = {
//    val xs = new Array[T](len)
//    for (i <- 0 until len) xs(i) = f(i)
//    xs
//}
//As a shorthand form, a context bound can be used on the type parameter T instead, giving:
//
//def tabulate[T: ClassManifest](len: Int, f: Int => T) = {
//    val xs = new Array[T](len)
//    for (i <- 0 until len) xs(i) = f(i)
//    xs
//}