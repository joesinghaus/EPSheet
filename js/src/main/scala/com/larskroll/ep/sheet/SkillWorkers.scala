package com.larskroll.ep.sheet

import com.larskroll.roll20.facade.Roll20;
import com.larskroll.roll20.facade.Roll20.EventInfo;
import com.larskroll.roll20.sheet._
import SheetWorkerTypeShorthands._
import util.{ Success, Failure }
import concurrent.{ Future, Promise, ExecutionContext }
import scala.scalajs.js

object SkillWorkers extends SheetWorker {
  import EPCharModel._

  val activeSkillTotalCalc = bind(op(activeSkills.noDefaulting, activeSkills.linkedAptitude, activeSkills.morphBonus, activeSkills.ranks, cogTotal, cooTotal, intTotal, refTotal, savTotal, somTotal, wilTotal)) update {
    case (nodef, apt, morph, ranks, cog, coo, int, ref, sav, som, wil) => {
      import Aptitude._

      val aptTotal = ValueParsers.aptFrom(apt) match {
        case COO => coo
        case COG => cog
        case INT => int
        case REF => ref
        case SAV => sav
        case SOM => som
        case WIL => wil
      };
      val total = if (nodef) {
        if (ranks == 0) 0 else aptTotal + ranks + morph
      } else {
        aptTotal + ranks + morph
      };
      Seq(activeSkills.total <<= total)
    }
  }

  val knowledgeSkillTotalCalc = bind(op(knowledgeSkills.noDefaulting, knowledgeSkills.linkedAptitude, knowledgeSkills.morphBonus, knowledgeSkills.ranks, cogTotal, cooTotal, intTotal, refTotal, savTotal, somTotal, wilTotal)) update {
    case (nodef, apt, morph, ranks, cog, coo, int, ref, sav, som, wil) => {
      import Aptitude._

      val aptTotal = ValueParsers.aptFrom(apt) match {
        case COO => coo
        case COG => cog
        case INT => int
        case REF => ref
        case SAV => sav
        case SOM => som
        case WIL => wil
      };
      val total = if (nodef) {
        if (ranks == 0) 0 else aptTotal + ranks + morph
      } else {
        aptTotal + ranks + morph
      };
      Seq(knowledgeSkills.total <<= total)
    }
  }

  val skillTotalCalc = activeSkillTotalCalc.all(activeSkills).andThen(knowledgeSkillTotalCalc.all(knowledgeSkills));

  val museSkillTotalCalcOp = bind(op(museSkills.linkedAptitude, museSkills.ranks, museCog, museCoo, museInt, museRef, museSav, museSom, museWil)) update {
    case (apt, ranks, cog, coo, int, ref, sav, som, wil) => {
      import Aptitude._

      val aptTotal = ValueParsers.aptFrom(apt) match {
        case COO => coo
        case COG => cog
        case INT => int
        case REF => ref
        case SAV => sav
        case SOM => som
        case WIL => wil
      };
      val total = aptTotal + ranks;
      Seq(museSkills.total <<= total)
    }
  }

  val museSkillTotalCalc = museSkillTotalCalcOp.all(museSkills);

  val nop7: Option[Tuple7[Int, Int, Int, Int, Int, Int, Int]] => ChainingDecision = (f) => ExecuteChain;
  val museAptSkillCalc = bind(op(museCog, museCoo, museInt, museRef, museSav, museSom, museWil)) apply (nop7, museSkillTotalCalc);

  val skillCategoryCalc = bind(op(activeSkills.category)) update {
    case (catName) => {
      import Skills._

      val cat = SkillCategory.withName(catName);
      val catLabel = SkillCategory.dynamicLabelShort(cat);
      val globalModsExpression = modsForSkillCategory(cat);
      Seq(activeSkills.categoryShort <<= catLabel,
        activeSkills.globalMods <<= activeSkills.globalMods.valueFrom(globalModsExpression))
    }
  }
  val setSkillsGenerating = nop update { _ =>
    Seq(generateSkillsLabel <<= "generating-skills",
      generateMuseSkillsLabel <<= "generating-skills")
  };
  val unsetSkillsGenerating = nop update { _ =>
    Seq(generateSkillsLabel <<= "generate-skills",
      generateSkills <<= false,
      generateMuseSkillsLabel <<= "generate-skills",
      generateMuseSkills <<= false)
  }
  val unsetSkillsSorting = nop update { _ =>
    Seq(sortSkills <<= false)
  }

  onRemove(activeSkills, (_: Roll20.EventInfo) => { EPWorkers.searchFrayAndSetHalved(); () });

  private def getActiveSkills(): Future[List[Skills.ActiveSkillTuple]] = {
    import Skills.ActiveSkillTuple;
    getRowAttrs(activeSkills, Seq(activeSkills.rowId, activeSkills.skillName, activeSkills.category, activeSkills.linkedAptitude, activeSkills.field)).map(_.map {
      case (k, v) => ActiveSkillTuple(k, v(activeSkills.rowId), v(activeSkills.skillName), v(activeSkills.category), v(activeSkills.linkedAptitude), v(activeSkills.field))
    }.toList)
  }

  private def getKnowledgeSkills(): Future[List[Skills.KnowledgeSkillTuple]] = {
    import Skills.KnowledgeSkillTuple;
    getRowAttrs(knowledgeSkills, Seq(knowledgeSkills.rowId, knowledgeSkills.skillName, knowledgeSkills.linkedAptitude, knowledgeSkills.field)).map(_.map {
      case (k, v) => KnowledgeSkillTuple(k, v(knowledgeSkills.rowId), v(knowledgeSkills.skillName), v(knowledgeSkills.linkedAptitude), v(knowledgeSkills.field))
    }.toList)
  }

  val sortSkillsOp = op(sortSkillsBy) { sortByO: Option[String] =>
    import Skills.{ SortBy, ActiveSkillTuple, KnowledgeSkillTuple };
    import js.JSConverters._;

    sortByO match {
      case Some(sortByS) => {
        val sortBy = Skills.SortBy.withName(sortByS);
        if (sortBy == SortBy.None) {
          log(s"Field ${sortSkillsBy.name} is set to None. Not sorting."); Future.successful(())
        } else {
          val activeTuplesF = getActiveSkills();
          val knowledgeTuplesF = getKnowledgeSkills();
          val r = for {
            activeTuples <- activeTuplesF
            knowledgeTuples <- knowledgeTuplesF
          } yield {
            //debug(s"Sorting with $sortBy (${SortBy.activeOrdering(sortBy)} , ${SortBy.knowledgeOrdering(sortBy)}");
            val activeSorted = activeTuples.sorted(SortBy.activeOrdering(sortBy));
            //            val differenceActive = activeSorted.zip(activeTuples).filterNot(t => t._1 == t._2);
            //            debug(s"Difference after sorting:\n${differenceActive.mkString(";")}");
            val knowledgeSorted = knowledgeTuples.sorted(SortBy.knowledgeOrdering(sortBy));
            //            val differenceKnowledge = knowledgeSorted.zip(knowledgeTuples).filterNot(t => t._1 == t._2);
            //            debug(s"Difference after sorting:\n${differenceKnowledge.mkString(";")}");
            val activeSortedIds = activeSorted.map { x => x.sortId }.toArray;
            val knowledgeSortedIds = knowledgeSorted.map { x => x.sortId }.toArray;
            val data = Seq(activeSkills.reporder <<= activeSortedIds,
              knowledgeSkills.reporder <<= knowledgeSortedIds).toMap;
            setAttrs(data)
          };
          r flatMap identity
        }
      }
      case None => error(s"Field ${sortSkillsBy.name} has no value. Not sorting."); Future.successful(())
    }
  }

  val generateDefaultSkills = nop { _: Option[Unit] =>

    val activeNamesF = getRowAttrs(activeSkills, Seq(activeSkills.skillName)).map(_.flatMap {
      case (k, v) => v.apply(activeSkills.skillName)
    } toSet);
    val knowledgeNamesF = getRowAttrs(knowledgeSkills, Seq(knowledgeSkills.skillName)).map(_.flatMap {
      case (k, v) => v.apply(knowledgeSkills.skillName)
    } toSet);

    val r = for {
      activeNames <- activeNamesF;
      knowledgeNames <- knowledgeNamesF
    } yield {
      // double check there are no duplicate row ids generated (Roll20 seems to be doing that sometimes...)
      val dataNoDuplicates = Skills.pregen.foldLeft((Set.empty[String], Map.empty[FieldLike[Any], Any]))((acc, skill) => {
        acc match {
          case (ids, valueAcc) => {
            val ignore = if (skill.cls == Skills.SkillClass.Active) ignoreSkill(skill, activeNames) else ignoreSkill(skill, knowledgeNames);
            if (ignore) {
              (ids, valueAcc)
            } else {
              var curId: String = null;
              do {
                curId = Roll20.generateRowID();
              } while (ids.contains(curId))
              val skillValues = generateSkillWithId(curId, skill);
              (ids + curId, valueAcc ++ skillValues)
            }
          }
        }
      });
      //.flatten.toMap;
      setAttrs(dataNoDuplicates._2)
    };
    r flatMap identity
  };

  private def ignoreSkill(skill: Skill, existingNames: Set[String]): Boolean = {
    skill.field match {
      case Some("???") => false
      case _           => existingNames.contains(skill.name)
    }
  }

  val generateMuseDefaultSkills = nop { _: Option[Unit] =>

    val namesF = getRowAttrs(museSkills, Seq(museSkills.skillName)).map(_.flatMap {
      case (k, v) => v.apply(museSkills.skillName)
    } toSet);

    val defaultSkills = Map(
      "Academics" -> 50,
      "Hardware" -> 20,
      "Infosec" -> 20,
      "Interfacing" -> 30,
      "Profession" -> 50,
      "Research" -> 20,
      "Programming" -> 10,
      "Perception" -> 10,
      "[Custom]" -> 30);
    val defaultFields = Map(
      "Academics" -> "Psychology",
      "Hardware" -> "Electronics",
      "Profession" -> "Accounting");
    val customSkill = Skill("[Custom]", None, null, null, Aptitude.COG);

    val r = for {
      names <- namesF
    } yield {
      // double check there are no duplicate row ids generated (Roll20 seems to be doing that sometimes...)
      val dataNoDuplicates = (Skills.pregen ++ Seq(customSkill, customSkill, customSkill)).foldLeft((Set.empty[String], Map.empty[FieldLike[Any], Any]))((acc, skill) => {
        acc match {
          case (ids, valueAcc) => {
            val ignore = filterSkill(skill, names, defaultSkills.keySet);
            if (ignore) {
              (ids, valueAcc)
            } else {
              var curId: String = null;
              do {
                curId = Roll20.generateRowID();
              } while (ids.contains(curId))
              val skillValues = generateMuseSkillWithId(curId, skill, defaultSkills, defaultFields);
              (ids + curId, valueAcc ++ skillValues)
            }
          }
        }
      });
      //.flatten.toMap;
      setAttrs(dataNoDuplicates._2)
    };
    r flatMap identity
  };

  private def filterSkill(skill: Skill, exclude: Set[String], include: Set[String]): Boolean = {
    if (include.contains(skill.name)) {
      skill.field match {
        case Some("???") => false
        case _           => exclude.contains(skill.name) || !include.contains(skill.name)
      }
    } else {
      true
    }
  }

  private def generateMuseSkillWithId(id: String, skill: Skill, defaultValues: Map[String, Int], defaultFields: Map[String, String]): Seq[(FieldLike[Any], Any)] = {
    import museSkills._;
    Seq(
      museSkills.at(id, skillName) <<= skill.name,
      museSkills.at(id, field) <<= defaultFields.getOrElse(skill.name, field.resetValue),
      museSkills.at(id, linkedAptitude) <<= skill.apt.toString(),
      museSkills.at(id, ranks) <<= defaultValues.getOrElse(skill.name, ranks.resetValue),
      museSkills.at(id, total) <<= total.resetValue)
  }

  private def modsForSkillCategory(c: Skills.SkillCategory.SkillCategory): ArithmeticExpression[Int] = {
    import Skills.SkillCategory._;
    c match {
      case Combat | Physical => EPCharModel.globalPhysicalMods
      case _                 => EPCharModel.globalMods
    }
  }

  private def generateSkillWithId(id: String, skill: Skill): Seq[(FieldLike[Any], Any)] = {
    if (skill.cls == Skills.SkillClass.Active) {
      import activeSkills._;
      val globalModsExpression = modsForSkillCategory(skill.category);
      Seq(
        activeSkills.at(id, rowId) <<= id,
        activeSkills.at(id, skillName) <<= skill.name,
        activeSkills.at(id, field) <<= skill.field.getOrElse(field.resetValue),
        activeSkills.at(id, category) <<= skill.category.toString(),
        activeSkills.at(id, categoryShort) <<= Skills.SkillCategory.dynamicLabelShort(skill.category),
        activeSkills.at(id, specialisations) <<= specialisations.resetValue,
        activeSkills.at(id, linkedAptitude) <<= skill.apt.toString(),
        activeSkills.at(id, noDefaulting) <<= skill.noDefaulting,
        activeSkills.at(id, ranks) <<= ranks.resetValue,
        activeSkills.at(id, morphBonus) <<= morphBonus.resetValue,
        activeSkills.at(id, total) <<= total.resetValue,
        activeSkills.at(id, globalMods) <<= globalMods.valueFrom(globalModsExpression))
    } else {
      import knowledgeSkills._;
      Seq(
        knowledgeSkills.at(id, rowId) <<= id,
        knowledgeSkills.at(id, skillName) <<= skill.name,
        knowledgeSkills.at(id, field) <<= skill.field.getOrElse(field.resetValue),
        knowledgeSkills.at(id, specialisations) <<= specialisations.resetValue,
        knowledgeSkills.at(id, linkedAptitude) <<= skill.apt.toString(),
        knowledgeSkills.at(id, noDefaulting) <<= skill.noDefaulting,
        knowledgeSkills.at(id, ranks) <<= ranks.resetValue,
        knowledgeSkills.at(id, morphBonus) <<= morphBonus.resetValue,
        knowledgeSkills.at(id, total) <<= total.resetValue)
    }
  }

  onChange(generateSkills, (ei: EventInfo) => {
    for {
      _ <- setSkillsGenerating();
      _ <- generateDefaultSkills();
      _ <- skillTotalCalc();
      _ <- sortSkillsOp();
      _ <- EPWorkers.setFrayHalved();
      _ <- unsetSkillsGenerating()
    } yield ();
    ()
  })

  onChange(generateMuseSkills, (ei: EventInfo) => {
    for {
      _ <- setSkillsGenerating();
      _ <- generateMuseDefaultSkills();
      _ <- museSkillTotalCalc();
      _ <- unsetSkillsGenerating()
    } yield ();
    ()
  })

  onChange(sortSkills, (ei: EventInfo) => {
    for {
      _ <- sortSkillsOp();
      _ <- unsetSkillsSorting()
    } yield ();
    ()
  })

  onChange(activeSkills, (ei: EventInfo) => {
    val rowId = Roll20.getActiveRepeatingField();
    if (js.isUndefined(rowId)) {
      error(s"Ignoring active skills event as rowId is undefined:\n${js.JSON.stringify(ei)}");
      ()
    } else {
      debug(s"Updated rowId=${rowId} caused by:\n${js.JSON.stringify(ei)}");
      val data = Seq(activeSkills.at(rowId, activeSkills.rowId) <<= rowId).toMap;
      setAttrs(data);
      ()
    }
  })

  private[sheet] val morphSkillBoniCalc = op(morphSkillBoni) { skillBoniO: Option[String] =>
    val activeTuplesF = getActiveSkills();
    val knowledgeTuplesF = getKnowledgeSkills();
    val r = for {
      activeTuples <- activeTuplesF
      knowledgeTuples <- knowledgeTuplesF
    } yield {
      val data = skillBoniO match {
        case Some(skillBoniS) if !skillBoniS.isEmpty() => {
          val skillBoni = ValueParsers.skillsFrom(skillBoniS);
          debug(s"Applying Skill Boni:\n${skillBoni.mkString(",")}");
          val activeUpdates = activeTuples.map { t =>
            val bonus = skillBoni.flatMap { sm =>
              t.name.flatMap { tname =>
                if (sm.skill.equalsIgnoreCase(tname)) {
                  val fieldMatch = for {
                    smField <- sm.field;
                    tField <- t.field
                  } yield smField.equalsIgnoreCase(tField);
                  fieldMatch match {
                    case Some(true) | None => Some(sm.mod)
                    case Some(false)       => None
                  }
                } else None
              }
            }.sum;
            activeSkills.at(t.id, activeSkills.morphBonus) <<= bonus
          };
          val knowledgeUpdates = knowledgeTuples.map { t =>
            val bonus = skillBoni.flatMap { sm =>
              t.name.flatMap { tname =>
                if (sm.skill.equalsIgnoreCase(tname)) {
                  val fieldMatch = for {
                    smField <- sm.field;
                    tFieldRaw <- t.field;
                    tField <- if (tFieldRaw == "???") None else Some(tFieldRaw)
                  } yield smField.equalsIgnoreCase(tField);
                  fieldMatch match {
                    case Some(true) | None => Some(sm.mod)
                    case Some(false)       => None
                  }
                } else None
              }
            }.sum;
            knowledgeSkills.at(t.id, knowledgeSkills.morphBonus) <<= bonus
          };
          (activeUpdates ++ knowledgeUpdates).toMap
        }
        case _ => {
          debug("No skill boni to apply. Resetting.");
          val activeUpdates = activeTuples.map { t =>
            activeSkills.at(t.id, activeSkills.morphBonus) <<= activeSkills.morphBonus.resetValue
          }
          val knowledgeUpdates = knowledgeTuples.map { t =>
            knowledgeSkills.at(t.id, knowledgeSkills.morphBonus) <<= knowledgeSkills.morphBonus.resetValue
          }
          (activeUpdates ++ knowledgeUpdates).toMap
        }
      };
      setAttrs(data)
    };
    r flatMap identity

  }
}