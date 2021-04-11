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

class MichaelScottQueueSpec extends BaseSpec {

  "Michael Scott queue" should {
    "offer and poll" in real {
      val test = for {
        ms <- MichaelScottQueue[IO, Int]
        _ <- ms.offer(1)
        pl1 <- ms.poll
        pl2 <- ms.poll
        _ <- ms.offer(2)
        _ <- ms.offer(3)
        pl3 <- ms.poll
        _ <- ms.offer(4)
        _ <- ms.offer(5)
        _ <- ms.offer(6)
        pl4 <- ms.poll
        pl5 <- ms.poll
        pl6 <- ms.poll
        pl7 <- ms.poll
        pl8 <- ms.poll
        _ <- ms.offer(7)
        pl9 <- ms.poll
      } yield List(pl1, pl2, pl3, pl4, pl5, pl6, pl7, pl8, pl9)

      test.flatMap { polls =>
        IO {
          polls mustEqual List(
            Some(1),
            None,
            Some(2),
            Some(3),
            Some(4),
            Some(5),
            Some(6),
            None,
            Some(7))
        }
      }
    }

    "offer race and poll race" in real {
      val test = for {
        ms <- MichaelScottQueue[IO, Int]
        _ <- ms.offer(1).both(ms.offer(2))
        _ <- ms.offer(3).both(ms.offer(4))
        t1 <- ms.poll.both(ms.poll)
        (pl1, pl2) = t1
        t2 <- ms.poll.both(ms.poll)
        (pl3, pl4) = t2
      } yield List(pl1, pl2, pl3, pl4)

      test.flatMap { polls =>
        IO {
          (polls mustEqual List(Some(1), Some(2), Some(3), Some(4))) or
            (polls mustEqual List(Some(2), Some(1), Some(3), Some(4))) or
            (polls mustEqual List(Some(1), Some(2), Some(4), Some(3))) or
            (polls mustEqual List(Some(2), Some(1), Some(4), Some(3)))
        }
      }
    }
  }
}
