package streams

import fs2.{Pure, Stream}

object Fixture {
  val justiceLeaguer = "Justice Leaguer"
  val avenger = "Avenger"
  val spider = "Spider"

  // Justice League
  val henryCavil: Actor = Actor(0, "Henry", "Cavill", justiceLeaguer)
  val galGodot: Actor = Actor(1, "Gal", "Godot", justiceLeaguer)
  val ezraMiller: Actor = Actor(2, "Ezra", "Miller", justiceLeaguer)
  val benFisher: Actor = Actor(3, "Ben", "Fisher", justiceLeaguer)
  val rayHardy: Actor = Actor(4, "Ray", "Hardy", justiceLeaguer)
  val jasonMomoa: Actor = Actor(5, "Jason", "Momoa", justiceLeaguer)

  // Avengers
  val scarlettJohansson: Actor = Actor(6, "Scarlett", "Johansson", avenger)
  val robertDowneyJr: Actor = Actor(7, "Robert", "Downey Jr.", avenger)
  val chrisEvans: Actor = Actor(8, "Chris", "Evans", avenger)
  val markRuffalo: Actor = Actor(9, "Mark", "Ruffalo", avenger)
  val chrisHemsworth: Actor = Actor(10, "Chris", "Hemsworth", avenger)
  val jeremyRenner: Actor = Actor(11, "Jeremy", "Renner", avenger)
  val tomHolland: Actor = Actor(13, "Tom", "Holland", s"$avenger-$spider") // only the current Spidey is an Avenger!

  // Spiders
  val tobeyMaguire: Actor = Actor(14, "Tobey", "Maguire", spider)
  val andrewGarfield: Actor = Actor(15, "Andrew", "Garfield", spider)

  val avengerActors: List[Actor] = List(
    scarlettJohansson,
    robertDowneyJr,
    chrisEvans,
    markRuffalo,
    chrisHemsworth,
    jeremyRenner,
    tomHolland
  )

  val avengerActorsStream: Stream[Pure, Actor] = Stream.emits(avengerActors)

  val justiceLeagueActors: List[Actor] = List(
    henryCavil,
    galGodot,
    ezraMiller,
    benFisher,
    rayHardy,
    jasonMomoa
  )

  val justiceLeagueActorsStream: Stream[Pure, Actor] = Stream.emits(justiceLeagueActors)

  val spiderMenActors: List[Actor] = List(
    tomHolland,
    tobeyMaguire,
    andrewGarfield
  )

  val spiderMenActorsStream: Stream[Pure, Actor] = Stream.emits(spiderMenActors)
}
