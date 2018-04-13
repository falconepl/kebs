package pl.iterators.kebs.tag.meta

import scala.collection.immutable
import scala.meta._

private[meta] object MetaModel {
  import MetaUtils._

  sealed abstract class TagTypeRep {
    def name: Type.Name
    final def termName = Term.Name(name.value)
    def matches(t: Type): Boolean
    def taggedWith(argName: Term.Name, typeParams: immutable.Seq[Type.Param]): Term
  }
  object TagTypeRep {
    private case class EmptyTrait(name: Type.Name) extends TagTypeRep {
      val nameString = name.value
      override def matches(t: Type): Boolean = t match {
        case Type.Name(`nameString`) => true
        case _                       => false
      }
      override def taggedWith(argName: Term.Name, typeParams: immutable.Seq[Type.Param]) = q"$argName.taggedWith[$name]"
    }

    private case class GenericTrait(name: Type.Name, typeParams: immutable.Seq[Type.Param]) extends TagTypeRep {
      require(typeParams.nonEmpty)

      val nameString           = name.value
      private val paramsLength = typeParams.length

      override def matches(t: Type): Boolean = t match {
        case Type.Apply(Type.Name(`nameString`), tParams) => tParams.lengthCompare(paramsLength) == 0
        case _                                            => false
      }
      override def taggedWith(argName: Term.Name, typeParams: immutable.Seq[Type.Param]) =
        q"$argName.taggedWith[${applied(name, typeParams)}]"
    }

    def apply(name: Type.Name, typeParams: immutable.Seq[Type.Param]): TagTypeRep =
      if (typeParams.isEmpty) EmptyTrait(name) else GenericTrait(name, typeParams)
  }

  case class TaggedType(name: Type.Name,
                        tparams: immutable.Seq[Type.Param],
                        baseType: Type,
                        tagType: TagTypeRep,
                        maybeCompanion: Option[Defn.Object]) {
    import TaggedType._

    final def hasValidations: Boolean =
      maybeCompanion
        .flatMap(_.templ.stats)
        .exists(_.exists {
          case ValidationMethod() => true
          case _                  => false
        })

    def generateCompanion: Defn.Object = maybeCompanion match {
      case Some(CompanionWithApply(companion)) => companion
      case Some(companion) =>
        q"..${companion.mods} object ${companion.name} { ..${List(TagPackageImport, generateApplyMethod, generateFromMethod) ++ companion.templ.stats
          .getOrElse(List.empty)} }"
      case None => q"object ${Term.Name(name.value)} { ..${List(TagPackageImport, generateApplyMethod, generateFromMethod)} }"
    }

    private def generateFromMethod = {
      val arg = Term.Name("arg")
      val body =
        if (hasValidations)
          q"${Term.Name(ValidationMethodName)}($arg).right.map(arg1 => ${tagType.taggedWith(Term.Name("arg1"), tparams)})"
        else tagType.taggedWith(arg, tparams)

      q"def from[..$tparams](arg: $baseType) = $body"
    }

    private def generateApplyMethod = {
      val arg      = Term.Name("arg")
      val fromCall = if (tparams.nonEmpty) q"from[..${reified(tparams)}]($arg)" else q"from($arg)"
      val body =
        if (hasValidations)
          q"$fromCall.getOrElse(throw new IllegalArgumentException($arg.toString))"
        else fromCall

      q"def apply[..$tparams](arg: $baseType) = $body"
    }
  }
  object TaggedType {
    val ValidationMethodName = "validate"
    val TagTypeName          = "@@"
    val TagPackage           = importer"_root_.pl.iterators.kebs.tag._"
    val TagPackageImport     = q"import ..${List(TagPackage)}"

    object ValidationMethod {
      //validation must be a method which is:
      //  - public
      //  - named with {ValidationMethodName}
      //  - has no type parameters
      //  - takes a single argument
      //  - it needs to return an Either instance but this is not enforced here (will result in a compilation error later)
      def unapply(stat: Stat): Boolean = stat match {
        case Defn.Def(IsPublic(), name, Seq(), Seq(Seq(_)), _, _) => name.value == ValidationMethodName
        case _                                                    => false
      }
    }
    object TaggedTypeDef {
      def unapply(stat: Stat): Option[(Type.Name, immutable.Seq[Type.Param], Type, Type)] = stat match {
        case Defn.Type(_, name, params, Type.ApplyInfix(base, IsTagType(), tag)) => Some((name, params, base, tag))
        case Defn.Type(_, name, params, Type.Apply(IsTagType(), Seq(base, tag))) => Some((name, params, base, tag))
        case _                                                                   => None
      }
    }

    //tagged types are aliases like type T = T @@ U where U is a tag (see below)
    def findAll(stats: immutable.Seq[Stat], tagTypes: immutable.Seq[TagTypeRep]): immutable.Seq[TaggedType] =
      if (tagTypes.isEmpty) immutable.Seq.empty
      else
        stats.flatMap {
          case TaggedTypeDef((name, params, base, tag)) =>
            tagTypes.find(_.matches(tag)).map(tt => TaggedType(name, params, base, tt, findCompanion(stats, name)))
          case _ => None
        }
  }

  def findTagTypes(stats: immutable.Seq[Stat]): immutable.Seq[TagTypeRep] = stats.collect {
    case Defn.Trait(_, tagName, tparams, _, body) if body.stats.isEmpty => TagTypeRep(tagName, tparams)
  }

  val TagTypeName = "@@"
  object IsTagType {
    def unapply(t: Type): Boolean = t match {
      case Type.Name(`TagTypeName`) | Type.Select(_, Type.Name(`TagTypeName`)) => true
      case _                                                                   => false
    }
  }

}
