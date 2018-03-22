import org.scalatest.{FunSuite, Matchers}
import pl.iterators.kebs.tagged.slickSupport.SlickSupport
import slick.lifted.Isomorphism

class TaggedTypeIsomorphismTests extends FunSuite with Matchers with SlickSupport {
  import pl.iterators.kebs.tagged._

  trait Tag1

  type Simple = Int @@ Tag1
  object Simple {
    def apply(i: Int) = i.@@[Tag1]
  }

  test("implicit isomorphism between bare type and type with tag") {
    val iso = implicitly[Isomorphism[Int @@ Tag1, Int]]
    iso.map(Simple(10)) shouldBe 10
    iso.comap(10) shouldBe Simple(10)
  }

  test("implicit isomorphism between bare type and type with tag (alias)") {
    val iso = implicitly[Isomorphism[Simple, Int]]
    iso.map(Simple(10)) shouldBe 10
    iso.comap(10) shouldBe Simple(10)
  }

}
