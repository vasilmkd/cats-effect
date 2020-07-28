/*
 * Copyright 2020 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cats.effect
package laws

import cats.{Eq, Show}
import cats.effect.testkit.{freeEval, FreeSyncGenerators, SyncTypeGenerators}, freeEval._
import cats.implicits._

import org.scalacheck.Prop
import org.scalacheck.util.Pretty

import org.specs2.ScalaCheck
import org.specs2.mutable._

import org.typelevel.discipline.specs2.mutable.Discipline

class FreeSyncSpec extends Specification with Discipline with ScalaCheck {
  import FreeSyncGenerators._
  import SyncTypeGenerators._

  implicit def prettyFromShow[A: Show](a: A): Pretty =
    Pretty.prettyString(a.show)

  implicit val eqThrowable: Eq[Throwable] =
    Eq.fromUniversalEquals

  implicit def exec(sbool: FreeEitherSync[Boolean]): Prop =
    run(sbool).fold(Prop.exception(_), b => if (b) Prop.proved else Prop.falsified)

  checkAll("FreeEitherSync", SyncTests[FreeEitherSync].sync[Int, Int, Int])
}