package domain

case class Director(firstName: String, lastName: String) {
  override def toString: String = s"$firstName $lastName"
}
