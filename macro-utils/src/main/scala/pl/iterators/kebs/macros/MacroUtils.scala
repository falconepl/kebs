package pl.iterators.kebs.macros

import scala.reflect.macros._

abstract class MacroUtils {
  val c: whitebox.Context

  protected val _this = c.prefix.tree

  import c.universe._

  protected def assertCaseClass(t: Type, msg: => String) = {
    val sym         = t.typeSymbol
    val isCaseClass = sym.isClass && sym.asClass.isCaseClass
    if (!isCaseClass) c.abort(c.enclosingPosition, msg)
  }

  protected def caseAccessors(caseClassType: Type): List[MethodSymbol] =
    caseClassType.decls
      .collect {
        case decl if decl.isMethod && decl.asMethod.isCaseAccessor => decl.asMethod
      }
      .toList

  object Product1 {
    def unapply(t: Type): Option[MethodSymbol] = caseAccessors(t) match {
      case _1 :: Nil => Some(_1)
      case _         => None
    }
  }

  protected def companion(t: Type): Symbol = t.typeSymbol.companion
  protected def apply(caseClassType: Type) = q"${companion(caseClassType)}.apply"
  protected def inferImplicitValue(p: Type, msgIfNotFound: => String) = {
    val implicitTree = c.inferImplicitValue(p)
    if (implicitTree.isEmpty) c.abort(c.enclosingPosition, msgIfNotFound)
    implicitTree
  }

}
