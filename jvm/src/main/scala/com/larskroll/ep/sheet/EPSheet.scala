/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Lars Kroll <bathtor@googlemail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 */

package com.larskroll.ep.sheet

import scalatags.Text.all._
import scalatags.stylesheet._
import com.larskroll.roll20.sheet._

object EPSheet extends TabbedSheet {
  import SheetImplicits._;
  import Roll20Predef._;

  val char = EPCharModel;
  val t = EPTranslation;
  val sty = EPStyle;

  override def hidden = Seq[SheetElement](char.characterSheet, char.morphType, char.woundMod, char.woundsApplied, char.traumaMod);
  override def header = Header;
  override def tabs = Seq(core, skills, morphs, gear, psi, identities, muse, options);
  override def footer = Footer;

  val core = tab(t.core, CoreTab);
  val skills = tab(t.skills, SkillTab);
  val morphs = tab(t.morph, MorphTab);
  val gear = tab(t.gear, GearTab);
  val options = tab(t.options, OptionsTab);
  val identities = tab(t.identities, IdentitiesTab);
  val psi = tab(t.psi, PsiTab);
  val muse = tab(t.muse, MuseTab);

  override def style(): StyleSheet = EPStyle;
  override def externalStyles() = List(this.getClass.getClassLoader.getResource("WEB-INF/defaults.css"));
  override def translation(): SheetI18N = EPTranslation;
  override def colourScheme = EPPalette;
  override def templates = EPIniTemplate :: EPDefaultTemplate :: EPDamageTemplate :: super.templates;
}