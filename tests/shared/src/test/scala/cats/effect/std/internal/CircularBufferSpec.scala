/*
 * Copyright 2020-2021 Typelevel
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
package std.internal

import cats.syntax.traverse._

class CircularBufferSpec extends BaseSpec {

  "circular buffer" should {

    "offer and poll sequential" in real {
      val test = for {
        buf <- CircularBuffer[IO, Int](3)
        of1 <- buf.offer(1)
        pl1 <- buf.poll
        of2 <- buf.offer(2)
        of3 <- buf.offer(3)
        pl2 <- buf.poll
        of4 <- buf.offer(4)
        of5 <- buf.offer(5)
        of6 <- buf.offer(6)
        pl3 <- buf.poll
        pl4 <- buf.poll
        pl5 <- buf.poll
        pl6 <- buf.poll
        of7 <- buf.offer(7)
        pl7 <- buf.poll
      } yield (List(of1, of2, of3, of4, of5, of6, of7), List(pl1, pl2, pl3, pl4, pl5, pl6, pl7))

      test.flatMap {
        case (offers, polls) =>
          IO {
            offers mustEqual List(true, true, true, true, true, false, true)
            polls mustEqual List(Some(1), Some(2), Some(3), Some(4), Some(5), None, Some(7))
          }
      }
    }

    "offer race and poll race" in real {
      val test = for {
        buf <- CircularBuffer[IO, Int](3)
        t1 <- buf.offer(1).both(buf.offer(2))
        (of1, of2) = t1
        t2 <- buf.offer(3).both(buf.offer(4))
        (of3, of4) = t2
        t3 <- buf.poll.both(buf.poll)
        (pl1, pl2) = t3
        t4 <- buf.poll.both(buf.poll)
        (pl3, pl4) = t4
      } yield (List(of1, of2, of3, of4), List(pl1, pl2, pl3, pl4))

      (0 until 100).toList.traverse { _ =>
        test.flatMap {
          case (offers, polls) =>
            IO {
              (offers mustEqual List(true, true, true, false)) or
                (offers mustEqual List(true, true, false, true))
              (polls mustEqual List(Some(1), Some(2), Some(3), None)) or
                (polls mustEqual List(Some(2), Some(1), Some(3), None)) or
                (polls mustEqual List(Some(1), Some(2), Some(4), None)) or
                (polls mustEqual List(Some(2), Some(1), Some(4), None)) or
                (polls mustEqual List(Some(1), Some(2), None, Some(3))) or
                (polls mustEqual List(Some(2), Some(1), None, Some(3))) or
                (polls mustEqual List(Some(1), Some(2), None, Some(4))) or
                (polls mustEqual List(Some(2), Some(1), None, Some(4)))
            }
        }
      }
    }

    "race offer and take" in real {
      val test = for {
        buf <- CircularBuffer[IO, Int](1)
        t <- buf.offer(1).both(buf.poll)
      } yield t

      (0 until 100).toList.traverse { _ =>
        test.flatMap {
          case (of, pl) =>
            IO {
              (of mustEqual true) and
                ((pl mustEqual None) or (pl mustEqual Some(1)))
            }
        }
      }
    }
  }
}
